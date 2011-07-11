package mihai.camera;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import mihai.lcmtypes.image_path_t;

public class ImageGrabber implements LCMSubscriber
{
    private LCM lcm;
    private ArrayList<String> cameras;
    private HashMap<String, byte[]> recentBuffers;
    
    public ImageGrabber()
    {
        lcm = LCM.getSingleton();
        lcm.subscribeAll(this);
        
        cameras = new ArrayList<String>();
        recentBuffers = new HashMap<String, byte[]>();
    }
    
    public static void main(String[] args)
    {
        new ImageGrabber();
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        if(channel.startsWith("rec"))
        {
            try
            {
                image_path_t imagePath = new image_path_t(ins);

                // assuming saved image is a byte array
                byte[] buffer = new byte[imagePath.width * imagePath.height];
                new FileInputStream(new File(imagePath.img_path)).read(buffer);

                // new camera
                if(!cameras.contains(channel))
                {
                    cameras.add(channel);
                }
                recentBuffers.put(channel, buffer);
            } catch (FileNotFoundException e)
            {
                e.printStackTrace();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }        
    }
}
