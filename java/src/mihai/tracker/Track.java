package mihai.tracker;

import java.io.IOException;
import java.util.ArrayList;

import mihai.camera.ImageReader;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.jmat.LinAlg;

/**
 * Tracks a given objects in 3D space (used by ObjectTracker)
 * 
 * @author Mihai Bulic
 *
 */
public class Track extends Thread implements ImageReader.Listener
{
    ArrayList<Listener> listeners = new ArrayList<Listener>();

    private boolean run = true;
    
    private double[][] transformation;
    private int id;
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion
    private double alpha; // Skew

    private ImageReader ir;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    private long timeStamp;

    private DummyLEDFinder dlf;
    
    public interface Listener
    {
        public void handleDetections(ArrayList<ImageObjectDetection> objects, double[][] transformation);
    }
    
    public Track(Config config, String url) throws ConfigException, CameraException, IOException
    {
    	Util.verifyConfig(config);

    	id = config.requireInt("id");
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
        transformation = LinAlg.xyzrpyToMatrix(config.requireDoubles("xyzrpy"));
        
    	ir = new ImageReader(config.getRoot(), url);
    	ir.addListener(this);

    	dlf = new DummyLEDFinder(fc, cc, kc, alpha, ir.getWidth(), ir.getHeight(), ir.getFormat());
    }

    public void run()
    {
        ArrayList<ImageObjectDetection> objects = new ArrayList<ImageObjectDetection>();
        byte[] temp;

        ir.start();
        
        while(run)
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
                
                imageReady = false;
                temp = imageBuffer.clone();
            }
            
            objects.clear();
            objects.addAll(dlf.getObjectUV(temp));
            
            for(ImageObjectDetection object : objects)
            {
                object.timeStamp = timeStamp;
                object.cameraM = transformation;
                object.cameraID = id;
            }
            
            if(objects.size() > 0)
            {
                for (Listener listener : listeners)
                {
                    listener.handleDetections(objects, transformation);
                }
            }
        }
    }
    
    public int getIndex()
    {
    	return ir.getCameraId();
    }
    
    public double[][] getTransformationMatrix()
    {
    	return transformation;
    }
    
    public boolean isGood()
    {
    	return ir.isGood();
    }
    
    public void kill()
    {
        run = false;
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void handleImage(byte[] image, long time, int camera)
    {
        synchronized(lock)
        {
            imageBuffer = image;
            timeStamp = time;
            imageReady = true;
            lock.notify();
        }        
    }
}
