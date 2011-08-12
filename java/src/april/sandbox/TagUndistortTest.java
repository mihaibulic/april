package april.sandbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFrame;
import april.camera.util.Distortion;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.util.ConfigUtil2;
import april.util.GetOpt;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisDataFillStyle;
import april.vis.VisImage;
import april.vis.VisRectangle;
import april.vis.VisWorld;

/**
 * This is a basic template to be able to read off images from the camera and display them in a GUI 
 */
public class TagUndistortTest implements ParameterListener
{
    private ParameterGUI pg;
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    private boolean toggle = true;
    
    TagDetector td;
    Distortion d;
    
    public TagUndistortTest (String url, boolean hiRes, boolean gray8, int fps)
    {
        d = new Distortion(new double[]{477,477}, new double[]{376,240}, new double[]{-0.30,.12,0,0,0}, 0, 752, 480);
        td = new TagDetector(new Tag36h11());
        
        pg = new ParameterGUI();
        pg.addButtons("Reset", "toggle image source");
        pg.addListener(this);

        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(1000, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
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
            vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { 1, 1});

            while (true)
            {
                if(toggle)
                {
                    toggleImageSourceFormat(isrc);
                    toggle = false;
                }
                
                byte imageBuffer[] = null;
                BufferedImage image = null;

                imageBuffer = isrc.getFrame();
                if (imageBuffer == null)
                {
                    System.out.println("err getting frame");
                    toggleImageSourceFormat(isrc);
                    continue;
                }

                double height = ifmt.height;
                double width = ifmt.width;
                image = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imageBuffer);
                BufferedImage newImage = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, d.naiveBufferUndistort(imageBuffer));
                
                if (image == null)
                {
                    System.out.println("err converting to image");
                    toggleImageSourceFormat(isrc);
                    continue;
                }

                vbImage.addBuffered(new VisChain(new VisImage(image), LinAlg.translate(new double[]{width,0,0}), new VisImage(newImage)));
                ArrayList<TagDetection> tags = td.process(image, new double[]{width/2, height/2});
                
                for(TagDetection tag : tags)
                {
                    TagDetection newTag = d.tagUndistort(tag);
                    
                    Color color = Color.red;
                    
                    double[] p0 = tag.interpolate(-1,  1); // do not change the order of these
                    double[] p1 = tag.interpolate( 1,  1);
                    double[] p2 = tag.interpolate(-1, -1);
                    double[] p3 = tag.interpolate( 1, -1);
    
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p0[0], height - p0[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p1[0], height - p1[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p2[0], height - p2[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p3[0], height - p3[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    
                    color = Color.blue;
                    p0 = newTag.interpolate(-1,  1); // do not change the order of these
                    p1 = newTag.interpolate( 1,  1);
                    p2 = newTag.interpolate(-1, -1);
                    p3 = newTag.interpolate( 1, -1);
    
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p0[0]+width, height - p0[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p1[0]+width, height - p1[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p2[0]+width, height - p2[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));
                    vbImage.addBuffered(new VisChain(LinAlg.translate(p3[0]+width, height - p3[1], 0.0), 
                            new VisCircle(3, new VisDataFillStyle(color))));                        
                    
                    double[][] Mo = CameraUtil.homographyToPose(477, 477, 0.158, tag.homography);
                    double[][] Mn = CameraUtil.homographyToPose(477, 477, 0.158, newTag.homography);
                    double[] o = LinAlg.matrixToXyzrpy(Mo);
                    double[] n = LinAlg.matrixToXyzrpy(Mn);

                    vbImage.addBuffered(new VisChain(Mo, new VisRectangle(0.158, 0.158, new VisDataFillStyle(Color.red ))));
                    vbImage.addBuffered(new VisChain(Mn, new VisRectangle(0.158, 0.158, new VisDataFillStyle(Color.blue))));
                    
                    System.out.println("before: " + ConfigUtil2.round(o[0], 2) + ", " + ConfigUtil2.round(o[1], 2) + ", " + ConfigUtil2.round(o[2], 2) + "  " +
                        ConfigUtil2.round(o[3], 2) + ", " + ConfigUtil2.round(o[4], 2) + ", " + ConfigUtil2.round(o[5], 2));
                    System.out.println("after: " + ConfigUtil2.round(n[0], 2) + ", " + ConfigUtil2.round(n[1], 2) + ", " + ConfigUtil2.round(n[2], 2) + "  " +
                        ConfigUtil2.round(n[3], 2) + ", " + ConfigUtil2.round(n[4], 2) + ", " + ConfigUtil2.round(n[5], 2));
                    System.out.println("***********************");
                }

                vbImage.switchBuffer();
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
    }

    public static void main(String[] args)
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "colors", "gray8", "gray8 or gray16");
        opts.addInt('f', "fps", 15, "set the max fps to publish");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480");
        opts.addString('u', "url", "dc1394", "camera url");

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

        new TagUndistortTest (opts.getString("url"), opts.getString("resolution").contains("hi"), opts.getString( "colors").contains("8"), opts.getInt("fps"));
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if(name == "Reset")
        {
            toggle = true;
        }
    }
}
