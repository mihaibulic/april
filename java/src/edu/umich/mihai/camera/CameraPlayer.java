package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.swing.JFrame;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.util.TimeUtil;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisImage;
import april.vis.VisWorld;
import april.vis.VisWorld.Buffer;
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
    static LCM lcm = LCM.getSingleton();
    String dir;
    int camera;
    int counter;
    int width = 0;
    int height = 0;
    
    public BufferedImage newImage;
    Object newImageCondition = new Object();
    boolean display = false;;
    
    VisWorld vw;
    VisCanvas vc;
    HashMap<Integer, VisWorld.Buffer> buffers;
    JFrame jf;
    
    /**
     * Will notifyAll when newImage arrives.  Can either display images or not
     * @param camera - index of camera to use
     * @param display - do you want to see images dispalyed?
     */
    public CameraPlayer(int camera, boolean all, boolean display)
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

        if(all)
        {
            lcm.subscribeAll(this);
            buffers = new HashMap<Integer, VisWorld.Buffer>();
        }
        else
        {
            lcm.subscribe("cam"+camera, this);
        }
        
        while(true)
        {
            TimeUtil.sleep(100);
        }
    }

    @Override
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            if (channel.contains("cam"))
            {
                Pattern intsOnly = Pattern.compile("\\d+");
                Matcher makeMatch = intsOnly.matcher(channel);
                makeMatch.find();
                camera = Integer.parseInt(makeMatch.group());
                
                synchronized (newImageCondition)
                {
                    image_path_t imagePath = new image_path_t(ins);
                    byte[] buffer = new byte[752*480];
                    new FileInputStream(new File(imagePath.img_path)).read(buffer);
                    newImage = ImageConvert.convertToImage(imagePath.format,imagePath.width, imagePath.height, buffer);
                    newImageCondition.notifyAll();
                }
                
                if(display)
                {
                    VisWorld.Buffer vb = (Buffer) (buffers.containsKey(camera) ? buffers.get(camera) : vw.getBuffer("cam"+camera));
                    
                    vb.addBuffered(new VisChain(LinAlg.translate(new double[] {width*(camera%3),height*(camera/3),0}), new VisImage(newImage)));
                    if(newImage.getWidth() != width || newImage.getHeight() != height)
                    {
                        width = newImage.getWidth();
                        height = newImage.getHeight();
                        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { width, height });
                    }
                    vb.switchBuffer();
                    
                    if(!buffers.containsKey(camera))
                    {
                        buffers.put(camera, vb);
                    }
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
        opts.addBoolean('a', "all", true, "display images from all cameras");
        
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
        
        new CameraPlayer(opts.getInt("camera"), opts.getBoolean("all"), true);
    }
}
