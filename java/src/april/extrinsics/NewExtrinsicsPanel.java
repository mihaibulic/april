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

    ArrayList<CaptureThread> captures;
    
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
        private boolean done = false;
        private Object lock = new Object();
        
        private CameraDriver driver;
        private double[] fc, cc, kc;
        private double alpha, tagSize;
        
        public CaptureThread(String url)
        {
            try
            {
                driver = new CameraDriver(url, config);
            } catch (ConfigException e)
            {
                e.printStackTrace();
            }
            
            run = driver.isGood();
            config = config.getRoot().getChild(CameraDriver.getSubUrl(url));
            fc = config.requireDoubles("fc");
            cc = config.requireDoubles("cc");
            kc = config.requireDoubles("kc");
            alpha = config.requireDouble("alpha");
            tagSize = config.getRoot().getChild("extrinsics").requireDouble("tag_size");
        }
    
        public boolean isGood()
        {
            return run;
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
                int tempcount = 0;
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
    
                        tempcount++;
                        g.edges.add(new Edge(new int[] { curNode, g.nodes.size() }, tagXyzrpy));
                        g.nodes.add(new Node(false, lastId + ""));
                    }
                }
                System.out.println(driver.getCameraId() + " done! (" + tempcount + " nodes added)");
                
                synchronized(lock)
                {
                    done = true;
                    lock.notify();
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
            
            synchronized(lock)
            {
                while(!done)
                {
                    try
                    {
                        lock.wait();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
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
    }

    public void update()
    {
    // XXX show progress/draw current set-up
    }

    @Override
    public void go(String configPath, String... urls)
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
        
        captures = new ArrayList<CaptureThread>();
        for(String url : urls)
        {
            CaptureThread test = new CaptureThread(url);
            if(test.isGood())
            {
                test.start();
                captures.add(test);
            }
        }
    }

    @Override
    public void kill()
    {
        for(CaptureThread c : captures)
        {
            c.kill();
        }
    }

    @Override
    public void displayMsg(String msg, boolean error)
    {}

    @Override
    public void showDisplay(boolean show)
    {}

}
