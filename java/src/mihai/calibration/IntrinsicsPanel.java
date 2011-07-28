package mihai.calibration;

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
import mihai.camera.CameraDriver;
import mihai.camera.Distortion;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import april.config.Config;
import april.config.ConfigFile;
import april.jmat.LinAlg;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisData;
import april.vis.VisDataLineStyle;
import april.vis.VisDataPointStyle;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisWorld;
import aprilO.graph.CholeskySolver;
import aprilO.graph.GEdge;
import aprilO.graph.GNode;
import aprilO.graph.Graph;
import aprilO.graph.GraphSolver;
import aprilO.graph.Linearization;
import aprilO.image.Homography33b;
import aprilO.jcam.ImageConvert;
import aprilO.jmat.ordering.MinimumDegreeOrdering;
import aprilO.tag.CameraUtil;
import aprilO.tag.Tag36h11;
import aprilO.tag.TagDetection;
import aprilO.tag.TagDetector;
import aprilO.tag.TagFamily;
import aprilO.util.StructureReader;
import aprilO.util.StructureWriter;

public class IntrinsicsPanel extends Broadcaster implements ActionListener
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw;
    private VisCanvas vc;

    private VisWorld vwImages;
    private VisCanvas vcImages;

    private String goButtonText = "Go";
    private String stopButtonText = "Stop";
    private String captureButtonText = "Capture";
    private JButton goButton = new JButton(goButtonText);
    private JButton captureButton = new JButton(captureButtonText);
    
    private TagDetector td = new TagDetector(new Tag36h11());

    private CaptureThread captureThread;
    private IterateThread iterateThread;
    
    private String configPath;
    private String url;
    private int urlId;
    private Config config;
    
    // indexed by tag id
    private HashMap<Integer, TagPosition> tagPositions;

    private Graph g;

    static class TagPosition
    {
        int id;
        double cx, cy;
        double size;
    }

    static class Capture
    {
        BufferedImage im;
        ArrayList<TagDetection> detections;
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
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(goButton);
        buttonBox.add(captureButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JSeparator separator = new JSeparator();
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(separator, BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);
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
    
    class CaptureThread extends Thread
    {
        String url, format;
        int width, height;
        CameraDriver driver;
        boolean stop = false;
        
        // protected by synchronizing on CaptureThread
        GCalibrateEdge lastEdge;
        GExtrinsicsNode lastNode;

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
            
            while (!stop)
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
                        vc.getViewManager().viewGoal.fit2D(new double[] { 125, 125 }, new double[] { image.getWidth()-125, image.getHeight()-125 });
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
            
            try
            {
                driver.kill();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    public void actionPerformed(ActionEvent e)
    {
        if (e.getActionCommand().equals(captureButtonText))
        {
            synchronized (captureThread)
            {
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
    
                    System.out.println("Added frame");
                }
                else
                {
                    System.out.printf("Could not create a constraint.\n");
                }
            }
        }
        else if (e.getActionCommand().equals(goButtonText))
        {
            if (iterateThread == null)
            {
                iterateThread = new IterateThread();
                iterateThread.start();
                goButton.setText(stopButtonText);
            }
        }
        else if(e.getActionCommand().equals(stopButtonText))
        {
            iterateThread.stop = true;
            try
            {
                iterateThread.join();
            } catch (InterruptedException e1)
            {
                e1.printStackTrace();
            }
            iterateThread = null;
            goButton.setText(goButtonText);
        }
    }

    class IterateThread extends Thread
    {
        public boolean stop = false;

        IterateThread()
        {}

        public void run()
        {
            GraphSolver gs = new CholeskySolver(g, new MinimumDegreeOrdering());
            VisWorld.Buffer vbDirections = vw.getBuffer("directions");
            VisWorld.Buffer vbError = vw.getBuffer("error");

            int ncorr = 0;
            int count = 0, size = 10;
            double[] oldChi2 = new double[size];
            while(!stop && (!shouldStop(oldChi2, g.getErrorStats().chi2, 0.1, ncorr) || count < 10))
            {
                oldChi2[(count++)%size] = g.getErrorStats().chi2;
                
                gs.iterate();

                double state[] = g.nodes.get(0).state;
                String directions = "<<left>> \n \n \n \n \n \n \n " +
                                    "<<mono-big>>Parameters:\n \n \n \n " +
                                    "<<mono-small>>focal length: " + (int)state[0] + ", " + (int)state[1] + "\n \n" +
                                    "image center: " + (int)state[2] + ", " + (int)state[3] + "\n \n"+
                                    "distortion: " + ConfigUtil.round(state[4],4) + ", " + ConfigUtil.round(state[5],4) + ", " + ConfigUtil.round(state[6],4) + ", " + ConfigUtil.round(state[7],4) + "\n \n" +
                                    "skew: " + ConfigUtil.round(state[8],4) + "\n \n " + 
                                    "<<left>>                                                                        \n \n \n \n \n \n \n ";
                vbDirections.addBuffered(new VisText(VisText.ANCHOR.CENTER, directions));
                vbDirections.switchBuffer();
                
                update();
                
                ncorr = 0;
                for (GEdge ge : g.edges)
                {
                    if (ge instanceof GCalibrateEdge)
                    {
                        ncorr += ((GCalibrateEdge) ge).correspondences.size();
                    }
                }

                vbError.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<blue, big>>Error: " + ConfigUtil.round(g.getErrorStats().chi2/ncorr,3)));
                vbError.switchBuffer();
            }
            
            double state[] = g.nodes.get(0).state;
            try
            {
                ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(config, url)}, "fc", new double[]{state[0], state[1]});
                ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(config, url)}, "cc", new double[]{state[2], state[3]});
                ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(config, url)}, "kc", new double[]{state[4], state[5], state[6], state[7], state[8]});
                ConfigUtil.setValue(configPath, new String[]{CameraDriver.getSubUrl(config, url)}, "alpha", state[9]);
            } catch (ConfigException e)
            {
                e.printStackTrace();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            
            vbError.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<green, big>>Calibration completed! Hit next to continue."));
            vbError.switchBuffer();

            goButton.setText(goButtonText);
        }
        
        /**
         * if all of old chi2 errors are within the threshold of the current error, recommend stopping 
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
        
        vb.switchBuffer();
    }

    // ///////////////////////////////////////////////////////////////////
    static class GIntrinsicsNode extends GNode
    {
        public GIntrinsicsNode(double fx, double fy, double cx, double cy)
        {
            state = new double[] { fx, fy, cx, cy, 0, 0, 0, 0, 0, 0 };
        }

        private GIntrinsicsNode()
        {}

        // state:
        // 0: fx
        // 1: fy
        // 2: cx
        // 3: cy
        // 4: distortion r coefficient
        // 5: distortion r^2 coefficient
        // 6: tangential coefficient
        // 7: tangential coefficient
        // 8: distortion r^4 coefficient
        // 9: alpha (skew)
        public int getDOF()
        {
            return state.length;
        }

        public static double[] project(double state[], double p[], int width, int height)
        {
            assert (p.length == 4); // 3D homogeneous coordinates in, please.

            double M[][] = new double[][] { { -state[0], 0, state[2], 0 }, { 0, state[1], state[3], 0 }, { 0, 0, 1, 0 } };
            double q[] = LinAlg.matrixAB(M, p);
            q[0] /= q[2];
            q[1] /= q[2];
            q[2] = 1;

            double centered[] = {(q[0]-state[2])/state[0],(q[1]-state[3])/state[1]};
            double r2 = centered[0]*centered[0] + centered[1]*centered[1];
            double scale = 1 + state[4]*r2 + state[5]*r2*r2;
            double scaled[] = {centered[0]*scale, centered[1]*scale};
            
            double tangential[] =
                {2 * state[6] * centered[0] * centered[1] +
                 state[7] * (r2 + 2 * centered[0] * centered[0]),
                 state[6] * (r2 + 2 * centered[1] * centered[1]) +
                 2 * state[7] * centered[0] * centered[1]};

            scaled[0] = scaled[0]+tangential[0];
            scaled[1] = scaled[1]+tangential[1];

            return new double[] {state[0] * (scaled[0] + state[9] * scaled[1]) + state[2], scaled[1] * state[1] + state[3]};
        }

        public GIntrinsicsNode copy()
        {
            GIntrinsicsNode gn = new GIntrinsicsNode();
            gn.state = LinAlg.copy(state);
            gn.init = LinAlg.copy(init);
            if (truth != null)
            {
                gn.truth = LinAlg.copy(truth);
            }
            gn.attributes = attributes.copy();
            return gn;
        }
    }

    static class GExtrinsicsNode extends GNode
    {
        // state:
        // 0-2: xyz
        // 3-5: rpy

        public int getDOF()
        {
            assert (state.length == 6);
            return state.length; // should be 6
        }

        public static double[] project(double state[], double p[])
        {
            double M[][] = LinAlg.xyzrpyToMatrix(state);

            return LinAlg.matrixAB(M, new double[] { p[0], p[1], 0, 1 });
        }

        public GExtrinsicsNode copy()
        {
            GExtrinsicsNode gn = new GExtrinsicsNode();
            gn.state = LinAlg.copy(state); 
            gn.init = LinAlg.copy(init);
            
            if (truth != null)
            {
                gn.truth = LinAlg.copy(truth);
            }
            
            gn.attributes = attributes.copy();
            return gn;
        }
    }

    static class GCalibrateEdge extends GEdge
    {
        // each correspondence is: worldx, worldy, imagex, imagey
        ArrayList<double[]> correspondences;
        BufferedImage im;

        public GCalibrateEdge(ArrayList<double[]> correspondences, BufferedImage im)
        {
            this.nodes = new int[] { -1, -1 }; // make sure someone sets us
                                               // later.
            this.correspondences = correspondences;
            this.im = im;
        }

        public int getDOF()
        {
            return correspondences.size();
        }

        public void draw(Graph g, VisWorld.Buffer vb, double xoff, double yoff, double xsize)
        {
            ArrayList<double[]> projected = new ArrayList<double[]>();
            VisChain errs = new VisChain();

            for (double corr[] : correspondences)
            {
                double pp[] = project(g, new double[] { corr[0], corr[1] });
                projected.add(new double[] { pp[0], pp[1] });
                ArrayList<double[]> line = new ArrayList<double[]>();
                line.add(new double[] { corr[2], corr[3] });
                line.add(new double[] { pp[0], pp[1] });
                errs.add(new VisData(line, new VisDataLineStyle(Color.orange, 1)));
            }

            vb.addBuffered(new VisChain(LinAlg.translate(xoff, yoff, 0), LinAlg.scale(xsize / im.getWidth(), xsize / im.getWidth(), 1), new VisImage(im), LinAlg.translate(0, im.getHeight(), 0), LinAlg.scale(1, -1, 1), errs, new VisData(projected, new VisDataPointStyle(Color.cyan, 3))));
        }

        double[] project(Graph g, double worldxy[])
        {
            GIntrinsicsNode gin = (GIntrinsicsNode) g.nodes.get(nodes[0]);
            GExtrinsicsNode gex = (GExtrinsicsNode) g.nodes.get(nodes[1]);

            return GIntrinsicsNode.project(gin.state, GExtrinsicsNode.project(gex.state, worldxy), im.getWidth(), im.getHeight());
        }

        public double getChi2(Graph g)
        {
            double err2 = 0;
            for (double corr[] : correspondences)
            {
                err2 += LinAlg.sq(getResidual(g, corr));
            }

            return err2;
        }

        public double getResidual(Graph g, double corr[])
        {
            double p[] = project(g, new double[] { corr[0], corr[1] });
            return LinAlg.distance(p, new double[] { corr[2], corr[3] });
        }

        public Linearization linearize(Graph g, Linearization lin)
        {
            if (lin == null)
            {
                lin = new Linearization();

                for (int nidx = 0; nidx < nodes.length; nidx++)
                {
                    lin.J.add(new double[correspondences.size()][g.nodes.get(nodes[nidx]).state.length]);
                }

                lin.R = new double[correspondences.size()];
                lin.W = LinAlg.identity(correspondences.size());

                // chi2 is sum of error of each correspondence, so W
                // should just be 1.
            }

            for (int cidx = 0; cidx < correspondences.size(); cidx++)
            {
                lin.R[cidx] = getResidual(g, correspondences.get(cidx));

                for (int nidx = 0; nidx < nodes.length; nidx++)
                {
                    GNode gn = g.nodes.get(nodes[nidx]);

                    double s[] = LinAlg.copy(gn.state);
                    for (int i = 0; i < gn.state.length; i++)
                    {
                        double eps = Math.max(0.001, Math.abs(gn.state[i]) / 1000);

                        gn.state[i] = s[i] + eps;
                        double chiplus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                        gn.state[i] = s[i] - eps;
                        double chiminus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                        lin.J.get(nidx)[cidx][i] = (chiplus - chiminus) / (2 * eps);

                        gn.state[i] = s[i];
                    }
                }
            }

            return lin;
        }

        public GCalibrateEdge copy()
        {
            assert (false);
            return null;
        }

        public void write(StructureWriter outs) throws IOException
        {
            assert (false);
        }

        public void read(StructureReader ins) throws IOException
        {
            assert (false);
        }
    }

    @Override
    public void displayMsg(String msg, boolean error)
    {}

    @Override
    public void go(String configPath, String... urls)
    {
        try
        {
            this.configPath = configPath;
            this.url = urls[urlId];
            config = new ConfigFile(configPath);
            ConfigUtil.verifyConfig(config);

            vwImages.clear();

            // ///////////////////////
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

            g = new Graph();
            double f = 400; // XXX random guess for f
            g.nodes.add(new GIntrinsicsNode(f, f, captureThread.height/2.0, captureThread.width/2.0));
        } catch (IOException e)
        {
            e.printStackTrace();
        }catch (ConfigException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void stop()
    {
        try
        {
            if(iterateThread != null)
            {
                iterateThread.stop = true;
                iterateThread.join();
            }
            
            if(captureThread != null)
            {
                captureThread.stop = true;
                captureThread.join();
            }
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void showDirections(boolean show)
    {
        VisWorld.Buffer vbDirections = vw.getBuffer("directions");
        vbDirections.setDrawOrder(10);
        
        if(show)
        {
            String directions = "<<left>><<mono-small>> \n \n \n " +
            		               "DIRECTIONS\n \n" +
            		               "<<left>> Place the tag mosaic in the following four positions and click capture:\n \n" +
                                   "<<left>>     1. Close up: camera should be looking straight at the mosaic\n"+
                                   "<<left>>                  and as close as possible\n \n" +
                                   "<<left>>     2. Far away: camera should be looking straight at the mosaic and far\n"+
                                   "<<left>>                  away, but close enough so the camera can see most tags\n \n" +
                                   "<<left>>     3. Angle 1: place camera so that it views the mosaic at a sharp angle\n \n" +
                                   "<<left>>     4. Angle 2: keep the camera at a sharp angle, but turn it 90 degrees\n"+
                                   "<<left>>                  so the video feed is sideways\n \n \n" +
                                   "<<left>>HINT #1: Not all tags must be visible in every image.\n \n" +
                                   "<<left>>HINT #2: The four images above are the minimum; the more unique images\n"+
                                   "<<left>>                 you capture, the better the parameters will be.\n \n \n \n ";

            vbDirections.addBuffered(new VisText(VisText.ANCHOR.CENTER, directions));
        }
        
        vbDirections.switchBuffer();
    }
}
