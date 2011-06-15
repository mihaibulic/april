package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JButton;
import javax.swing.JPanel;
import mihai.camera.ImageReader;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Distortion;
import mihai.util.Util;
import april.config.Config;
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

public class IntrinsicsCalibrator extends JPanel implements ImageReader.Listener, ActionListener
{
    private static final long serialVersionUID = 1L;
    
    private JButton resetButton;
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

    private final static int CORNERS = 4;
    private final static int MOSAIC_WIDTH = 6;
    private final static int MOSAIC_HEIGHT = 8;
    private final static int MOSAIC_OFFSET = 10;
    private final static int MOSAIC_SIZE = MOSAIC_HEIGHT*MOSAIC_WIDTH;

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

    public IntrinsicsCalibrator(Config config, String configPath, String url) throws IOException, ConfigException, CameraException
    {
        super();
        setLayout(new BorderLayout());

        Util.verifyConfig(config);

        vw = new VisWorld();
        vc = new VisCanvas(vw);
        add(vc, BorderLayout.CENTER);
        
        resetButton = new JButton("Reset Calibration");
        add(resetButton, BorderLayout.SOUTH);
        resetButton.addActionListener(this);
        
        ir = new ImageReader(config, url);
        if (ir.isGood())
        {
            int width = ir.getWidth();
            int height = ir.getHeight();
            String format = ir.getFormat();
            
            vc.setBackground(Color.BLACK);

            vbImage = vw.getBuffer("image");
            vbImage.setDrawOrder(1);
            vbStatus = vw.getBuffer("status");
            vbStatus.setDrawOrder(2);
            vbDirections = vw.getBuffer("directions");
            vbDirections.setDrawOrder(3);
            vbGray = vw.getBuffer("gray");
            vbGray.setDrawOrder(4);
            
            ir.addListener(this);
            ir.start();

            new Calibrate(config, configPath, url, width, height, format).start();
        }
    }

    class Calibrate extends Thread
    {
        Config config;
        String configPath;
        String url;
        int width;
        int height;
        String format;
        
        public Calibrate(Config config, String configPath, String url, int width, int height, String format)
        {
            this.config = config;
            this.configPath = configPath;
            this.url = url;
            this.width = width;
            this.height = height;
            this.format = format;
        }
        
        public void run()
        {
            while(!kill)
            {
                double[][] tagCorners = getTags(width, height, format);

                try
                {
                    config = calibrationSolver(tagCorners, width, height, format, config, configPath, url);
                } catch (ConfigException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                
                print(tagCorners, width, height, format);
            }
        }
    }
    
