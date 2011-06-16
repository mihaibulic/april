package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.EmptyBorder;
import mihai.calibration.gui.Broadcaster;
import mihai.camera.ImageReader;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Distortion;
import mihai.util.PointUtil;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.jmat.SingularValueDecomposition;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisDataFillStyle;
import april.vis.VisImage;
import april.vis.VisRectangle;
import april.vis.VisText;
import april.vis.VisWorld;

public class IntrinsicsPanel extends Broadcaster implements ImageReader.Listener, ActionListener
{
    private static final long serialVersionUID = 1L;
    
    private boolean reset = true;
    private boolean kill = false;
    
    private ImageReader ir;
    private byte[] imageBuffer;
    private boolean imageReady = false;
    private Object lock = new Object();
    
    private VisWorld vw;
    private VisCanvas vc;
    private VisWorld.Buffer vbImage;
    private VisWorld.Buffer vbDirections;
    private VisWorld.Buffer vbStatus;
    private VisWorld.Buffer vbGray;

    private JButton resetButton;
    private String resetButtonText = "Reset";
    
    private TagDetection tagsShort[][];
    private TagDetection tagsLong[][];
    private int shortTagLoc[][];
    private int longTagLoc[][];
    
    private final static int CORNERS = 4;
    private final static int MOSAIC_SHORT_SIZE = 6;
    private final static int MOSAIC_LONG_SIZE = 8;
    private final static int MOSAIC_OFFSET = 10;
    private final static int MOSAIC_SIZE = MOSAIC_LONG_SIZE*MOSAIC_SHORT_SIZE;

    private double fc[];
    private double cc[];
    private double kc[];
    private double alpha;
    private static final int FC_X = 0;
    private static final int FC_Y = 1;
    private static final int CC_X = 2;
    private static final int CC_Y = 3;
    private static final int KC_0 = 4;
    private static final int KC_1 = 5;
    private static final int KC_2 = 6;
    private static final int KC_3 = 7;
    private static final int KC_4 = 8;
    private static final int ALPHA = 9;

