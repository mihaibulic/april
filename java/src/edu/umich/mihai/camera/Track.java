package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import april.jmat.LinAlg;

public class Track extends Thread
{
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

    // Focal length, in pixels
    private double fc[]; // [X Y]

    // Principal point
    private double cc[]; // [X Y]

    // Distortion
    private double kc[]; // [kc1 kc2 kc3 kc4 kc5 kc6]

    // Skew
    private double alpha;

    private double[][] transformation;
    
    private BlockingQueue<BufferedImage> inputQueue;
    private BlockingQueue<ArrayList<LEDDetection>> outputQueue;
    private ImageReader ir;
    private boolean run = true;
    
    public Track(BlockingQueue<ArrayList<LEDDetection>> outputQueue, String url, double[][] transformation, double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        this(outputQueue, url, transformation, false, false, 15, fc, cc, kc, alpha);
    }
    
    public Track(BlockingQueue<ArrayList<LEDDetection>> outputQueue, String url, double[][] transformation, boolean loRes, boolean color16, int fps, double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        this.transformation = transformation;
        this.fc = fc;
        this.cc = cc;
        this.kc = kc;
        this.alpha = alpha;
        this.outputQueue = outputQueue;
        
        inputQueue = new ArrayBlockingQueue<BufferedImage>(100);
        
        try
        {
            ir = new ImageReader(inputQueue, url, loRes, color16, fps, true);
        } catch (Exception e)
        {
            e.printStackTrace();
        }
        
        ir.start();
    }

    public void run()
    {
        while(run)
        {
            try
            {
                ArrayList<LEDDetection> leds = DummyLEDFinder.getLedUV(inputQueue.take());
                
                for(LEDDetection led : leds)
                {
                    led.uv = undistort(led.uv);
                    led.transformation = transformation;
                }
                outputQueue.put(leds);
            } 
            catch (InterruptedException e)
            {
                e.printStackTrace();
            }
            catch (IllegalStateException ise)
            {
                System.err.println("Led queue is full, emptying...");
                outputQueue.clear();
                continue;
            }
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
}
