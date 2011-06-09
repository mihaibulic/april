package mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JPanel;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.PointDistortion;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
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

public class IntrinsicsCalibrator extends JPanel implements ImageReader.Listener
{
    private static final long serialVersionUID = 1L;

    private ImageReader ir;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    
    private int width;
    private int height;
    private String format;
    
    private VisWorld vw;
    private VisCanvas vc;
    VisWorld.Buffer vbImage;
    VisWorld.Buffer vbDirections;
    
    int mosaicWidth = 6;
    int mosaicHeight = 8;
    int corners = 4;
    int mosaicOffset = 10;
    int mosaicSize = mosaicHeight * mosaicWidth;
    double[][] tagCorners = new double[mosaicSize*4][2];
    
//    private ParameterGUI pg;

    public IntrinsicsCalibrator(Config config, String url) throws IOException, ConfigException, CameraException
    {
        super();
        setLayout(new BorderLayout());
        
        Util.verifyConfig(config);
        
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        add(vc, BorderLayout.CENTER);

//        pg = new ParameterGUI();
//        add(pg, BorderLayout.SOUTH);
//        pg.addDoubleSlider("r", "r", 0.5, 2, 2.0);
//        pg.addDoubleSlider("r2", "r2", -.005, .005, 0);

        ir = new ImageReader(config, url);
        ir.addListener(this);
        
        if(ir.isGood())
        {
            width = ir.getWidth();
            height = ir.getHeight();
            format = ir.getFormat();
            
            vc.setBackground(Color.BLACK);

            vbImage = vw.getBuffer("image");
            vbImage.setDrawOrder(1);
            vbDirections = vw.getBuffer("directions");
            vbDirections.setDrawOrder(1000);
            
            ir.start();

            new TagThread().start();
//            new ManualThread().start();
            new AutoThread().start();
        }
    }
    
    class TagThread extends Thread
    {
        public void run()
        {
            BufferedImage image;
            TagDetector td = new TagDetector(new Tag36h11());
            ArrayList<TagDetection> tags;
            ArrayList<Integer> tagIDs = new ArrayList<Integer>(mosaicSize);
            for(int x = 0; x < mosaicSize; x++)
            {
                tagIDs.add(x+mosaicOffset);
            }
            
            vc.getViewManager().viewGoal.fit2D(new double[] { 100, 100 }, new double[] { width-100, height-100});
            vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP, 
                    "DIRECTIONS: Place tag mosaic in front of camera such that all tag corners are visible\n" +
                    "NOTE: make sure the mosaic is flat and takes up most of the camera image.  " +
                            "Also make sure it is not angled with respect to the camera\n" +
                    "HINT: Cover up one tag to prevent the software from detecting all tags until " +
                    "the mosaic is in good position"));
            vbDirections.switchBuffer();
            
