package april.intrinsics;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.border.EmptyBorder;
import april.calibration.Broadcaster;
import april.camera.CameraDriver;
import april.camera.util.Distortion;
import april.config.Config;
import april.config.ConfigFile;
import april.jmat.LinAlg;
import april.util.ConfigException;
import april.util.ConfigUtil;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisData;
import april.vis.VisDataLineStyle;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisWorld;
import aprilO.graph.CholeskySolver;
import aprilO.graph.GEdge;
import aprilO.graph.Graph;
import aprilO.graph.GraphSolver;
import aprilO.image.Homography33b;
import aprilO.jcam.ImageConvert;
import aprilO.jmat.ordering.MinimumDegreeOrdering;
import aprilO.tag.CameraUtil;
import aprilO.tag.Tag36h11;
import aprilO.tag.TagDetection;
import aprilO.tag.TagDetector;
import aprilO.tag.TagFamily;

public class IntrinsicsPanel extends Broadcaster implements ActionListener
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw;
    private VisCanvas vc;
    
    private VisWorld vwImages;
    private VisCanvas vcImages;
    
    private Status status;
    
    private JButton goButton = new JButton("Go");
    private JButton captureButton = new JButton("Capture");
    private JButton resetButton = new JButton("Reset");
    
    private TagDetector td = new TagDetector(new Tag36h11());
    private HashMap<Integer, TagPosition> tagPositions;
    private CaptureThread captureThread;

    private IterateThread iterateThread;
    private Graph g;
    
    private String configPath;
    private Config config;
    private String url;
    private int urlId;
    
    static class Status
    {
        static final int INITIAL = 1;
        static final int ITTERATING = 10;
        static final int DONE = 100;
        
        boolean show;
        String display;
        int mode;
    }
    
    static class TagPosition
    {
        int id;
        double cx, cy;
        double size;
    }

    /**
     * @param urlId - It's a little weird to send the index when the url could be sent just as easily.  
     *                This is for consistency (urls always sent through go method)
     */
    public IntrinsicsPanel(String id, int urlId) 
    {
        super(new BorderLayout());
        
        this.urlId = urlId;
        
        initVis();
        
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BorderLayout());
        topPanel.add(vc);
        JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel, vcImages);
        jsp.setDividerLocation(0.75);
        jsp.setResizeWeight(0.75);
        add(jsp, BorderLayout.CENTER);

        goButton.addActionListener(this);
        captureButton.addActionListener(this);
        resetButton.addActionListener(this);
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(goButton);
        buttonBox.add(captureButton);
        buttonBox.add(resetButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JSeparator separator = new JSeparator();
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(separator, BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);
        
        goButton.setEnabled(false);
    }

    private void initVis()
    {
        vw = new VisWorld();
        vc = new VisCanvas(vw);

        vwImages = new VisWorld();
        vcImages = new VisCanvas(vwImages);
        
        vc.setBackground(Color.BLACK);
        vc.getViewManager().interfaceMode = 1.0;
        vcImages.setBackground(Color.BLACK);
        vcImages.getViewManager().interfaceMode = 1.0;
    }
    
    private void initGraph()
    {
        g = new Graph();
        double f = 400; // XXX random guess for f
        g.nodes.add(new GIntrinsicsNode(f, f, captureThread.height/2.0, captureThread.width/2.0));
    }
    
    class CaptureThread extends Thread
    {
        private boolean run = true, done = false;
        private Object lock = new Object();   
        
        private String url, format;
        private int width, height;
        private CameraDriver driver;
        
        // protected by synchronizing on CaptureThread
        private GCalibrateEdge lastEdge;
        private GExtrinsicsNode lastNode;

        CaptureThread(String url) throws ConfigException
        {
            driver = new CameraDriver(url, config);
            this.url = url;
            this.format = driver.getFormat();
            this.width  = driver.getWidth();
            this.height = driver.getHeight();
        }

        public void run()
        {
            boolean first = true;
            BufferedImage image = null;
            BufferedImage undistortedImage = null;
            
            driver.start();
            
            while (run)
            {
                byte[] imageBuffer = driver.getFrameBuffer();
                image = ImageConvert.convertToImage(format, width, height, imageBuffer);
                Distortion dist = new Distortion(((GIntrinsicsNode) g.nodes.get(0)).state, width, height);
                undistortedImage = ImageConvert.convertToImage(format, width, height, dist.naiveBufferUndistort(imageBuffer));    
                
                synchronized(this)
                {
                    lastEdge = null;
    
                    ArrayList<TagDetection> detections = td.process(image, new double[] { width / 2.0, height / 2.0 });

                    {
                        VisWorld.Buffer vb = vw.getBuffer("camera");
                        vb.addBuffered(new VisImage(undistortedImage));
    
                        for (TagDetection tag : detections)
                        {
                            double p0[] = dist.undistort(tag.interpolate(-1, -1));
                            double p1[] = dist.undistort(tag.interpolate(1, -1));
                            double p2[] = dist.undistort(tag.interpolate(1, 1));
                            double p3[] = dist.undistort(tag.interpolate(-1, 1));
    
                            vb.addBuffered(new VisChain(LinAlg.translate(0, image.getHeight(), 0), LinAlg.scale(1, -1, 1), new VisData(new VisDataLineStyle(Color.blue, 4), p0, p1, p2, p3, p0), new VisData(new VisDataLineStyle(Color.green, 4), p0, p1), // x
                                                                                                                                                                                                                                                                                                                                                                                 // axis
                            new VisData(new VisDataLineStyle(Color.red, 4), p0, p3))); // y
                                                                                       // axis
                        }
    
                        vb.switchBuffer();
                    }
    
                    if (first)
                    {
                        first = false;
                        int w = image.getWidth();
                        int h = image.getHeight();
                        
                        vc.getViewManager().viewGoal.fit2D(new double[] { w*0.20, h*0.20 }, new double[] { w*0.80, h*0.80 });
                    }
    
                    // every frame adds 6 unknowns, so we need at
                    // least 8 tags for it to be worth the
                    // trouble.
                    int minTags = 8;
                    ArrayList<double[]> correspondences = new ArrayList<double[]>();
    
                    if (detections.size() >= minTags)
                    {
                        // compute a homography using the entire set of tags
                        Homography33b h = new Homography33b();
                        for (TagDetection d : detections)
                        {
                            TagPosition tp = tagPositions.get(d.id);
                            if (tp == null)
                            {
                                System.out.println("Found tag that doesn't exist in model: " + d.id);
                                continue;
                            }
    
                            h.addCorrespondence(tp.cx, tp.cy, d.cxy[0], d.cxy[1]);
                            correspondences.add(new double[] { tp.cx, tp.cy, d.cxy[0], d.cxy[1] });
                        }
    
                        double fx = ((GIntrinsicsNode) g.nodes.get(0)).state[0];
                        double fy = ((GIntrinsicsNode) g.nodes.get(0)).state[1];
                        double cx = ((GIntrinsicsNode) g.nodes.get(0)).state[2];
                        double cy = ((GIntrinsicsNode) g.nodes.get(0)).state[3];
    
                        double P[][] = CameraUtil.homographyToPose(-fx, fy, cx, cy, h.getH());
    
                        lastEdge = new GCalibrateEdge(correspondences, image);
                        lastNode = new GExtrinsicsNode();
                        lastNode.state = LinAlg.matrixToXyzrpy(P);
                        lastNode.init = LinAlg.copy(lastNode.state);
                    }
                }
            }
            driver.kill();
            
            synchronized(lock)
            {
                done = true;
                lock.notify();
            }
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
        private boolean run = true, done = false;
        private Object lock = new Object();
        
        public void run()
        {
            status.mode = Status.ITTERATING;
            VisWorld.Buffer vbMessage = vw.getBuffer("message");
            vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<blue, big>>Calibrating..."));
            vbMessage.switchBuffer();
            
            GraphSolver gs = new CholeskySolver(g, new MinimumDegreeOrdering());

            int ncorr = 0;
            int count = 0, size = 10;
            double[] oldChi2 = new double[size];
            while(run && (!shouldStop(oldChi2, g.getErrorStats().chi2, 0.1, ncorr) || count < 10))
            {
                oldChi2[(count++)%size] = g.getErrorStats().chi2;
                
                gs.iterate();

                update();
                
                ncorr = 0;
                for (GEdge ge : g.edges)
                {
                    if (ge instanceof GCalibrateEdge)
                    {
                        ncorr += ((GCalibrateEdge) ge).correspondences.size();
                    }
                }
            }
            
            if(run)
            {
                status.mode = Status.DONE;
                vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<green, big>>Calibration completed! Hit next to continue."));
                vbMessage.switchBuffer();
                
                double state[] = g.nodes.get(0).state;
                try
                {
                    ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(url)}, "fc", new double[]{state[0], state[1]});
                    ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(url)}, "cc", new double[]{state[2], state[3]});
                    ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(url)}, "kc", new double[]{state[4], state[5], state[6], state[7], state[8]});
                    ConfigUtil.setValue(configPath, new String[]{CameraDriver.getSubUrl(url)}, "alpha", state[9]);
                } catch (ConfigException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            else
            {
                status.mode = Status.INITIAL;
                
                vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<red, big>>Calibration failed! Please retry."));
                vbMessage.switchBuffer();
                
                initGraph();
                update();
                
                synchronized(lock)
                {
                    done = true;
                    lock.notify();
                }
            }
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
        
        /**
         * if all old chi2 errors are within threshold of current err, stop 
         */
        private boolean shouldStop(double[] oldChi2, double error, double threshold, int corr)
        {
            boolean stop = false;
            
            if(corr != 0)
            {
                for(double c : oldChi2)
                {
                    if((error-c)/corr > threshold)
                    {
                        stop = true;
                        break;
                    }
                }
            }
            
            return stop;
        }
    }

    void update()
    {
        int xoff = 0;

        VisWorld.Buffer vb = vwImages.getBuffer("frame " + xoff);
        
        for (GEdge ge : g.edges)
        {
            if (ge instanceof GCalibrateEdge)
            {
                ((GCalibrateEdge) ge).draw(g, vb, xoff, 0, 1);
                xoff++;
            }
        }
        
        if(xoff <= 5)
        {
            vcImages.getViewManager().viewGoal.fit2D(new double[] { (xoff-1)/2.0, 0 }, new double[] { (xoff+1)/2.0 , 0.5 });
        }
        else
        {
            xoff -= 5;
            vcImages.getViewManager().viewGoal.fit2D(new double[] { 2+xoff, 0 }, new double[] { 3+xoff , 0.5 });
        }
        
        VisWorld.Buffer vbDisplay = vw.getBuffer("display");
        if(status.mode == Status.ITTERATING)
        {
            double state[] = g.nodes.get(0).state;
            status.display =   "<<left>> \n \n \n \n \n \n " +
            "<<mono-big>>Parameters:\n \n \n \n " +
            "<<mono-small>>focal length: " + (int)state[0] + ", " + (int)state[1] + "\n \n" +
            "image center: " + (int)state[2] + ", " + (int)state[3] + "\n \n"+
            "distortion: " + ConfigUtil.round(state[4],4) + ", " + ConfigUtil.round(state[5],4) + ", " + ConfigUtil.round(state[6],4) + ", " + ConfigUtil.round(state[7],4) + "\n \n" +
            "skew: " + ConfigUtil.round(state[8],4) + "\n \n " + 
            "<<left>>                                                                        \n \n \n \n \n \n \n ";
        }

        if(status.show)
        {
            vbDisplay.addBuffered(new VisText(VisText.ANCHOR.CENTER, status.display));
        }
        vbDisplay.switchBuffer();
        
        vb.switchBuffer();
    }
    
    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals(captureButton.getText()))
        {
            synchronized (captureThread)
            {
                VisWorld.Buffer vbMessage = vw.getBuffer("message");
                if (captureThread.lastEdge != null)
                {
                    int nidx = g.nodes.size();
                    g.nodes.add(captureThread.lastNode);
                    captureThread.lastEdge.nodes[0] = 0;
                    captureThread.lastEdge.nodes[1] = nidx;
                    g.edges.add(captureThread.lastEdge);

                    update();
    
                    captureThread.lastEdge = null;
                    captureThread.lastNode = null;
    
                    vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<green, big>>Added frame " + (g.nodes.size()-1) + "."));
                }
                else
                {
                    vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<red, big>>Could not create a constraint."));
                }
                vbMessage.switchBuffer();
            }
        }
        else if (e.getActionCommand().equals(goButton.getText()))
        {
            if (iterateThread == null)
            {
                iterateThread = new IterateThread();
                iterateThread.start();
                goButton.setEnabled(false);
            }
        }
        else if(e.getActionCommand().equals(resetButton.getText()))
        {
            try
            {
                if(iterateThread != null)
                {
                    iterateThread.run = false;
                    iterateThread.join();
                    iterateThread = null;
                }
            } catch (InterruptedException e1)
            {
                e1.printStackTrace();
            }

            initGraph();
            update();
            status.mode = Status.INITIAL;
            status.display = config.requireString("display");
            showDisplay(status.show);
            
            VisWorld.Buffer vbMessage = vw.getBuffer("message");
            vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<gray, big>>Reset complete."));
                vbMessage.switchBuffer();

            goButton.setEnabled(true);
        }
        
        goButton.setEnabled(g.nodes.size()>4);
    }

    @Override
    public void go(String configPath, String... urls)
    {
        try
        {
            this.configPath = configPath;
            this.url = urls[urlId];
            config = new ConfigFile(configPath).getChild("intrinsics");
            ConfigUtil.verifyConfig(config);

            status = new Status();
            status.mode = Status.INITIAL;
            status.display = config.requireString("display");
            
            // Create ground truth
            {
                tagPositions = new HashMap<Integer, TagPosition>();
                TagFamily tf = new Tag36h11();
                int tagsPerRow = config.requireInt("mosaic_tags_per_row");
                int columns = config.requireInt("mosaic_rows");
                int rows = config.requireInt("mosaic_columns");
                double spacing = config.requireDouble("mosaic_tag_spacing");
                
                for (int y = 0; y < tagsPerRow; y++)
                {
                    for (int x = 0; x < tagsPerRow; x++)
                    {
                        TagPosition tp = new TagPosition();
                        tp.id = y * tagsPerRow + x;

                        if (tp.id >= tf.codes.length)
                        {
                            continue;
                        }

                        tp.cx = x * spacing; 
                        tp.cy = -y * spacing;
                        tp.size = spacing * columns / rows;

                        tagPositions.put(tp.id, tp);
                    }
                }
            }
            
            captureThread = new CaptureThread(url);
            captureThread.start();

            initGraph();
            update();
        } catch (IOException e)
        {
            e.printStackTrace();
        }catch (ConfigException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void kill()
    {
        try
        {
            if(iterateThread != null)
            {
                iterateThread.run = false;
                iterateThread.join();
                iterateThread = null;
            }
            
            if(captureThread != null)
            {
                captureThread.run = false;
                captureThread.join();
                captureThread = null;
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {
        String prefix = "<<" + (error ? "red" : "blue") + ", big>>";
        VisWorld.Buffer vbMessage = vw.getBuffer("message");
        vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, prefix + msg));
        vbMessage.switchBuffer();
    }
    
    @Override
    public void showDisplay(boolean show)
    {
        VisWorld.Buffer vbDisplay = vw.getBuffer("display");
        vbDisplay.setDrawOrder(10);
     
        status.show = show;
        if(show)
        {
            vbDisplay.addBuffered(new VisText(VisText.ANCHOR.CENTER, status.display));
        }
        vbDisplay.switchBuffer();
    }
}
