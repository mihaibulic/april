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
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.tag.TagFamily;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import aprilO.graph.CholeskySolver;
import aprilO.graph.GEdge;
import aprilO.graph.Graph;
import aprilO.graph.GraphSolver;
import aprilO.image.Homography33b;
import aprilO.jmat.ordering.MinimumDegreeOrdering;
import aprilO.tag.CameraUtil;
import aprilO.vis.VisCanvas;
import aprilO.vis.VisChain;
import aprilO.vis.VisData;
import aprilO.vis.VisDataFillStyle;
import aprilO.vis.VisDataLineStyle;
import aprilO.vis.VisImage;
import aprilO.vis.VisRectangle;
import aprilO.vis.VisText;
import aprilO.vis.VisWorld;

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
    private String url, configPath;
    
    private IterateThread iterateThread;
    private Graph g;
    
    static class Status
    {
        static final int INITIAL = 1;
        static final int ITTERATING = 10;
        static final int DONE = 100;
        
        boolean show;
        String display;
        String directions;
        int mode;
    }
    
    static class TagPosition
    {
        int id;
        double cx, cy;
        double size;
    }

    public IntrinsicsPanel(String id, String url) 
    {
        super(new BorderLayout());
        
        this.url = url;
        
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.getViewManager().interfaceMode = 1.0;
        vc.setBackground(Color.BLACK);

        vwImages = new VisWorld();
        vcImages = new VisCanvas(vwImages);
        vcImages.getViewManager().interfaceMode = 1.0;
        vcImages.setBackground(Color.BLACK);
        
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

    private void initGraph()
    {
        g = new Graph();
        double f = 400; // XXX random guess for f
        g.nodes.add(new GIntrinsicsNode(f, f, captureThread.height/2.0, captureThread.width/2.0));
    }
    
    class CaptureThread extends Thread
    {
        private boolean run = true;
        private CameraDriver driver;
        
        int width, height;
        
        // protected by synchronizing on CaptureThread
        private GCalibrateEdge lastEdge;
        private GExtrinsicsNode lastNode;

        CaptureThread(String url, Config config) throws ConfigException
        {
            driver = new CameraDriver(url, config);
            width = driver.getWidth();
            height = driver.getHeight();

            vc.getViewManager().viewGoal.fit2D(new double[] { width*0.20, height*0.20 }, 
                    new double[] { width*0.80, height*0.80 });
        }

        public void run()
        {
            BufferedImage image = null;
            BufferedImage undistortedImage = null;
            String format = driver.getFormat();

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

                    ArrayList<double[]> correspondences = new ArrayList<double[]>();
                    Homography33b h = new Homography33b();
                    int cur = 0;
                    while(cur < detections.size())
                    {
                        TagDetection d = detections.get(cur);
                        TagPosition tp = tagPositions.get(d.id);
                        if (tp == null)
                        {
                            detections.remove(cur);
                        }
                        else
                        {
                            cur++;
                            h.addCorrespondence(tp.cx, tp.cy, d.cxy[0], d.cxy[1]);
                            correspondences.add(new double[] { tp.cx, tp.cy, d.cxy[0], d.cxy[1] });
                        }
                    }
                    
                    // every frame adds 6 unknowns, so we need at
                    // least 8 tags for it to be worth the
                    // trouble.
                    int minTags = 8;
                    if (detections.size() >= minTags) 
                    {
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
                    
                    {
                        VisWorld.Buffer vb = vw.getBuffer("camera");
                        vb.addBuffered(new VisImage(undistortedImage));
    
                        for (TagDetection tag : detections)
                        {
                            double p0[] = dist.undistort(tag.interpolate(-1, -1));
                            double p1[] = dist.undistort(tag.interpolate(1, -1));
                            double p2[] = dist.undistort(tag.interpolate(1, 1));
                            double p3[] = dist.undistort(tag.interpolate(-1, 1));
    
                            vb.addBuffered(new VisChain(LinAlg.translate(0, height, 0), 
                                    LinAlg.scale(1, -1, 1), 
                                    new VisData(new VisDataLineStyle(Color.blue, 4), 
                                    p0, p1, p2, p3, p0), 
                                    new VisData(new VisDataLineStyle(Color.green, 4), p0, p1), // x
                                    new VisData(new VisDataLineStyle(Color.red, 4), p0, p3))); // y
                                                                                       // axis
                        }
    
                        vb.switchBuffer();
                    }
                }
            }
            driver.kill();
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
                    String[] path = {CameraDriver.getSubUrl(url)};
                    ConfigUtil2.setValues(configPath, path, "fc", new double[]{state[0], state[1]});
                    ConfigUtil2.setValues(configPath, path, "cc", new double[]{state[2], state[3]});
                    ConfigUtil2.setValues(configPath, path, "kc", new double[]{state[4], state[5], state[6], state[7], state[8]});
                    ConfigUtil2.setValue(configPath, path, "alpha", state[9]);
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
            status.display =   "<<left>> \n \n \n \n \n \n \n " +
            "<<mono-big>>Parameters:\n \n \n \n " +
            "<<mono-small>>focal length: " + (int)state[0] + ", " + (int)state[1] + "\n \n" +
            "image center: " + (int)state[2] + ", " + (int)state[3] + "\n \n"+
            "distortion: " + ConfigUtil2.round(state[4],4) + ", " + ConfigUtil2.round(state[5],4) + ", " + ConfigUtil2.round(state[6],4) + ", " + ConfigUtil2.round(state[7],4) + "\n \n" +
            "skew: " + ConfigUtil2.round(state[8],4) + "\n \n " + 
            "<<left>>                                                                                \n \n \n \n \n \n \n \n ";
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
            status.display = status.directions;
            showDisplay(status.show);
            
            VisWorld.Buffer vbMessage = vw.getBuffer("message");
            vbMessage.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<gray, big>>Reset complete."));
                vbMessage.switchBuffer();

            goButton.setEnabled(true);
        }
        
        goButton.setEnabled(g.nodes.size()>4);
    }

    @Override
    public void go(String configPath)
    {
        try
        {
            this.configPath = configPath;
            Config config = new ConfigFile(configPath).getChild("intrinsics");
            ConfigUtil2.verifyConfig(config);

            status = new Status();
            status.mode = Status.INITIAL;
            status.display = "<<mono-small>>DIRECTIONS\n \n"+
                "<<mono-small>><<left>> Place the tag mosaic in the following four positions and click capture:\n \n"+
                "<<mono-small>><<left>>     1. Close up: camera should be looking straight at the mosaic\n"+
                "<<mono-small>><<left>>                  and as close as possible\n \n"+
                "<<mono-small>><<left>>     2. Far away: camera should be looking straight at the mosaic and far\n"+
                "<<mono-small>><<left>>                  away, but close enough so the camera can see most tags\n \n"+
                "<<mono-small>><<left>>     3. Angle 1: place camera so that it views the mosaic at a sharp angle\n \n"+
                "<<mono-small>><<left>>     4. Angle 2: keep the camera at a sharp angle, but turn it 90 degrees\n"+
                "<<mono-small>><<left>>                  so the video feed is sideways\n \n \n"+
                "<<mono-small>><<left>>HINT #1: Not all tags must be visible in every image.\n \n"+
                "<<mono-small>><<left>>HINT #2: The four images above are the minimum; the more unique images\n "+
                "<<mono-small>><<left>>                 you capture, the better the parameters will be.";
            
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
            
            captureThread = new CaptureThread(url, config);
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
        if(iterateThread != null)
        {
            iterateThread.kill();
            iterateThread = null;
        }
        
        if(captureThread != null)
        {
            captureThread.kill();
            captureThread = null;
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
            VisText v = new VisText(VisText.ANCHOR.CENTER, status.display);
            v.dropShadowColor = new Color(0,0,0,0);
            vbDisplay.addBuffered(new VisChain(new VisRectangle(new double[]{0,0}, 
                    new double[]{captureThread.width,captureThread.height}, 
                    new VisDataFillStyle(new Color(0,0,0,200))), v));
        }
        vbDisplay.switchBuffer();
    }
}
