package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JFrame;

import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.util.GetOpt;
import april.util.TimeUtil;
import april.vis.VisCanvas;
import april.vis.VisImage;
import april.vis.VisWorld;
import edu.umich.mihai.lcmtypes.image_path_t;

/**
 * 
 * Plays back the video from a camera. Images are selected from the LCM message stream of image paths.
 *  The images themselves are saved onto the HDD at the location specified by the messages.
 * 
 * @author Mihai Bulic
 *
 */
public class CameraPlayer implements LCMSubscriber
{
    static LCM lcm     = LCM.getSingleton();
    String     dir;
    int        camera;
    int        counter;
    int width = 0;
    int height = 0;
    
    public BufferedImage newImage;
    Object newImageCondition = new Object();
    boolean display = false;;
    
    VisWorld  vw;
    VisCanvas vc;
    JFrame jf;
    
    /**
     * Will notifyAll when newImage arrives.  Can either display images or not
     * @param camera - index of camera to use
     * @param display - do you want to see images dispalyed?
     */
    public CameraPlayer(int camera, boolean display)
    {
        this.display = display;
        this.camera = camera;
        
        if(display)
        {
            vw = new VisWorld();
            vc = new VisCanvas(vw);
            jf = new JFrame("Camera " + camera + " player");
            
            jf.add(vc);
            jf.setSize(500, 500);
            jf.setVisible(true);
        }

        lcm.subscribe("cam"+camera, this);
        
        while(true)
        {
            TimeUtil.sleep(100);
        }
    }

    /**
     * Will notifyAll when newImage arrives.  Can either display images or not (use iff you want to instantiate CameraPlayer for each camera)
     * @param dir - directory that contains images (do not include cam<#> folder)
     */
    public CameraPlayer(String dir)
    {
        this.dir = dir;
    }
    
    /**
     * Will notifyAll when newImage arrives.  Can either display images or not (use iff you don't want to instantiate CameraPlayer for each camera)
     * @param camera - index of camera to use
     * @param dir - directory that contains images (do not include cam<#> folder)
     */
    public CameraPlayer(int camera, String dir)
    {
        counter = -1;
        this.camera = camera;
        this.dir = dir;
    }
    
    /**
     * returns bufferedimage saved on computer
     * @param camera - index pf camera to use
     * @param count - image number you want
     * @param dir - directory that contains images (do not include cam<#> folder)
     * @return
     */
    public static BufferedImage getImage(int camera, int count, String dir)
    {
        BufferedImage image = null;
        
        try
        {
            image = ImageIO.read(new File(dir + File.separator + "cam" + camera + File.separator + "IMG" + count));
        } catch (IOException e)
        {
            System.out.println("is camera null?");
            e.printStackTrace();
        }
        
        return image;
    }
    
    /**
     * returns bufferedimage saved on computer (use iff you specified which camera you are using via constructor or get NPE)
     * @param count
     * @return
     */
    public BufferedImage getImage(int count)
    {
        return getImage(camera, count, dir);
    }
    
    public BufferedImage getNextImage(int camera)
    {
        try
        {
            counter++;
        } catch (Exception e)
        {
            System.out.println("must instantiate class to use this method");
            e.printStackTrace();
        }
        
        return getImage(camera, counter, dir);
    }

    /**
     * returns the next saved image (use iff you specified which camera you are using via constructor or get NPE)
     * @return
     */
    public BufferedImage getNextImage()
    {
        return getNextImage(camera);
    }
    
    @Override
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            if (channel.contains("cam"+camera))
            {
                synchronized (newImageCondition)
                {
                    image_path_t imagePath = new image_path_t(ins);
                    newImage = ImageIO.read(new File(imagePath.img_path));
                    newImageCondition.notifyAll();
                }
                
                if(display)
                {
                    VisWorld.Buffer vb = vw.getBuffer("image");
                    vb.addBuffered(new VisImage(newImage));
                    if(newImage.getWidth() != width || newImage.getHeight() != height)
                    {
                        width = newImage.getWidth();
                        height = newImage.getHeight();
                        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { width, height });
                    }
                    vb.switchBuffer();
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 
     * @param args -h (Usage: displays images from camera specified) -c (index of camera for which to get images)
     */
    public static void main(String[] args)
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addInt('c', "camera", 0, "index of camera for which to get images");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: displays images from camera specified");  
            opts.doHelp();
            System.exit(1);
        }
        
        new CameraPlayer(opts.getInt("camera"), true);
    }
}
