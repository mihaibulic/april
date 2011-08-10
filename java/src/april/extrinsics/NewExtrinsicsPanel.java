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
import april.jmat.LinAlg;
import april.random.TagDetectionComparator;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.util.ConfigException;
import april.util.ConfigUtil;
import april.util.PointUtil;
import april.vis.VisCanvas;
import april.vis.VisWorld;
import aprilO.graph.CholeskySolver;
import aprilO.graph.GNode;
import aprilO.graph.Graph;
import aprilO.graph.GraphSolver;
import aprilO.jmat.ordering.MinimumDegreeOrdering;

public class NewExtrinsicsPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw;
    private VisCanvas vc;

    private Config config;

    private Graph g;

    private ManagerThread manager;
    
    public NewExtrinsicsPanel(String id)
    {
        super(id, new BorderLayout());

        g = new Graph();

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.setBackground(Color.BLACK);
        vc.getViewManager().viewGoal.fit2D(new double[] { -1, -1 }, new double[] { 1, 1 });

        add(vc, BorderLayout.CENTER);
    }

    class CaptureThread extends Thread
    {
        private boolean run = true;
        
        private CameraDriver driver;
        private double[] fc, cc, kc;
        private double alpha, tagSize;
        
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
            tagSize = config.getRoot().getChild("extrinsics").requireDouble("tag_size");
        }
    
        public void run()
        {
            if(run)
            {
                int curNode = g.nodes.size();
                int imageCount = 30; // # of images to look for tags in
                ArrayList<TagDetection> detections = getTags(imageCount);
    
                g.nodes.add(new Node(true, driver.getCameraId()));

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
                        double[][] points = new double[end - start][6];
                        for (int b = 0; b < points.length; b++)
                        {
                            double[][] M = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, detections.get(start + b).homography);
                            points[b] = LinAlg.matrixToXyzrpy(M);
                        }
    
                        double[] tagXyzrpy = PointUtil.getLocation(points);
    
                        nodeCount++;
                        g.edges.add(new Edge(new int[] { curNode, g.nodes.size() }, tagXyzrpy));
                        g.nodes.add(new Node(false, lastId + ""));
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

        public void run()
        {
            GraphSolver gs = new CholeskySolver(g, new MinimumDegreeOrdering());

            int count = 0, size = 10;
            double[] oldChi2 = new double[size];

            while (run && !shouldStop(oldChi2, g.getErrorStats().chi2, 0.1))
            {
                oldChi2[(count++) % size] = g.getErrorStats().chi2;
                gs.iterate();

                update();
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
            for(String url : ImageSource.getCameraURLs())
            {
                if(CameraDriver.isValidUrl(config, url))
                {
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
            
            iterate = new IterateThread();
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
            
            for(GNode n : g.nodes)
            {
                if(((Node)n).isCamera)
                {
                    PointUtil.print(((Node)n).state);
                }
            }
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
            ConfigUtil.verifyConfig(config);
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
