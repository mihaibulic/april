package april.extrinsics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import april.calibration.Broadcaster;
import april.camera.CameraDriver;
import april.camera.util.TagDetector2;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.jmat.Function;
import april.random.TagDetectionComparator;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import april.util.PointUtil;
import april.vis.VisCanvas;
import april.vis.VisWorld;
import aprilO.jmat.LinAlg;
import aprilO.jmat.Matrix;
import aprilO.jmat.NumericalJacobian;

public class NewExtrinsicsPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw;
    private VisCanvas vc;

    private Config config;

    private ManagerThread manager;

    private ArrayList<Point> points = new ArrayList<Point>();
    
    public NewExtrinsicsPanel(String id)
    {
        super(id, new BorderLayout());

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.setBackground(Color.BLACK);
        vc.getViewManager().viewGoal.fit2D(new double[] { -1, -1 }, new double[] { 1, 1 });

        add(vc, BorderLayout.CENTER);
    }

    public class Spot
    {
        double[] uv = new double[2];
        String spotterId;
        
        public Spot(double[] uv, String spotterId)
        {
            this.uv = uv;
            this.spotterId = spotterId;
        }
    }

    public class Point
    {
        static final int TOP_LEFT  = 0;
        static final int TOP_RIGHT = 1;
        static final int BOT_LEFT  = 2;
        static final int BOT_RIGHT = 3;
        
        int id, corner;
        ArrayList<Spot> uvs = new ArrayList<Spot>();
        double[] xyz = new double[3];
        
        public Point(int id, int corner)
        {
            this.id = id;
            this.corner = corner;
        }
        
        @Override
        public boolean equals(Object a)
        {
            return id == ((Point)a).id && corner == ((Point)a).corner;
        }
    }
    
    class CaptureThread extends Thread
    {
        private boolean run = true;
        
        private CameraDriver driver;
        private double[] fc, cc, kc;
        private double alpha;
        
        public CaptureThread(String url, Config config)
        {
            config = config.getRoot().getChild(CameraDriver.getSubUrl(url));

            try
            {
                driver = new CameraDriver(url, config);
            } catch (ConfigException e)
            {
                e.printStackTrace();
            }
            
            fc = config.requireDoubles("fc");
            cc = config.requireDoubles("cc");
            kc = config.requireDoubles("kc");
            alpha = config.requireDouble("alpha");
        }
    
        public void run()
        {
            if(run)
            {
                int imageCount = 30; // # of images to look for tags in
                ArrayList<TagDetection> detections = getTags(imageCount);
    
                int end = 0;
                int nodeCount = 0;
                for (int start = 0; start < detections.size() && run; start = end)
                {
                    int lastId = detections.get(start).id;
                    int curId = lastId;
    
                    while (lastId == curId && ++end < detections.size())
                    {
                        curId = detections.get(end).id;
                    }
    
                    if (end - start > imageCount * 0.75) // tag must be seen in 3/4 images
                    {
                        double[][] tl = new double[end - start][2];
                        double[][] tr = new double[end - start][2];
                        double[][] bl = new double[end - start][2];
                        double[][] br = new double[end - start][2];
                        for(int b = 0; b < end-start; b++)
                        {
                            tl[b] = detections.get(start + b).p[Point.TOP_LEFT];
                            tr[b] = detections.get(start + b).p[Point.TOP_RIGHT];
                            bl[b] = detections.get(start + b).p[Point.BOT_LEFT];
                            br[b] = detections.get(start + b).p[Point.BOT_RIGHT];
                        }
                        
                        add(tl, lastId, Point.TOP_LEFT);
                        add(tr, lastId, Point.TOP_RIGHT);
                        add(bl, lastId, Point.BOT_LEFT);
                        add(br, lastId, Point.BOT_RIGHT);
                    }
                }
                
                System.out.println(driver.getCameraId() + " done! (" + nodeCount + " nodes added)");
                if(nodeCount == 0) 
                {
                    alertListener(false);
                }
            }
        }
        
        private ArrayList<TagDetection> getTags(int imageCount)
        {
            TagDetector2 td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
            ArrayList<TagDetection> detections = new ArrayList<TagDetection>();

            driver.start();
            for (int x = 0; x < imageCount && run; x++)
            {
                detections.addAll(td.process(driver.getFrameImage(), cc));
            }
            driver.kill();
            Collections.sort(detections, new TagDetectionComparator());

            return detections;
        }
        
        private synchronized void add(double[][] p, int id, int corner)
        {
            Point newPoint = new Point(id, corner);
            int loc = points.indexOf(newPoint);
            double[] point = PointUtil.getLocation(p);
            
            if(loc == -1) // new point
            {
                newPoint.uvs.add(new Spot(point, driver.getCameraId()));
            }
            else
            {
                points.get(loc).uvs.add(new Spot(point, driver.getCameraId()));
            }
        }
        
        public void kill()
        {
            run = false;

            try
            {
                this.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    class IterateThread extends Thread
    {
        public boolean run = true;
        private double[] locations;
        
        public IterateThread(int numberOfCams)
        {
            locations = new double[6*(numberOfCams+points.size())];
            for(int x = 0; x < locations.length; x++)
            {
                locations[x] = 0;
            }
        }
        
        public void run()
        {
            int count = 0, size = 10;
            double[] oldChi2 = new double[size];
            double error = 0;
            
            double threshold = 0.000001; // found experimentally 
            int ittLimit = 100;          // found experimentally 

            Distance distance = new Distance();

            double[] eps = new double[locations.length];
            for(int i = 0; i < eps.length; i++)
            {
                eps[i] = 0.0001;
            }
            
            double[] oldR = null;
            double[] r = LinAlg.scale(distance.evaluate(locations), -1);
            Matrix I = Matrix.identity(locations.length, locations.length);

            while (run && !shouldStop(oldChi2, error, 0.1))
            {
                double[][] _J = NumericalJacobian.computeJacobian(distance, locations, eps);
                Matrix J = new Matrix(_J);
                
                Matrix JTtimesJplusI = J.transpose().times(J).plus(I);
                Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
                Matrix dx = JTtimesJplusI.solve(JTr);
                
                for(int x = 0; x < locations.length; x++)
                {
                    locations[x] += 0.1*dx.get(x,0); // scaling by 0.1 helps keep results stable 
                }
                
                oldR = r.clone();
                r = LinAlg.scale(distance.evaluate(locations), -1);

                update();
            }
            
            class Distance extends Function
            {
                @Override
                public double[] evaluate(double[] locations, double[] distance)
                {
                    if(distance == null)
                    {
                        distance = new double[locations.length];
                    }
                    
                    
                    
                    return distance;
                }
            }
        }

        private boolean shouldStop(double[] oldChi2, double error, double threshold)
        {
            boolean stop = false;

            for (double c : oldChi2)
            {
                if (error - c > threshold)
                {
                    stop = true;
                    break;
                }
            }

            return stop;
        }
     
        private Point get(int id, int corner)
        {
            return points.get(points.indexOf(new Point(id, corner)));
        }
        
        public void kill()
        {
            run = false;
            try
            {
                this.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void update()
    {
    // XXX show progress/draw current set-up
    }

    class ManagerThread extends Thread
    {
        private ArrayList<CaptureThread> captures = new ArrayList<CaptureThread>();
        private IterateThread iterate;
        private Config config;
        
        public ManagerThread(Config config)
        {
            this.config = config;
        }
        
        public void run()
        {
            int cameras = 0;
            for(String url : ImageSource.getCameraURLs())
            {
                if(CameraDriver.isValidUrl(config, url))
                {
                    cameras++;
                    CaptureThread newThread = new CaptureThread(url, config);
                    newThread.start();
                    captures.add(newThread);
                }
            }
            
            for(CaptureThread c : captures)
            {
                try
                {
                    c.join();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            System.out.println("done capturing");
            
            iterate = new IterateThread(cameras);
            iterate.start();
            System.out.println("started iterating");
            
            try
            {
                iterate.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            System.out.println("done iterating. results: ");
            // XXX print results
        }
        
        public void kill()
        {
            for(CaptureThread c : captures)
            {
                c.kill();
            }
            
            iterate.kill();
            
            try
            {
                this.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void go(String configPath)
    {
        try
        {
            config = new ConfigFile(configPath).getChild("extrinsics");
            ConfigUtil2.verifyConfig(config);
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        }
        
        manager = new ManagerThread(config);
        manager.start();
    }

    @Override
    public void kill()
    {
        manager.kill();
    }

    @Override
    public void displayMsg(String msg, boolean error)
    {}

    @Override
    public void showDisplay(boolean show)
    {}

}
