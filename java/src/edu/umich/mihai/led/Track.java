package edu.umich.mihai.led;

import java.io.IOException;
import java.util.ArrayList;
import april.config.Config;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import edu.umich.mihai.camera.CamUtil;
import edu.umich.mihai.camera.ImageReader;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.Util;

/**
 * Tracks a given LED in 3D space (used by LEDTracker)
 * 
 * @author Mihai Bulic
 *
 */
public class Track extends Thread implements ImageReader.Listener
{
    ArrayList<Listener> listeners = new ArrayList<Listener>();
    
    private boolean run = true;

    private int id;
    private ImageReader ir;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    private double timeStamp;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
    private DummyLEDFinder dlf;

    private double[][] transformation;
    
    // indices for lookup in kc[]
    private int KC1 = 0;//^2
    private int KC2 = 1;//^4
    private int KC3 = 2;//^tangential
    private int KC4 = 3;//^tangential
    private int KC5 = 4;//^6
    
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion, [kc1 kc2 kc3 kc4 kc5 kc6]
    private double alpha; // Skew
    
    public interface Listener
    {
        public void handleDetections(ArrayList<LEDDetection> leds, int index);
    }
    
    public Track(Config config, String url) throws ConfigException, CameraException, IOException
    {
    	Util.verifyConfig(config);

    	id = config.requireInt("id");
    	transformation = LinAlg.xyzrpyToMatrix(config.requireDoubles("xyzrpy"));
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
    	dlf = new DummyLEDFinder(fc, cc, kc, alpha);

    	ir = new ImageReader(config.getRoot(), url);
    }

    public void run()
    {
        ArrayList<LEDDetection> leds = new ArrayList<LEDDetection>();
        ir.addListener(this);
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
                
                leds.clear();
                leds.addAll(dlf.getLedUV(imageBuffer, width, height, format));
            }              
            
            for(LEDDetection led : leds)
            {
                led.timeStamp = timeStamp;
                led.uv = undistort(led.uv);
                led.transformation = transformation;
            }
            
            if(leds.size() > 0)
            {
                for (Listener listener : listeners)
                {
                    listener.handleDetections(leds, id);
                }
            }
                
            imageReady = false;
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
    
    private double[] undistort(double pixel[])
    {
        double p[] = LinAlg.resize(pixel, 2);

        double centered[] = LinAlg.subtract(p, cc);
        centered[0] = centered[0] / fc[0];
        centered[1] = centered[1] / fc[1];

        double r2 = LinAlg.normF(centered);

        double scale  = 1 +
            (kc[KC1] * r2 +
             kc[KC2] * Math.pow(r2, 2) +
             kc[KC5] * Math.pow(r2, 3));

        double scaled[] = LinAlg.scale(centered, scale);
        double tangential[] =
                    {2 * kc[KC3] * centered[0] * centered[1] +
                     kc[KC4] * (r2 + 2 * centered[0] * centered[0]),
                     kc[KC3] * (r2 + 2 * centered[1] * centered[1]) +
                     2 * kc[KC4] * centered[0] * centered[1]};

        LinAlg.plusEquals(scaled, tangential);
        double result[] = {fc[0] * (scaled[0] + alpha * scaled[1]) +
                           cc[0],
                           scaled[1] * fc[1] + cc[1]};

        return result;
    }
    
    public void kill()
    {
        run = false;
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    public void handleImage(byte[] image, ImageSourceFormat ifmt, double time, int camera)
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