            boolean tagsFound = false;
            while (!tagsFound)
            {
                ArrayList<Integer> tagsToBeSeen = new ArrayList<Integer>();
                tagsToBeSeen.addAll(tagIDs);
                
                synchronized(lock)
                {
                    while(!imageReady)
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
                tags = td.process(image, new double[]{width/2.0, height/2.0});
                for(TagDetection tag : tags)
                {
                    tagsToBeSeen.remove((Integer)tag.id);
                    double[] p0 = tag.interpolate(-1, -1);
                    double[] p1 = tag.interpolate( 1, -1);
                    double[] p2 = tag.interpolate( 1,  1);
                    double[] p3 = tag.interpolate(-1,  1);
                    
                    tagCorners[(tag.id-mosaicOffset)*4 + 0] = p0;
                    tagCorners[(tag.id-mosaicOffset)*4 + 1] = p1;
                    tagCorners[(tag.id-mosaicOffset)*4 + 2] = p2;
                    tagCorners[(tag.id-mosaicOffset)*4 + 3] = p3;
                    
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p0[0],height-p0[1],0.0), new VisCircle(3, new VisDataFillStyle(Color.RED))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p1[0],height-p1[1],0.0), new VisCircle(3, new VisDataFillStyle(Color.RED))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p2[0],height-p2[1],0.0), new VisCircle(3, new VisDataFillStyle(Color.RED))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p3[0],height-p3[1],0.0), new VisCircle(3, new VisDataFillStyle(Color.RED))));
                }
                
                vbImage.switchBuffer();
                if(tagsToBeSeen.size() == 0)
                {
                    tagsFound = true;
                }
            }
            
            vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP, 
                    "Please wait while calibration takes place..."));
            vbDirections.switchBuffer();

            
            VisWorld.Buffer vbGray = vw.getBuffer("gray");
            vbGray.setDrawOrder(4);
            vbGray.addBuffered(new VisChain(
                    new VisRectangle(
                            new double[]{0,0}, new double[]{width, height}, 
                            new VisDataFillStyle(new Color(0x55FFFFFF, true)))));
            vbGray.switchBuffer();
        }
    }

    class AutoThread extends Thread
    {
        public void run()
        {
            double fc[] = {500,500}; // better init?
            double cc[] = {width/2.0, height/2.0};
            double kc[] = {0,0,0,0,0,0};
            double alpha = 0;
            double pointThreshold = 0.1;
            double i[] = {fc[0], fc[1], cc[0], cc[1],
                          kc[0], kc[1], kc[2], kc[3], kc[4], kc[5],
                          alpha};
            double newTagCorners[][] = new double[tagCorners.length][2];
            
            double delta = 0.00001; // experimentally derived
            double eps[] = new double[i.length];
            for(int x = 0; x < eps.length; x++)
            {
                eps[x] = delta;
            }
            double[] r    = null;
            double[] oldR = null;
            
            double solverThreshold = 0.00000001; // experimentally derived
            int ittLimit = 1000;                 // experimentally derived
            int count = 0;
            while(!shouldStop(r, oldR, solverThreshold) && ++count < ittLimit)
            {
                fc = new double[]{i[0], i[1]};
                cc = new double[]{i[2], i[3]};
                kc = new double[]{i[4], i[5], i[6], i[7], i[8], i[9]};
                alpha = i[10];
                PointDistortion pd = new PointDistortion(fc, cc, kc, alpha, width, height, pointThreshold);
                
                for(int x = 0; x < tagCorners.length; x++)
                {
                    newTagCorners[x] = pd.undistort(tagCorners[x]);
                }
                Straightness s = new Straightness(newTagCorners);

                // compute residual
                oldR = r.clone();
                r = LinAlg.scale(s.evaluate(i), -1);
                
                // compute jacobian
                double _J[][] = NumericalJacobian.computeJacobian(s, i, eps);
                Matrix J = new Matrix(_J);
              
                Matrix dx = J.solve(Matrix.columnMatrix(r));
                
                // adjust guess
                for (int x = 0; x < dx.getRowDimension(); x++) 
                {
                    i[x] += 0.1*dx.get(x,0); // 0.1 helps stabilize results
                }
            }
        }
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        boolean stop = true;
        int length = 2;
        
        if(oldR != null && r != null)
        {
            for(int x = 0; x < r.length/length; x++)
            {
                double[] cur = new double[length];
                double[] old = new double[length];
                for(int y = 0; y < length; y++)
                {
                    cur[y] = r[x*length+y];
                    old[y] = oldR[x*length+y];
                }
                
                if(LinAlg.distance(cur, old) > threshold)
                {
                    stop = false;
                    break;
                }
            }
        }
        else
        {
            stop = false;
        }
        
        return stop;
    }
    
    class Straightness extends Function
    {
        double tagCorners[][];
        
        public Straightness(double[][] tagCorners)
        {
            this.tagCorners = tagCorners;
        }

        public double[] evaluate(double[] intrinsics)
        {
            return evaluate(intrinsics, null);
        }
        
        @Override
        public double[] evaluate(double[] intrinsics, double[] straightness)
        {
            if(straightness == null)
            {
                // the tags are spread out 6x8 on the mosaic
                //      each row/column can provide 2 lines
                straightness = new double[6*2 + 8*2];
            }
            
            //width=6 height=8
            int cur = 0;
            for(int y = 0; y < mosaicHeight; y++)
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for(int x = 0; x < mosaicWidth; x++)
                {
                    topLine.add(tagCorners[y*corners*mosaicWidth+corners*x+0]);
                    topLine.add(tagCorners[y*corners*mosaicWidth+corners*x+1]);
                    botLine.add(tagCorners[y*corners*mosaicWidth+corners*x+2]);
                    botLine.add(tagCorners[y*corners*mosaicWidth+corners*x+3]);
                }
                straightness[cur++] = LinAlg.fitLine(topLine)[2];
                straightness[cur++] = LinAlg.fitLine(botLine)[2];
            }
            for(int x = 0; x < mosaicWidth; x++)
            {
                ArrayList<double[]> topLine = new ArrayList<double[]>();
                ArrayList<double[]> botLine = new ArrayList<double[]>();
                for(int y = 0; y < mosaicHeight; y++)
                {
                    topLine.add(tagCorners[x*corners*mosaicHeight+corners*y+0]);
                    topLine.add(tagCorners[x*corners*mosaicHeight+corners*y+1]);
                    botLine.add(tagCorners[x*corners*mosaicHeight+corners*y+2]);
                    botLine.add(tagCorners[x*corners*mosaicHeight+corners*y+3]);
                }
                straightness[cur++] = LinAlg.fitLine(topLine)[2];
                straightness[cur++] = LinAlg.fitLine(botLine)[2];
            }
            
            return straightness;
        }
        
    }
    
