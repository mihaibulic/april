package edu.umich.mihai;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.swing.JFrame;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.util.GetOpt;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.vis.VisCanvas;
import april.vis.VisImage;
import april.vis.VisTexture;
import april.vis.VisWorld;

public class CalibrationTest implements ParameterListener
{
    private ParameterGUI pg;
    private JFrame jf;
    private BlockingQueue<BufferedImage> queue;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    private boolean toggle = true;
    
    private BufferedImage image;
    private double[] r = {1,1}; // calibration perameters to remove radial distortion
    
    public CalibrationTest(String url, boolean hiRes, boolean gray8, int fps)
    {
        pg = new ParameterGUI();
        pg.addButtons("reset", "toggle image source", "tags", "get tag numbers", "calibrate", "generate r matrix to undestort image");
        pg.addDoubleSlider("r1", "radial distortion1", 0, 1, 1);
        pg.addListener(this);

        queue = new ArrayBlockingQueue<BufferedImage>(60);

        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(1000, 500);

        new Image(url, hiRes, gray8, fps).start();
    }

    class Image extends Thread
    {
        private ImageSource isrc;
        private ImageSourceFormat ifmt;

        public Image(String url, boolean hiRes, boolean gray8, int fps)
        {
            ArrayList<String> cameras = ImageSource.getCameraURLs();

            for (String i : cameras)
            {
                if (i.contains(url))
                {
                    try
                    {
                        isrc = ImageSource.make(i);
                        
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    break;
                }
            }

            if(isrc == null)
            {
                System.out.println("Image source url not found");
                System.exit(1);
            }
            
            // 760x480 8 = 0
            // 760x480 16 = 1
            // 380x240 8 = 2
            // 380x240 16 = 3
            // converts booleans to 1/0 and combines them into an int
            isrc.setFormat(Integer.parseInt("" + (hiRes ? 0 : 1) + (gray8 ? 0 : 1), 2));

            isrc.setFeatureValue(0, 1); // white-balance-manual=1, idx=0
            isrc.setFeatureValue(1, 495); // white-balance-red=495, idx=1
            isrc.setFeatureValue(2, 612); // white-balance-blue=612, idx=2
            isrc.setFeatureValue(3, 0); // exposure-manual=0, idx=3
            isrc.setFeatureValue(5, 0); // brightness-manual=0, idx=5
            isrc.setFeatureValue(7, 0); // gamma-manual=1, idx=7
            isrc.setFeatureValue(9, 1); // timestamps-enable=1, idx=9
            isrc.setFeatureValue(10, 1); // frame-rate-manual=1, idx=10
            isrc.setFeatureValue(11, fps); // frame-rate, idx=11

            ifmt = isrc.getCurrentFormat();

            vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { ifmt.width, ifmt.height });

            isrc.start();
        }

