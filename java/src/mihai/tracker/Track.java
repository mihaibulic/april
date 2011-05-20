package mihai.tracker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import mihai.camera.ImageReader;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageSourceFormat;
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
    private int width = 0;
    private int height = 0;
    private String format = "";

    private DummyLEDFinder dlf;
    
    public interface Listener
    {
        public void handleDetections(ArrayList<ImageObjectDetection> objectsL, HashMap<Integer,ImageObjectDetection> objectsH, int id);
    }
    
    public Track(Config config, String url) throws ConfigException, CameraException, IOException
    {
    	Util.verifyConfig(config);

    	transformation = LinAlg.xyzrpyToMatrix(config.requireDoubles("xyzrpy"));
    	id = config.requireInt("id");
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
        
    	dlf = new DummyLEDFinder(fc, cc, kc, alpha);
    	ir = new ImageReader(config.getRoot(), url);
    	ir.addListener(this);
    }

    public void run()
    {
        ArrayList<ImageObjectDetection> objectsL = new ArrayList<ImageObjectDetection>();
        HashMap<Integer,ImageObjectDetection> objectsH = new HashMap<Integer, ImageObjectDetection>();
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
                objectsL.clear();
                objectsL.addAll(dlf.getObjectUV(imageBuffer, width, height, format));
            }              
            
            for(ImageObjectDetection object : objectsL)
            {
                object.timeStamp = timeStamp;
                object.transformation = transformation;
                
                objectsH.put(object.id, object);
            }
            
            if(objectsL.size() > 0)
            {
                for (Listener listener : listeners)
                {
                    listener.handleDetections(objectsL, objectsH, id);
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

    public void handleImage(byte[] image, ImageSourceFormat ifmt, long time, int camera)
    {
        synchronized(lock)
        {
            imageBuffer = image;
            width = ifmt.width;
            height = ifmt.height;
            format = ifmt.format;
            timeStamp = time;
            imageReady = true;
            lock.notify();
        }        
    }
}
