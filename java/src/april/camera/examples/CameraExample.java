package april.camera.examples;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JFrame;
import lcm.lcm.LCM;
import april.camera.CameraDriver;
import april.util.GetOpt;
import aprilO.jcam.ImageSource;
import aprilO.vis.VisCanvas;
import aprilO.vis.VisImage;
import aprilO.vis.VisTexture;
import aprilO.vis.VisWorld;

public class CameraExample
{
    static LCM lcm = LCM.getSingleton();

    private CameraDriver driver;
    private boolean run = true;
    
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    
    public CameraExample(String url) throws Exception
    {
        driver = new CameraDriver(url);
        showGUI();

        run();
    }
    
    public void showGUI()
    {
        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1000, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
    }
    
    public void run()
    {
        byte[] imageBuffer;
        BufferedImage im;
        driver.start();
        
        int w = driver.getWidth();
        int h = driver.getHeight();
        vc.getViewManager().viewGoal.fit2D(new double[]{0,0}, new double[]{w,h});
        
        while(run)
        {
            imageBuffer = driver.getFrameBuffer();
            
            // Do stuff with byte array (example, modify each RGB value as such: red += 10, green +=5, blue += 1)
            for(int x = 0; x < w; x++)
            {
                for(int y = 0; y < h; y++)
                {
                    imageBuffer[y*w + x] += (10<<16 + 5<<8 + 1);
                }
            }

            // Convert to image to display if buffer was modified
//            post = ImageConvert.convertToImage(driver.getFormat(), w, h, imageBuffer);
            
            // Or get the original image directly
            im = driver.getFrameImage();

            vbImage.addBuffered(new VisImage(new VisTexture(im),new double[] { 0, 0}, new double[] {w, h }, true));
            vbImage.switchBuffer();
        }
    }
    
    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();
        opts.addBoolean('h', "help", false, "see this help screen");
        opts.addString('u', "url", "dc1394", "url of camera to use");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: displays images from camera specified");  
            System.out.println("Cameras available:");
            ArrayList<String> urls = ImageSource.getCameraURLs();
            for(String url : urls)
            {
                System.out.println(url);
            }

            opts.doHelp();
            System.exit(1);
        }
        
        new CameraExample(opts.getString("url"));
    }

    public void kill()
    {
        run = false;
    }
}
