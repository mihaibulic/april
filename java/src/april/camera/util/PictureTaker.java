package april.camera.util;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisImage;
import april.vis.VisRectangle;
import april.vis.VisTexture;
import april.vis.VisWorld;

/**
 * This is a basic template to be able to read off images from the camera and display them in a GUI 
 */
public class PictureTaker implements ParameterListener
{
    private ParameterGUI pg;
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    private boolean toggle = true;
    private static String dir = "";
    private BufferedImage image;
    private Object lock = new Object();
    private int saveCounter = 0;
    
    public PictureTaker(String url, boolean hiRes, boolean gray8, int fps)
    {
        pg = new ParameterGUI();
        pg.addButtons("Reset", "toggle image source", "Save Picture", "saves this image");
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
            isrc.setFeatureValue(14, 1); // frame-rate, idx=11
            isrc.setFeatureValue(15, fps); // frame-rate, idx=11

            ifmt = isrc.getCurrentFormat();

            vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { ifmt.width, ifmt.height });

            isrc.start();
        }

        public void run()
        {
            jf.setVisible(true);

            byte[] imageBuffer;
            while (true)
            {
                if(toggle)
                {
                    toggleImageSourceFormat(isrc);
                    toggle = false;
                }

                imageBuffer = isrc.getFrame();
                synchronized(lock)
                {
	                image = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imageBuffer);
                }

                vbImage.addBuffered(new VisChain(new VisImage(new VisTexture(image),new double[] { 0., 0, }, 
                        new double[] {image.getWidth(), image.getHeight() }, true), LinAlg.translate(image.getWidth()/2, image.getHeight()/2), 
                        new VisRectangle(110, 110, new VisDataLineStyle(Color.RED, 2))));
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
        opts.addString('d', "directory", "/home/april/Desktop/", "directory in which to save pictures");

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

        dir = opts.getString("directory");
        
        if (ImageSource.getCameraURLs().size() == 0)
        {
            System.out.println("No cameras found.  Are they plugged in?");
            System.exit(1);
        }

        new PictureTaker(opts.getString("url"), opts.getString("resolution").contains("hi"), opts.getString( "colors").contains("8"), opts.getInt("fps"));
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if(name == "Reset")
        {
            toggle = true;
        }
        if(name == "Save Picture")
        {
            String filepath = dir + File.separator + "IMG" + saveCounter;

            System.out.println(filepath);
            
            synchronized(lock)
            {
	            try 
	            {
	            	ImageIO.write(image, "jpg", new File(filepath));
	            	
				} catch (FileNotFoundException e) 
				{
					e.printStackTrace();
				} catch (IOException e) 
				{
					e.printStackTrace();
				}
            }
            
            saveCounter++;
        }
    }
}