    private double[][] getTags(int width, int height, String format)
    {
        BufferedImage image;
        double[][] tagCorners = new double[MOSAIC_SIZE * CORNERS][2];
        TagDetector td = new TagDetector(new Tag36h11());
        ArrayList<Integer> tagIDs = new ArrayList<Integer>(MOSAIC_SIZE);
        for (int x = 0; x < MOSAIC_SIZE; x++)
        {
            tagIDs.add(x + MOSAIC_OFFSET);
        }

        vc.getViewManager().viewGoal.fit2D(new double[] { 100, 100 }, new double[] { width - 100, height - 100 });
        vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP, 
                "DIRECTIONS: Place tag mosaic in front of camera such that all tag corners are visible\n" + 
                "NOTE: make sure the mosaic is flat and takes up most of the camera image.  " + 
                "Also make sure it is not angled too much with respect to the camera\n" + 
                "HINT: Cover up one tag to prevent the software from detecting all tags until " + 
                "the mosaic is in good position"));
        vbDirections.switchBuffer();
        
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
                image = ImageConvert.convertToImage(format, width, height, imageBuffer);
            }

            vbImage.addBuffered(new VisImage(image));
            ArrayList<TagDetection> tags = td.process(image, new double[] { width / 2.0, height / 2.0 });
            Color color = Color.RED;
            for (TagDetection tag : tags)
            {
                tagsToBeSeen.remove((Integer) tag.id);
                double[] p0 = tag.interpolate(-1,  1); // do not change the order of these
                double[] p1 = tag.interpolate( 1,  1);
                double[] p2 = tag.interpolate(-1, -1);
                double[] p3 = tag.interpolate( 1, -1);

                tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 0] = p0;
                tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 1] = p1;
                tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 2] = p2;
                tagCorners[(tag.id - MOSAIC_OFFSET) * CORNERS + 3] = p3;

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
            if (tagsToBeSeen.size() == 0)
            {
                tagsFound = true;
            }
        }
        reset = false;
        
        return tagCorners;
    }

    private Config calibrationSolver(double[][] tagCorners, int width, int height, String format, 
            Config config, String configPath, String url) throws ConfigException, IOException
    {
        fc = new double[]{ 500, 500}; // better init?
        cc = new double[]{ width/2.0, height/2.0};
        kc = new double[]{ 0, 0, 0, 0, 0 };
        alpha = 0;

        double i[] = getI(fc,cc,kc,alpha);
        double oldI[] = null;
        
        Straightness s = new Straightness(tagCorners, width, height);
        
        double[] r = LinAlg.scale(s.evaluate(i), -1);
        double[] oldR = null;

        double eps[] = new double[i.length];
        for (int x = 0; x < eps.length; x++)
        {
            eps[x] = 0.00001;
        }

        vbGray.addBuffered(new VisChain(new VisRectangle(new double[] { 0, 0 }, new double[] { width, height }, 
                new VisDataFillStyle(new Color(0x55FFFFFF, true)))));
        vbGray.switchBuffer();

        String[] dots = {".   ", "..  ", "... ", "...."};
        int count = 0;
        int ittLimit = 500;
        double threshold = 0.3;
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
            Matrix JTtimesJplusI = JtJ.plus(scaledIdentityMatrix(i.length, scale(svd.getSingularValues())));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            
            Matrix dx = null;
            try
            {
                dx = JTtimesJplusI.solve(JTr);
            } catch(Exception e)
            {
                e.printStackTrace();
                errorReset();
                break;
            }

            // adjust guess
            oldI = i.clone();
            for (int x = 0; x < dx.getRowDimension(); x++)
            {
                   //tangential distortion  // weak comp   // skew should stay ~0
                if(x == KC_2 || x == KC_3 || x == KC_4 || x == ALPHA)
                {
                    i[x] += 0.0001 * dx.get(x, 0); // scale helps stabilize some components
                }
                else
                {
                    i[x] += 0.1 * dx.get(x, 0); // scale helps stabilize results
                }
            }

            // compute residual
            vbStatus.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, 
                    "count: " + count + ", min: " + Util.round(LinAlg.magnitude(r),3)));
            vbStatus.switchBuffer();
            oldR = r.clone();
            r = LinAlg.scale(s.evaluate(i), -1);
        }
        
        if(shouldStop(r, oldR, threshold))
        {
            fc = getFc(oldI);
            cc = getCc(oldI);
            kc = getKc(oldI);
            alpha = getAlpha(oldI);
            
            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "fc", fc);
            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "cc", cc);
            config = Util.setValues(configPath, new String[]{Util.getSubUrl(config, url)}, "kc", kc);
            config = Util.setValue(configPath, new String[]{Util.getSubUrl(config, url)}, "alpha", alpha);
        }
        else
        {
            errorReset();
        }
        
        return config;
    }
    
    private double scale(double[] values)
    {
        double max = Double.MIN_VALUE;
        
        for(double v : values)
        {
            if(v > max)
            {
                max = v;
            }
        }
        
        return max/100000; // The max is much too large.  Without making it smaller, the step sizes become incredibly small
    }
    
    private Matrix scaledIdentityMatrix(int size, double scale)
    {
        return new Matrix(LinAlg.scale(Matrix.identity(size, size).copyArray(), scale));
    }

    private void errorReset()
    {
        vbStatus.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, 
        "<<red, big>>Error: unable to calculate intrinsics from given image, please try again"));
        vbStatus.switchBuffer();
        reset = true;
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        return (oldR != null && LinAlg.magnitude(r) < r.length*threshold && LinAlg.magnitude(r) > LinAlg.magnitude(oldR));
    }
    
    class Straightness extends Function
    {
        double tagCorners[][];
        int width;
        int height;
        
        public Straightness(double[][] tagCorners, int width, int height)
        {
            this.tagCorners = tagCorners;
            this.width = width;
            this.height = height;
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
                // each row/column can provide 2 lines
                straightness = new double[2*(MOSAIC_WIDTH + MOSAIC_HEIGHT)];
            }
            
            // init intrinsics
            Distortion pd = new Distortion(getFc(i), getCc(i), getKc(i), getAlpha(i), width, height);
            
            // calculate new tag corner locations
            double[][] newTagCorners = new double[tagCorners.length][2];
            for (int x = 0; x < tagCorners.length; x++)
            {
                newTagCorners[x] = pd.undistort(tagCorners[x]);
            }
            
            int cur = 0;
            for (int y = 0; y < MOSAIC_HEIGHT; y++) // long lines
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for (int x = 0; x < MOSAIC_WIDTH; x++)
                {
                    topLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 0]);
                    topLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 1]);
                    botLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 2]);
                    botLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 3]);
                }
                straightness[cur++] = fitLine(topLine);
                straightness[cur++] = fitLine(botLine);
            }
            for (int x = 0; x < MOSAIC_WIDTH; x++) // short lines
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for (int y = 0; y < MOSAIC_HEIGHT; y++)
                {
                    topLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 0]);
                    topLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 2]);
                    botLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 1]);
                    botLine.add(newTagCorners[y * CORNERS * MOSAIC_WIDTH + CORNERS * x + 3]);
                }
                straightness[cur++] = fitLine(topLine);
                straightness[cur++] = fitLine(botLine);
            }

            return straightness;
        }
     
        public double fitLine(ArrayList<double[]> points)
        {
            double ave[] = {0,0};
            for(double[] p : points)
            {
                ave = LinAlg.add(p, ave);
            }
            ave = LinAlg.scale(ave, 1.0/points.size());
            
            double XXminusYY = 0;
            double xy = 0;
            for(int i = 0; i < points.size(); i++)
            {
                double p[] = LinAlg.subtract(points.get(i), ave);
                XXminusYY += (p[0]*p[0])-(p[1]*p[1]); 
                xy += p[0]*p[1]; // (x^2 - y^2)/xy
                points.set(i, p);
            }
            
            double error = 0;
            if(xy != 0)
            {
                double a = XXminusYY/xy;
                
                double roots[] = { Math.atan((-a + Math.sqrt(4+sq(a)))/2), 
                                   Math.atan((-a - Math.sqrt(4+sq(a)))/2) };
        
                double errors[] = {0,0};
                for(int i = 0; i < roots.length; i++)
                {
                    for(double[] p: points)
                    {
                        //y'  =  -x sin(q) + y cos(q)
                        errors[i] += sq(-p[0]*Math.sin(roots[i]) + p[1]*Math.cos(roots[i]));  
                    }
                }
                
                error = Math.min(errors[0], errors[1]);
            }
            else // special case: all points have either the same x or the same y values (clearly this means the error of the line is 0)
            {
                error = 0;
            }
            
            return error/points.size();
        }
        
        private double sq(double a)
        {
            return a*a;
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
        BufferedImage image;
        byte[] undistortedBuffer;
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

    private double[] getI(double[] fc, double[] cc, double[] kc, double alpha)
    {
        double[] i = new double[10];
        i[FC_X] = fc[0];
        i[FC_Y] = fc[1];
        i[CC_X] = cc[0];
        i[CC_Y] = cc[1];
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
        return new double[]{i[FC_X], i[FC_Y]};
    }
    
    private double[] getCc(double[] i)
    {
        return new double[]{i[CC_X], i[CC_Y]};
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

    public void kill()
    {
        kill = true;
    }
    
    public void actionPerformed(ActionEvent ae)
    {
        if(ae.getActionCommand().equals("Reset Calibration"))
        {
            reset = true;
        }
    }
}