//    class ManualThread extends Thread
//    {
//        public void run()
//        {
//           VisWorld.Buffer vbGrid = vw.getBuffer("grid");
//           vbGrid.setDrawOrder(3);
//           VisGrid vg = new VisGrid(10);
//           vg.gridColor = Color.RED;
//           vg.autoColor = false;           
//           
//           vbGrid.addBuffered(vg);
//           vbGrid.switchBuffer();
//           vc.getViewManager().viewGoal.fit2D(new double[] { 250, 250 }, new double[] { width-250, height-250});
//           
//            boolean linesStraight = false;
//            while (!linesStraight)
//            {
//                synchronized(lock)
//                {
//                    while(!imageReady)
//                    {
//                        try
//                        {
//                            lock.wait();
//                        } catch (InterruptedException e)
//                        {
//                            e.printStackTrace();
//                        }
//                    }
//                    imageReady = false;
//                    BufferedImage originalImage = ImageConvert.convertToImage(format, width, height, imageBuffer);
//                    BufferedImage rectifiedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
//
//                    double cx = width / 2.0;
//                    double cy = height / 2.0;
//
//                    double A = pg.gd("r");
//                    double B = pg.gd("r2");
//
//                    for (int y = 0; y < height; y++)
//                    {
//                        for (int x = 0; x < width; x++)
//                        {
//                            double dy = y - cy;
//                            double dx = x - cx;
//
//                            double theta = Math.atan2(dy, dx);
//                            double r = Math.sqrt(dy * dy + dx * dx);
//
//                            double rp = A * r + B * r * r;
//
//                            int nx = (int) Math.round(cx + rp * Math.cos(theta));
//                            int ny = (int) Math.round(cy + rp * Math.sin(theta));
//
//                            if (nx >= 0 && nx < width && ny >= 0 && ny < height)
//                            {
//                                rectifiedImage.setRGB(x, y, originalImage.getRGB((int) nx, (int) ny));
//                            }
//                        }
//                    }
//                    vbImage.addBuffered(new VisImage(rectifiedImage));
//                    vbImage.switchBuffer();
//                }
//            }
//        }
//    }

    public void kill()
    {
        // XXX
    }
    
    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        synchronized(lock)
        {
            imageBuffer = image;
            imageReady = true;
            lock.notify();
        }
    }
}
