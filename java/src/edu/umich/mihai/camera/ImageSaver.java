package edu.umich.mihai.camera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import lcm.lcm.LCM;
import april.jcam.ImageSourceFormat;
import edu.umich.mihai.lcmtypes.image_path_t;

/**
 * 
 * Saves images (byte[]) given to it by ImageReader to a specified output directory and publishes the path via an LCM message 
 * 
 * @author Mihai Bulic
 *
 */
public class ImageSaver extends Thread implements ImageReader.Listener
{
    private LCM lcm = LCM.getSingleton();

    private boolean run = true;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    private double timeStamp = 0;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
    private String url;
    private int index = 0; 
    private String outputDir = "";
    private int saveCounter = 0;

    public ImageSaver(ImageReader ir, String url, String outputDir)
    {
    	index = ir.getIndex();
        this.outputDir = outputDir + "cam" + index;
        this.url = url;
        File dir = new File(this.outputDir);
        dir.mkdirs();
        
        ir.addListener(this);
        ir.start();
    }
    
    public void run()
    {
        if(url == null) return;
        
        while (run)
        {
            synchronized(lock)
            {
                try
                {
                    while(!imageReady)
                    {
                        lock.wait();
                    }
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
	            image_path_t imagePath = new image_path_t();
	
	            try
	            {
	                imagePath.img_path = saveImage(imageBuffer);
	                imagePath.width = width;
	                imagePath.height = height;
	                imagePath.format = format;
	                imagePath.utime = (long)timeStamp;
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
	
	            lcm.publish("cam" + index, imagePath);
	            imageReady = false;
            }
        }
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
     * Stops the reader in a safe way
     */
    public void kill()
    {
    	synchronized(lock)
        {
    		run = false;
	    	imageReady = true;
	    	lock.notify();
        }
    }

    public void handleImage(byte[] im, ImageSourceFormat ifmt, double time, int camera)
    {
        synchronized(lock)
        {
            imageBuffer = im;
            width = ifmt.width;
            height = ifmt.height;
            format = ifmt.format;
            timeStamp = time;
            imageReady = true;
            lock.notify();
        }
    }
}