        public void run()
        {
            jf.setVisible(true);

            while (true)
            {
                if(toggle)
                {
                    toggleImageSourceFormat(isrc);
                    toggle = false;
                }
                
                byte imageBuffer[] = null;

                imageBuffer = isrc.getFrame();
                if (imageBuffer == null)
                {
                    System.out.println("err getting frame");
                    toggleImageSourceFormat(isrc);
                    continue;
                }

                imageBuffer = rectify(imageBuffer);
                
                image = ImageConvert.convertToImage(ifmt.format, ifmt.width,
                        ifmt.height, imageBuffer);


                if (image == null)
                {
                    System.out.println("err converting to image");
                    toggleImageSourceFormat(isrc);
                    continue;
                }

                vbImage.addBuffered(new VisImage(new VisTexture(image),new double[] { 0., 0, }, 
                        new double[] {image.getWidth(), image.getHeight() }, true));
                vbImage.switchBuffer();

                queue.add(image);
                
                // XXX move this to thread doing processing 
                try
                {
                    queue.take();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }

        private void toggleImageSourceFormat(ImageSource isrc)
        {
            System.out.println("Toggling imagesource");
            
            isrc.stop();
            int currentFormat = isrc.getCurrentFormatIndex();
            isrc.setFormat(currentFormat);

            isrc.start();
        }

        private byte[] rectify(byte[] image)
        {
            byte[] rectifiedImage = new byte[image.length];
            
            for(int x = (int)(-0.5*ifmt.width); x < ifmt.width/2; x++)
            {
                for(int y = (int)(-0.5*ifmt.height); y < ifmt.height/2; y++)
                {
                    if(y*r[1] < ifmt.height && x*r[0]<ifmt.width)
                    {
                        rectifiedImage[(int)((y*r[1]+ifmt.height/2))*ifmt.width + (int)(x*r[0]+ifmt.width/2)] = image[(y+ifmt.height/2)*ifmt.width + (x+ifmt.width/2)];
                    }
                }
            }
            
            return rectifiedImage;
        }
    }

    public void setCalibrationPerameters() throws Exception
    {
        TagDetector detector = new TagDetector(new Tag36h11());
        int tags = 27;
        double tagsize = 0.216; // meters
        double focal = 485.6; // pixels
        ArrayList<TagDetection> detections = detector.process(image, new double[] {image.getWidth()/2.0, image.getHeight()/2.0});
        double[][] uv = new double[tags][2]; // 2D estimate of upper left hand corner of tags

        double[][] xyz = new double[tags][3]; // actual location of upper left hand corner of tags in 3D plane (without distortion)
        
        double[] abcU = {0,0,0}; // ax^2 + bx + x coefficients for solving over-determined system for U using least-squares 
        double[] abcV = {0,0,0}; // ax^2 + bx + x coefficients for solving over-determined system for V using least-squares

//        if(tags == detections.size())
        if(true)
        {
            for(int i = 0; i < detections.size(); i++)
            {
                
                // upper left hand corner of tag
                uv[i] = detections.get(i).interpolate(-1,-1);
                uv[i][0] -= 768/2;
                uv[i][1] -= 480/2; 
                double M[][]= CameraUtil.homographyToPose(focal, focal, tagsize, detections.get(i).homography);
                xyz[i] = new double[] {M[0][3], M[1][3], M[2][3]};

                abcU[0] += uv[i][0];
                abcU[1] += -2*uv[i][0]*(focal*xyz[i][0]/xyz[i][2]);
                abcU[2] += sq((focal*xyz[i][0]/xyz[i][2]));
                
                abcV[0] += uv[i][1];
                abcV[1] += -2*uv[i][1]*(focal*xyz[i][1]/xyz[i][2]);
                abcV[2] += sq((focal*xyz[i][1]/xyz[i][2]));
            }
            
            if(sq(abcU[1])-(4*abcU[0]*abcU[2]) >= 0)
            {
                r[0] = (-abcU[1]+Math.sqrt(sq(abcU[1])-(4*abcU[0]*abcU[2])))/(2*abcU[0]); // does plus not minus
            }
            else
            {
                r[0] = -abcU[1]/(2*abcU[0]);
            }
            
            if(sq(abcV[1])-(4*abcV[0]*abcV[2]) >= 0)
            {
                r[1] = (-abcV[1]-Math.sqrt(sq(abcV[1])-(4*abcV[0]*abcV[2])))/(2*abcV[0]); // does plus not minus
            }
            else
            {
                r[1] = -abcV[1]/(2*abcV[0]);
            }
        }
        else
        {
            throw new Exception("Not enough tags seen");
        }
    }
    
    static final double sq(double v)
    {
        return v*v;
    }

    private String[] getDetections()
    {
        TagDetector detector = new TagDetector(new Tag36h11());
        ArrayList<TagDetection> detections = detector.process(image, new double[] {image.getWidth()/2.0, image.getHeight()/2.0});
        String[] tags = new String[detections.size()];
        
        for(int i = 0; i < detections.size(); i++)
        {
            tags[i] = "#" + i + "\t" + detections.get(i).id;
        }
        
        return tags;
    }

    public static void main(String[] args)
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "colors", "gray8", "gray8 or gray16");
        opts.addInt('f', "fps", 15, "set the max fps to publish");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480");
        opts.addString('u', "url", "", "camera url");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: record video from multiple cameras.");
            System.out.println("Cameras available:");

            ArrayList<String> cameras = ImageSource.getCameraURLs();

            for (String i : cameras)
            {
                System.out.println(i);
            }

            opts.doHelp();
            System.exit(1);
        }

        if (ImageSource.getCameraURLs().size() == 0)
        {
            System.out.println("No cameras found.  Are they plugged in?");
            System.exit(1);
        }

        new CalibrationTest(opts.getString("url"), opts.getString("resolution").contains("hi"), opts.getString( "colors").contains("8"), opts.getInt("fps"));
    }

    @Override
    public void parameterChanged(ParameterGUI pg, String name)
    {
        if(name == "reset")
        {
            toggle = true;
        }
        else if(name == "calibrate")
        {
            try
            {
                setCalibrationPerameters();
                System.out.println(r[0] + "\t" + r[1]);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        else if(name == "tags")
        {
            String[] detections = getDetections();
            
            for(String d : detections)
            {
                System.out.println(d);
            }
        }
        else if(name == "r1")
        {
            r[0] = pg.gd("r1");
            r[1] = pg.gd("r1");
        }
       
    }


}
