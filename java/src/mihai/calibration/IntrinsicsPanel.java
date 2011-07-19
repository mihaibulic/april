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
import mihai.util.ConfigException;
import mihai.util.Distortion;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.graph.CholeskySolver;
import april.graph.GEdge;
import april.graph.GNode;
import april.graph.Graph;
import april.graph.GraphSolver;
import april.graph.Linearization;
import april.image.Homography33b;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.jmat.ordering.MinimumDegreeOrdering;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.tag.TagFamily;
import april.util.StructureReader;
import april.util.StructureWriter;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisData;
import april.vis.VisDataLineStyle;
import april.vis.VisDataPointStyle;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisWorld;

public class IntrinsicsPanel extends Broadcaster implements ActionListener
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw;
    private VisCanvas vc;

    private VisWorld vwImages;
    private VisCanvas vcImages;

    private String goButtonText = "Go";
    private String stopButtonText = "Stop";
    private String resetButtonText = "Reset";
    private String captureButtonText = "Capture";
    private JButton goButton = new JButton(goButtonText);
    private JButton resetButton = new JButton(resetButtonText);
    private JButton captureButton = new JButton(captureButtonText);
    
    private TagDetector td = new TagDetector(new Tag36h11());

    private CaptureThread captureThread;
    private IterateThread iterateThread;
    
    private String configPath;
    private String url;
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
    
    public IntrinsicsPanel(String id, String url)
    {
        super(new BorderLayout());
        
        this.url = url;
        
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
        
        // ///////////////////////
        // Create ground truth
        {
            tagPositions = new HashMap<Integer, TagPosition>();
            TagFamily tf = new Tag36h11();
            int tagsPerRow = 24;  // XXX get from config
            
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

                    // XXX get from config
                    tp.cx = x * 0.0254; 
                    tp.cy = -y * 0.0254;
                    tp.size = 0.0254 * 8.0 / 10.0;

                    tagPositions.put(tp.id, tp);
                }
            }
        }
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
        vcImages.getViewManager().viewGoal.fit2D(new double[] { 3.4 , 0 }, new double[] { 3.9, 0.6 });
    }
    
    class CaptureThread extends Thread
    {
        String url;
        ImageSource isrc;
        ImageSourceFormat ifmt;
        boolean stop = false;
        
        // protected by synchronizing on CaptureThread
        GCalibrateEdge lastEdge;
        GExtrinsicsNode lastNode;

        CaptureThread(String url)
        {
            this.url = url;
            
            try
            {
                isrc = ImageSource.make(url);
                ifmt = isrc.getCurrentFormat();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        public int getWidth()
        {
            return ifmt.width;
        }
        
        public int getHeight()
        {
            return ifmt.height;
        }
        
        public void run()
        {
            boolean first = true;
            BufferedImage image;
            BufferedImage undistortedImage;
            
            isrc.start();
            
            while (!stop)
            {
                byte[] imageBuffer = isrc.getFrame();
                image = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imageBuffer);
                Distortion dist = new Distortion(((GIntrinsicsNode) g.nodes.get(0)).state, ifmt.width, ifmt.height);
                undistortedImage = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, dist.naiveBufferUndistort(imageBuffer));    
                
                synchronized(this)
                {
                    lastEdge = null;
    
                    ArrayList<TagDetection> detections = td.process(image, new double[] { ifmt.width / 2.0, ifmt.height / 2.0 });

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
            isrc.stop();
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
        else if(e.getActionCommand().equals(resetButtonText))
        {
            stop();
            alertListener();
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
            int text = captureThread.getHeight()+120;

            int ncorr = 0;
            int count = 0, size = 10;
            double[] oldChi2 = new double[size];
            while(!stop && (!shouldStop(oldChi2, g.getErrorStats().chi2, 0.1, ncorr) || count < 10))
            {
                oldChi2[(count++)%size] = g.getErrorStats().chi2;
                
                gs.iterate();

                double state[] = g.nodes.get(0).state;
                vbDirections.addBuffered(new VisText(new double[]{0,text-20*0}, VisText.ANCHOR.LEFT,"Calibrating..."));
                vbDirections.addBuffered(new VisText(new double[]{0,text-20*1}, VisText.ANCHOR.LEFT, "    focal length:    " + Util.round(state[0],4) + ", " + Util.round(state[1],4)));
                vbDirections.addBuffered(new VisText(new double[]{0,text-20*2}, VisText.ANCHOR.LEFT, "    image center:    " + Util.round(state[2],4) + ", " + Util.round(state[3],4)));
                vbDirections.addBuffered(new VisText(new double[]{0,text-20*3}, VisText.ANCHOR.LEFT, "    distortion:    " + Util.round(state[4],4) + ", " + Util.round(state[5],4) + ", " + Util.round(state[6],4) + ", " + Util.round(state[7],4)));
                vbDirections.addBuffered(new VisText(new double[]{0,text-20*4}, VisText.ANCHOR.LEFT, "    skew:    " + Util.round(state[8],4)));
                
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

                VisWorld.Buffer vbError = vw.getBuffer("error");
                vbError.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, "<<blue, big>>Error: " + Util.round(g.getErrorStats().chi2/ncorr,3)));
                vbError.switchBuffer();
            }
            
            double state[] = g.nodes.get(0).state;
            try
            {
                Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "fc", new double[]{state[0], state[1]});
                Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "cc", new double[]{state[2], state[3]});
                Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "kc", new double[]{state[4], state[5], state[6], state[7], state[8]});
                Util.setValue(configPath, new String[]{Util.getSubUrl(config, url)}, "alpha", state[9]);
            } catch (ConfigException e)
            {
                e.printStackTrace();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            
            vbDirections.addBuffered(new VisText(new double[]{0,text-20*0}, VisText.ANCHOR.LEFT,
                    "Calibration complete!  The camera feed is now undistorted.  If everything looks ok, hit next"));
            vbDirections.addBuffered(new VisText(new double[]{0,text-20*1}, VisText.ANCHOR.LEFT, "    focal length:    " + Util.round(state[0],4) + ", " + Util.round(state[1],4)));
            vbDirections.addBuffered(new VisText(new double[]{0,text-20*2}, VisText.ANCHOR.LEFT, "    image center:    " + Util.round(state[2],4) + ", " + Util.round(state[3],4)));
            vbDirections.addBuffered(new VisText(new double[]{0,text-20*3}, VisText.ANCHOR.LEFT, "    distortion:    " + Util.round(state[4],4) + ", " + Util.round(state[5],4) + ", " + Util.round(state[6],4) + ", " + Util.round(state[7],4)));
            vbDirections.addBuffered(new VisText(new double[]{0,text-20*4}, VisText.ANCHOR.LEFT, "    skew:    " + Util.round(state[8],4)));
            
            vbDirections.switchBuffer();

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
    public void go(String configPath, String[] urls)
    {
        try
        {
            this.configPath = configPath;
            config = new ConfigFile(configPath);
            Util.verifyConfig(config);

            vwImages.clear();
            
            captureThread = new CaptureThread(url);
            captureThread.start();

            g = new Graph();
            double f = 400; // XXX random guess for f
            g.nodes.add(new GIntrinsicsNode(f, f, captureThread.getHeight()/2.0, captureThread.getWidth()/2.0));
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

            vbDirections.addBuffered(new VisChain(new VisText(VisText.ANCHOR.CENTER, directions)));
        }
        
        vbDirections.switchBuffer();
    }
}
