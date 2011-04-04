package edu.umich.mihai.camera;

import java.util.ArrayList;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;

public class Track extends Thread implements ImageReader.Listener
{
    ArrayList<Listener> listeners = new ArrayList<Listener>();
    
    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    static final public int LENGTH_KC = 5;

    // indices for lookup in kc[]
    static final public int KC1 = 0;//^2
    static final public int KC2 = 1;//^4
    static final public int KC3 = 2;//^tangential
    static final public int KC4 = 3;//^tangential
    static final public int KC5 = 4;//^6

    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion, [kc1 kc2 kc3 kc4 kc5 kc6]
    private double alpha; // Skew

    private double[][] transformation;
    
    private int id;
    private ImageReader ir;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    private double timeStamp;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
    private boolean run = true;

    private DummyLEDFinder dlf;
    
    public interface Listener
    {
        public void handleDetections(ArrayList<LEDDetection> leds, int index);
    }
    
    public Track(int id, String url, double[][] transformation, double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        this(id, url, transformation, false, false, 15, fc, cc, kc, alpha);
    }
    
    public Track(int id, String url, double[][] transformation, boolean loRes, boolean color16, int fps, double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        dlf = new DummyLEDFinder();
        
        this.id = id;
        this.transformation = transformation;
        this.fc = fc;
        this.cc = cc;
        this.kc = kc;
        this.alpha = alpha;
        
        System.out.println("Track-Constructor: Starting ImageReader for camera " + url);
        ir = new ImageReader(url, loRes, color16, fps);
        ir.addListener(this);
        ir.start();
        System.out.println("Track-Constructor: ImageReader started for camera " + url);
    }

    public void run()
    {
        ArrayList<LEDDetection> leds = new ArrayList<LEDDetection>();
        
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

    public void handleImage(byte[] image, ImageSourceFormat ifmt, double time)
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
