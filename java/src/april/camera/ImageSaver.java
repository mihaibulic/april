package april.camera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import april.lcmtypes.image_path_t;
import april.util.TimeUtil;

import lcm.lcm.LCM;

/**
 * 
 * Saves images (byte[]) given to it by CameraDriver to a specified output directory and publishes the path via an LCM message 
 * 
 * @author Mihai Bulic
 *
 */
public class ImageSaver extends Thread
{
    private LCM lcm = LCM.getSingleton();

    private boolean run = true;
    private CameraDriver driver;
    
    private String id; 
    private String dir = "";
    private String imagePath = "";
    private int saveCounter = 0;

    public ImageSaver(CameraDriver driver, String dir)
    {
        this.driver = driver;
        this.dir = dir + (!dir.endsWith(File.separator) ? File.separator : "");
        id = driver.getCameraId();
        imagePath = id + File.separator;
        
        (new File(this.dir + imagePath)).mkdirs();
    }
    
    public void run()
    {
        int width = driver.getWidth();
        int height = driver.getHeight();
        String format = driver.getFormat();

        driver.start();
        
        while (run)
        {
            image_path_t imagePath = new image_path_t();

            try
            {
                imagePath.utime = (long)TimeUtil.utime();
                imagePath.id = id;
                imagePath.dir = dir;
                imagePath.img_path = saveImage(driver.getFrameBuffer());
                imagePath.width = width;
                imagePath.height = height;
                imagePath.format = format;
            } catch (NullPointerException e)
            {
            	if(run)
            	{
            		e.printStackTrace();
            	}
            	else
            	{
            		break;
            	}
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            lcm.publish("rec " + id, imagePath);
        }
    }

    public String getCameraId()
    {
        return id;
    }
    
    private String saveImage(byte[] image) throws NullPointerException, IOException
    {
        String path = imagePath + "IMG" + saveCounter;
        
        if (image == null)
        {
    		throw new NullPointerException();
        }

        new FileOutputStream(new File(dir + path)).write(image);

        saveCounter++;

        return path;
    }
    
    /**
     * Stops the driver in a safe way
     * @throws InterruptedException 
     */
    public void kill() throws InterruptedException
    {
        driver.kill();
    }
}