    public IntrinsicsPanel(int id, String url) throws IOException, ConfigException, CameraException
    {
        super(id, new BorderLayout());

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.setBackground(Color.BLACK);
        vbImage = vw.getBuffer("image");
        vbImage.setDrawOrder(1);
        vbStatus = vw.getBuffer("status");
        vbStatus.setDrawOrder(2);
        vbDirections = vw.getBuffer("directions");
        vbDirections.setDrawOrder(3);
        vbGray = vw.getBuffer("gray");
        vbGray.setDrawOrder(4);
        add(vc, BorderLayout.CENTER);
        
        resetButton = new JButton(resetButtonText);
        resetButton.addActionListener(this);
        Box buttonBox = new Box(BoxLayout.X_AXIS);
        buttonBox.setBorder(new EmptyBorder(new Insets(5, 10, 5, 10)));
        buttonBox.add(resetButton);
        buttonBox.add(Box.createHorizontalStrut(30));
        JSeparator separator = new JSeparator();
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.add(separator, BorderLayout.NORTH);
        buttonPanel.add(buttonBox, java.awt.BorderLayout.EAST);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    class Calibrate extends Thread
    {
        private Config config;
        private String configPath;
        private String url;

        private int imageWidth;
        private int imageHeight;
        private String format;
        
        public Calibrate(Config config, String configPath, String url, int imageWidth, int imageHeight, String format)
        {
            this.config = config;
            this.configPath = configPath;
            this.url = url;
            
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.format = format;
        }
        
        public void run()
        {
            while(!kill)
            {
                double[][] tagCorners = getTags(imageWidth, imageHeight, format);

                try
                {
                    config = calibrationSolver(tagCorners, imageWidth, imageHeight, format, config, configPath, url);
                } catch (ConfigException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                
                print(tagCorners, imageWidth, imageHeight, format);
            }
        }
    }
    
    private double[][] getTags(int imageWidth, int imageHeight, String format)
    {
        double[][] tagCorners = new double[MOSAIC_SIZE * CORNERS][2];
        TagDetector td = new TagDetector(new Tag36h11());
        tagsShort = new TagDetection[MOSAIC_SHORT_SIZE-2][2]; // not using tags on the end
        tagsLong = new TagDetection[MOSAIC_LONG_SIZE-2][2];  // not using tags on the end
        shortTagLoc = new int[][]{{11,12,13,14},{53,54,55,56}};
        longTagLoc = new int[][]{{16,22,28,34,40,46},{21,27,33,39,45,51}};
        
        BufferedImage image = null;
        
        ArrayList<Integer> tagIDs = new ArrayList<Integer>(MOSAIC_SIZE);
        for (int x = 0; x < MOSAIC_SIZE; x++)
        {
            tagIDs.add(x + MOSAIC_OFFSET);
        }
        
        vc.getViewManager().viewGoal.fit2D(new double[] { 100, 125 }, new double[] { imageWidth - 100, imageHeight - 75 });
        
        double text = imageHeight+80;
        String directions[] = {"DIRECTIONS: Place tag mosaic in front of camera such that all tag corners are visible", 
                                "NOTE: make sure the mosaic is flat, takes up as much of the image as possible,",
                                "       and is as close to the edges of the image as possible (where the majority of distortion is present).", 
                                "HINT: Cover up one tag to prevent the software from detecting all tags until the mosaic is in good position"};
        for(int x = 0; x < directions.length; x++)
        {
            vbDirections.addBuffered(new VisText(new double[]{0,text-18*x}, VisText.ANCHOR.LEFT,directions[x]));
            
        }
        vbDirections.switchBuffer();

        reset = false;
        boolean tagsFound = false;
        while (!tagsFound)
        {
            ArrayList<Integer> tagsToBeSeen = new ArrayList<Integer>();
            tagsToBeSeen.addAll(tagIDs);

            synchronized (lock)
            {
                while (!imageReady)
                {
                    try
                    {
                        lock.wait();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                imageReady = false;
                image = ImageConvert.convertToImage(format, imageWidth, imageHeight, imageBuffer);
            }

            vbImage.addBuffered(new VisImage(image));
            ArrayList<TagDetection> tags = td.process(image, new double[] { imageWidth / 2.0, imageHeight / 2.0 });
            Color color = Color.RED;
            for(TagDetection tag : tags)
            {
                if(tag.id >= MOSAIC_OFFSET && tag.id < MOSAIC_OFFSET+MOSAIC_SIZE)
                {
                    for(int a = 0; a < shortTagLoc.length; a++)
                    {
                        for(int x = 0; x < shortTagLoc[a].length; x++)
                        {
                            if(tag.id == shortTagLoc[a][x])
                            {
                                tagsShort[x][a] = tag;
                            }
                        }
                    }
                    for(int a = 0; a < longTagLoc.length; a++)
                    {
                        for(int y = 0; y < longTagLoc[a].length; y++)
                        {
                            if(tag.id == longTagLoc[a][y])
                            {
                                tagsLong[y][a] = tag;
                            }
                        }
                    }
                    
                    tagsToBeSeen.remove((Integer) tag.id);
                    double[] p0 = tag.interpolate(-1,  1); // do not change the order of these
                    double[] p1 = tag.interpolate( 1,  1);
                    double[] p2 = tag.interpolate(-1, -1);
                    double[] p3 = tag.interpolate( 1, -1);
    
                    tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 0] = p0;
                    tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 1] = p1;
                    tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 2] = p2;
                    tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 3] = p3;
    
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p0[0], imageHeight - p0[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p1[0], imageHeight - p1[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p2[0], imageHeight - p2[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p3[0], imageHeight - p3[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                }
            }

            vbImage.switchBuffer();
            if (tagsToBeSeen.size() == 0)
            {
                tagsFound = true;
            }
        }
        
        return tagCorners;
    }

    private Config calibrationSolver(double[][] tagCorners, int imageWidth, int imageHeight, String format, 
            Config config, String configPath, String url) throws ConfigException, IOException
    {
        double tagSize = config.requireDouble("mosaic_tag_size");
        double smallSpacing = config.requireDouble("mosaic_small_spacing");
        double largeSpacing = config.requireDouble("mosaic_large_spacing");
        
        fc = new double[]{ 500, 500}; // better init?
        cc = new double[]{ imageWidth/2.0, imageHeight/2.0};
        kc = new double[]{ 0, 0, 0, 0, 0 };
        alpha = 0;

        double i[] = getI(fc,cc,kc,alpha);
        double oldI[] = null;
        
        Straightness s = new Straightness(tagCorners, imageWidth, imageHeight, tagSize, tagsShort, tagsLong, smallSpacing, largeSpacing);
        
        double[] r = LinAlg.scale(s.evaluate(i), -1);
        double[] oldR = null;

        double eps[] = new double[i.length];
        for (int x = 0; x < eps.length; x++)
        {
            eps[x] = 0.00001;
        }

        vbGray.addBuffered(new VisChain(new VisRectangle(new double[] { 0, 0 }, new double[] { imageWidth, imageHeight }, 
                new VisDataFillStyle(new Color(0x55FFFFFF, true)))));
        vbGray.switchBuffer();

        String[] dots = {".   ", "..  ", "... ", "...."};
        int count = 0;
        int ittLimit = 500;
        double threshold = 0.5;
        while (!shouldStop(r, oldR, threshold) && ++count < ittLimit && !reset)
        {
            vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP, "Please wait while calibration takes place" + dots[count%dots.length] + "\n " +
                    "focal length (x,y):  " + Util.round(getFc(i)[0], 0) + ", " + Util.round(getFc(i)[1], 0) + "\n" + 
                    "image center (x,y):  " + Util.round(getCc(i)[0], 0) + ", " + Util.round(getCc(i)[1], 0) + "\n" + 
                    "distortion parameters:  "+ Util.round(getKc(i)[0], 3) +", "+Util.round(getKc(i)[1],3)+", "+Util.round(getKc(i)[2],3)+", "+Util.round(getKc(i)[3],3)+", "+ Util.round(getKc(i)[4],3) + "\n" +
                    "alpha:  " + Util.round(getAlpha(i),3))); 
            vbDirections.switchBuffer();
            
            // compute jacobian
            double _J[][] = NumericalJacobian.computeJacobian(s, i, eps);
            Matrix J = new Matrix(_J);
            Matrix JtJ = J.transpose().times(J);
            
            SingularValueDecomposition svd = new SingularValueDecomposition(JtJ);
            double scale = getMax(svd.getSingularValues())/10000;
            scale = scale > 500 ? 500 : scale < 0.1 ? 0.1 : scale; // limit the range of scale
            Matrix JTtimesJplusI = JtJ.plus(scaledIdentityMatrix(i.length, scale));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = null;
            try
            {
                dx = JTtimesJplusI.solve(JTr);
            } catch(Exception e)
            {
                e.printStackTrace();
                break;
            }

            // adjust guess
            oldI = i.clone();
            for (int x = 0; x < dx.getRowDimension(); x++)
            {
                if(x == KC_0 || x == KC_1)
                {
                    i[x] += 0.01 * dx.get(x, 0); // scale helps stabilize results
                }
                else //XXX I don't solve for fc, cc, alpha, kc2,3,4
                {
                    i[x] += 0.0001 * dx.get(x, 0); // scale helps stabilize some components
                }
            }

            // compute residual
            vbStatus.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, 
                    "count: " + count + ", min: " + Util.round(getError(r),3)));
            vbStatus.switchBuffer();
            oldR = r.clone();
            r = LinAlg.scale(s.evaluate(i), -1);
        }
        
        if(getError(r) < threshold)
        {
            fc = getFc(oldI);
            cc = getCc(oldI);
            kc = getKc(oldI);
            alpha = getAlpha(oldI);
            
            //XXX I don't solve for fc or cc
//            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "fc", fc);
//            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "cc", cc);
            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "kc", kc);
            config = Util.setValue(configPath, new String[]{Util.getSubUrl(config, url)}, "alpha", alpha);
        }
        else
        {
            errorReset(getError(r));
        }
        
