package mihai.tracker;

import java.io.IOException;
import java.util.ArrayList;
import mihai.camera.CameraDriver;
import mihai.camera.util.CameraException;
import mihai.camera.util.TagDetector2;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import april.config.Config;
import april.jmat.LinAlg;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import aprilO.util.TimeUtil;

/**
 * Tracks a given objects in 3D space (used by ObjectTracker)
 * 
 * @author Mihai Bulic
 *
 */
public class Track extends Thread
{
    ArrayList<Listener> listeners = new ArrayList<Listener>();

    private int id;
    private double[][] transformation;
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion
    private double alpha;// Skew

    private CameraDriver driver;

    // pull out into subclass
    private boolean run = true, done = false;
    private Object lock = new Object();
    
    private TagDetector2 td;
    
    public interface Listener
    {
        public void handleDetections(ArrayList<ImageObjectDetection> objects, double[][] transformation);
    }
    
    public Track(Config config, String url) throws ConfigException, CameraException, IOException
    {
    	ConfigUtil.verifyConfig(config);

    	id = config.requireInt("id");
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
        transformation = LinAlg.xyzrpyToMatrix(config.requireDoubles("xyzrpy"));
        
    	driver = new CameraDriver(url, config);
    	
        // pull out into subclass
    	td = new TagDetector2(new Tag36h11(),fc, cc, kc, alpha);
    }

    public String getCameraId()
    {
    	return driver.getCameraId();
    }
    
    public double[][] getTransformationMatrix()
    {
    	return transformation;
    }
    
    public boolean isGood()
    {
    	return driver.isGood();
    }
    
    public void kill()
    {
        run = false;
        
        synchronized(lock)
        {
            while(!done)
            {
                try
                {
                    lock.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            driver.kill();
        }
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void run()
    {
        driver.start();
        
        while(run)
        {
            ArrayList<TagDetection> tags = td.process(driver.getFrameImage(), cc);
            ArrayList<ImageObjectDetection> objects = new ArrayList<ImageObjectDetection>(tags.size());
            
            for(TagDetection tag : tags)
            {
                objects.add(new ImageObjectDetection(tag.id, id, TimeUtil.utime(), tag.cxy, transformation, fc, cc));
            }
            
            for (Listener listener : listeners)
            {
                listener.handleDetections(objects, transformation);
            }
        }
        
        synchronized(lock)
        {
            driver.kill();
            done = true;
            lock.notify();
        }
    }
}
