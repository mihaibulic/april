package mihai.camera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import april.util.TimeUtil;

import lcm.lcm.LCM;
import mihai.lcmtypes.image_path_t;

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
    private String outputDir = "";
    private int saveCounter = 0;

    public ImageSaver(CameraDriver driver, String outputDir)
    {
        this.driver = driver;
        id = driver.getCameraId();
        this.outputDir = outputDir + id;
        File dir = new File(this.outputDir);
        dir.mkdirs();
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
                imagePath.img_path = saveImage(driver.getFrameBuffer());
                imagePath.width = width;
                imagePath.height = height;
                imagePath.format = format;
                imagePath.utime = (long)TimeUtil.utime();
                imagePath.id = ""+id;
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

            lcm.publish("camera " + id, imagePath);
        }
    }

    public String getCameraId()
    {
        return id;
    }
    
    private String saveImage(byte[] image) throws NullPointerException, IOException
    {
        String filepath = outputDir + File.separator + "IMG" + saveCounter;

        if (image == null)
        {
    		throw new NullPointerException();
        }

        new FileOutputStream(new File(filepath)).write(image);

        saveCounter++;

        return filepath;
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