        return config;
    }
    
    private double getError(double[] r)
    {
        return LinAlg.magnitude(r)/r.length;
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        return (oldR != null && getError(r) < threshold && getError(r) > getError(oldR));
    }
    
    class Straightness extends Function
    {
        double tagCorners[][];
        int imageWidth;
        int imageHeight;
        
        double tagSize;
        double smallSpacing;
        double largeSpacing;
        TagDetection tagsShort[][];
        TagDetection tagsLong[][];
        
        public Straightness(double[][] tagCorners, int imageWidth, int imageHeight, double tagSize, 
                            TagDetection[][] tagsShort, TagDetection[][] tagsLong, double smallSpacing, double largeSpacing)
        {
            this.tagCorners = tagCorners;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            
            this.tagSize = tagSize;
            this.smallSpacing = smallSpacing;
            this.largeSpacing = largeSpacing;
            this.tagsShort = tagsShort;
            this.tagsLong = tagsLong;
        }
        
        public double[] evaluate(double[] intrinsics)
        {
            return evaluate(intrinsics, null);
        }
        
        @Override
        public double[] evaluate(double[] i, double[] straightness)
        {
            if (straightness == null)
            {
                // each row/column can provide 2 lines and fc needs 2 spots
//                straightness = new double[2*(MOSAIC_SHORT_SIZE + MOSAIC_LONG_SIZE)+2];  //XXX fc
                straightness = new double[2*(MOSAIC_SHORT_SIZE + MOSAIC_LONG_SIZE)];
            }
            
            // init intrinsics
            double tempFc[] = getFc(i); //used a lot
            Distortion pd = new Distortion(tempFc, getCc(i), getKc(i), getAlpha(i), imageWidth, imageHeight);
            
            // calculate new tag corner locations
            double[][] newTagCorners = new double[tagCorners.length][2];
            for (int a = 0; a < tagCorners.length; a++)
            {
                newTagCorners[a] = pd.undistort(tagCorners[a]);
            }
          
          //XXX fc
//            straightness[0]=0;
//            for(int y = 0; y < tagsLong.length; y++)
//            {
//                double[][] M1 = CameraUtil.homographyToPose(tempFc[0], tempFc[1], tagSize, tagsLong[y][0].homography);
//                double[][] M2 = CameraUtil.homographyToPose(tempFc[0], tempFc[1], tagSize, tagsLong[y][1].homography);
//                straightness[0] += Math.abs(LinAlg.distance(LinAlg.matrixToXyzrpy(M1), LinAlg.matrixToXyzrpy(M2), 3)-smallSpacing);
//                System.out.println(tagsLong[y][0].id + "." + tagsLong[y][1].id + "=" +Util.round(Math.abs(LinAlg.distance(LinAlg.matrixToXyzrpy(M1), LinAlg.matrixToXyzrpy(M2), 3)), 3));
//            }
//            straightness[0] = Math.pow(500.0*straightness[0]/tagsLong.length,4);
//            
//            straightness[1]=0;
//            for(int x = 0; x < tagsShort.length; x++)
//            {
//                double[][] M1 = CameraUtil.homographyToPose(tempFc[0], tempFc[1], tagSize,tagsShort[x][0].homography);
//                double[][] M2 = CameraUtil.homographyToPose(tempFc[0], tempFc[1], tagSize,tagsShort[x][1].homography);
//                straightness[1] += Math.abs(LinAlg.distance(LinAlg.matrixToXyzrpy(M1), LinAlg.matrixToXyzrpy(M2), 3)-largeSpacing);
//                System.out.println(tagsShort[x][0].id + "." + tagsShort[x][1].id + "=" +Util.round(Math.abs(LinAlg.distance(LinAlg.matrixToXyzrpy(M1), LinAlg.matrixToXyzrpy(M2), 3)), 3));
//            }
//            straightness[1] = Math.pow(500.0*straightness[1]/tagsShort.length,4);
//            System.out.println(straightness[0]+ "\t" + straightness[1]);
            
          //XXX fc
//            int cur = 2;
            int cur = 0;
            for (int x = 0; x < MOSAIC_LONG_SIZE; x++) // long lines
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for (int y = 0; y < MOSAIC_SHORT_SIZE; y++)
                {
                    topLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 0]);
                    topLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 1]);
                    botLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 2]);
                    botLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 3]);
                }
                straightness[cur++] = PointUtil.fitLine(topLine)[3];
                straightness[cur++] = PointUtil.fitLine(botLine)[3];
            }
            for (int y = 0; y < MOSAIC_SHORT_SIZE; y++) // short lines
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for (int x = 0; x < MOSAIC_LONG_SIZE; x++)
                {
                    topLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 0]);
                    topLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 2]);
                    botLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 1]);
                    botLine.add(newTagCorners[x * CORNERS * MOSAIC_SHORT_SIZE + CORNERS * y + 3]);
                }
                straightness[cur++] = PointUtil.fitLine(topLine)[3];
                straightness[cur++] = PointUtil.fitLine(botLine)[3];
            }
            
            return straightness;
        }
    }
    
    private void print(double[][] tagCorners, int width, int height, String format)
    {
        vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP, 
                "Calibration is complete.  The intrinsics have been written to the config file:\n" + 
                "focal length (x,y):  " + Util.round(fc[0],0) + ", " + Util.round(fc[1],0) + "\n" + 
                "image center (x,y):  " + Util.round(cc[0],0) + ", " + Util.round(cc[1],0) + "\n" + 
                "distortion parameters:  "+Util.round(kc[0],3)+", "+Util.round(kc[1],3)+", "+Util.round(kc[2],3)+", "+Util.round(kc[3],3)+", "+Util.round(kc[4],3) + "\n" +
                "alpha:  " + Util.round(alpha,3)));
        vbDirections.switchBuffer();
        vbGray.switchBuffer();

        Distortion d = new Distortion(fc, cc, kc, alpha, width, height, 0.1);
        TagDetector td = new TagDetector(new Tag36h11());
        byte[] undistortedBuffer;
        BufferedImage image;
        while(!reset)
        {
            synchronized (lock)
            {
                while (!imageReady)
                {
                    try
                    {
                        lock.wait();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                imageReady = false;
                undistortedBuffer = d.naiveBufferUndistort(imageBuffer);
                image = ImageConvert.convertToImage(format, width, height, imageBuffer);
            }
            
            vbImage.addBuffered(new VisImage(ImageConvert.convertToImage(format, width, height, undistortedBuffer)));
            ArrayList<TagDetection> tags = td.process(image, cc );
            Color color = Color.BLUE;
            for (TagDetection tag : tags)
            {
                double[] p0 = d.undistort(tag.interpolate(-1, -1));
                double[] p1 = d.undistort(tag.interpolate( 1, -1));
                double[] p2 = d.undistort(tag.interpolate( 1,  1));
                double[] p3 = d.undistort(tag.interpolate(-1,  1));
                
                vbImage.addBuffered(new VisChain(LinAlg.translate(p0[0], height - p0[1], 0.0), 
                        new VisCircle(3, new VisDataFillStyle(color))));
                vbImage.addBuffered(new VisChain(LinAlg.translate(p1[0], height - p1[1], 0.0), 
                        new VisCircle(3, new VisDataFillStyle(color))));
                vbImage.addBuffered(new VisChain(LinAlg.translate(p2[0], height - p2[1], 0.0), 
                        new VisCircle(3, new VisDataFillStyle(color))));
                vbImage.addBuffered(new VisChain(LinAlg.translate(p3[0], height - p3[1], 0.0), 
                        new VisCircle(3, new VisDataFillStyle(color))));
            }

            vbImage.switchBuffer();
        }
    }

    private double getMax(double[] values)
    {
        double max = Double.MIN_VALUE;
        
        for(double v : values)
        {
            if(v > max)
            {
                max = v;
            }
        }
        
        return max;
    }
    
    private Matrix scaledIdentityMatrix(int size, double scale)
    {
        return new Matrix(LinAlg.scale(Matrix.identity(size, size).copyArray(), scale));
    }

    private void errorReset(double error)
    {
        vbStatus.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, 
        "<<red, big>>Error: unable to calculate intrinsics from given image, please try again (" + Util.round(error,3) + ")"));
        vbStatus.switchBuffer();
        reset = true;
    }
    
    private double[] getI(double[] fc, double[] cc, double[] kc, double alpha)
    {
        double[] i = new double[10];
        i[FC_X] = fc[0]/1000.0;
        i[FC_Y] = fc[1]/1000.0;
        i[CC_X] = cc[0]/1000.0;
        i[CC_Y] = cc[1]/1000.0;
        i[KC_0] = kc[0];
        i[KC_1] = kc[1];
        i[KC_2] = kc[2];
        i[KC_3] = kc[3];
        i[KC_4] = kc[4];
        i[ALPHA] = alpha;
        
        return i;
    }
    
    private double[] getFc(double[] i)
    {
        return new double[]{1000*i[FC_X], 1000*i[FC_Y]};
//        return new double[]{i[FC_X], i[FC_Y]};
    }
    
    private double[] getCc(double[] i)
    {
        return new double[]{1000*i[CC_X], 1000*i[CC_Y]};
//        return new double[]{i[CC_X], i[CC_Y]};
    }
    
    private double[] getKc(double[] i)
    {
        return new double[]{i[KC_0], i[KC_1], i[KC_2], i[KC_3], i[KC_4]};
    }
    
    private double getAlpha(double[] i)
    {
        return i[ALPHA];
    }
    
    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        synchronized (lock)
        {
            imageBuffer = image;
            imageReady = true;
            lock.notify();
        }
    }

    public void actionPerformed(ActionEvent ae)
    {
        if(ae.getActionCommand().equals(resetButtonText))
        {
            reset = true;
        }
    }
    
    @Override
    public void go(String configPath, String...urls)
    {
        try
        {
            Config config = new ConfigFile(configPath);
            Util.verifyConfig(config);
            ir = new ImageReader(config, urls[0]);

            if (ir.isGood())
            {
                int imageWidth = ir.getWidth();
                int imageHeight = ir.getHeight();
                String format = ir.getFormat();
                
                ir.addListener(this);
                ir.start();
                
                new Calibrate(config, configPath, urls[0], imageWidth, imageHeight, format).start();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }catch (ConfigException e)
        {
            e.printStackTrace();
        } catch (CameraException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void stop()
    {
        kill = true;
        try
        {
            ir.kill();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {}
}
