package edu.umich.mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.swing.JFrame;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.vis.VisCamera;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisSphere;
import april.vis.VisWorld;

public class LEDTracker extends CameraComparator
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbLeds = vw.getBuffer("leds");
    private boolean display;

    private final double version = 0.1;
    private boolean run = true;
    private BlockingQueue<ArrayList<LEDDetection>> queues[]; 
    private ArrayList<LEDDetection> last[];
    private ArrayList<LEDDetection> triangulatedLEDs;
    
    public LEDTracker() throws Exception
    {
        // get cal info from txt file
    }
    
    public LEDTracker(Camera cameras[], double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        this(cameras, false, false, 15, fc, cc, kc, alpha, false);
    }
    
    public LEDTracker(Camera cameras[], boolean loRes, boolean color16, int fps, double[] fc, double[] cc, double[] kc, double alpha) throws Exception
    {
        this(cameras, loRes, color16, fps, fc, cc, kc, alpha, false);
    }
    
    public LEDTracker(Camera cameras[], boolean loRes, boolean color16, int fps, double[] fc, double[] cc, double[] kc, double alpha, boolean display) throws Exception
    {
        this.display = display;
        Track tracks[] = new Track[cameras.length];
        
        triangulatedLEDs = new ArrayList<LEDDetection>();
        
        queues = new ArrayBlockingQueue[tracks.length];
        System.out.println("ICC-Constructor: starting tracks...");
        for (int x = 0; x < tracks.length; x++)
        {
            queues[x] = new ArrayBlockingQueue<ArrayList<LEDDetection>>(256);
            last[x] = new ArrayList<LEDDetection>();
            
            try
            {
               tracks[x] = new Track(queues[x], cameras[x].getUrl(), cameras[x].getTransformationMatrix(), loRes, color16, fps, fc, cc, kc, alpha);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        showGui(cameras);
        
        run();
    }
    
    public void run()
    {
        ArrayList<LEDDetection> detections = new ArrayList<LEDDetection>();
        
        while(run)
        {
            for(int x = 0; x < last.length; x++)
            {
                try
                {
                    last[x] = queues[x].take();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            
            for(int x = 0; x < last.length; x++)
            {
                for(int y = 0; y < last[x].size(); y++)
                {
                    detections.add(last[x].get(y));
                    for(int a = x; x < last.length; a++)
                    {
                        for(int b = y+1; b < last[a].size(); b++)
                        {
                            if(last[x].get(y).id == last[a].get(b).id)
                            {
                                detections.add(last[a].get(b));
                                last[a].remove(b);
                            }
                        }
                    }
                    
                    triangulatedLEDs.add(triangulate(detections));
                    detections.clear();
                }
            }
            
            if(display)
            {
                for(LEDDetection ld : triangulatedLEDs)
                {
                    vbLeds.addBuffered(new VisChain(LinAlg.translate(ld.xyz),
                            new VisSphere(0.1, Color.red)));
                }
                vbLeds.switchBuffer();
            }
        }
    }
    
    private void showGui(Camera cameras[])
    {
        if(display)
        {
            jf = new JFrame("LEDTracker v" + version);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setLayout(new BorderLayout());
            jf.add(vc, BorderLayout.CENTER);
            jf.setSize(1000, 500);
            
            for (Camera cam : cameras)
            {
                Color color = (cam.getIndex() == 11 ? Color.blue : Color.red);
                double[][] camM = cam.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
            }
        }
    }
    
    private LEDDetection triangulate(ArrayList<LEDDetection> ld)
    {
        double transformations[][][] = new double[ld.size()][4][4];
        double variance;
        double lastChange = 1;
        double change = 1;
        double slide[][] = new double[4][4];
        double delta[][] = LinAlg.translate(0,0,0.0001);
        
        for(int x = 0; x < ld.size(); x++)
        {
            transformations[x] = ld.get(x).transformation;
        }
        
        while(change/lastChange > 0 ) // signs are the same
        {
            variance = calculateVar(transformations);
            
            for(int x = 0; x < ld.size(); x++)
            {
                transformations[x] = LinAlg.add(transformations[x], delta);
            }
            
            change = variance - calculateVar(transformations);
            slide = LinAlg.translate(0,0,change);
            
            for(int x = 0; x < ld.size(); x++)
            {
                transformations[x] = LinAlg.add(transformations[x], slide);
            }
        }
        
        return new LEDDetection(calculateAve(transformations), ld.get(0).id);
    }
    
    private double[] calculateAve(double[][][] transformations)
    {
        double average[] = new double[]{0,0,0};
        
        for(int x = 0; x < transformations.length; x++)
        {
            average = LinAlg.add(average, new double[]{transformations[x][3][0], transformations[x][3][1], transformations[x][3][2]});
        }
        
        return LinAlg.scale(average, 1.0/transformations.length);
    }
    
    private double calculateVar(double[][][] transformations)
    {
        double average[] = new double[]{0,0,0};
        double variance[] = new double[]{0,0,0};
        
        average = calculateAve(transformations) ;
        
        for(int x = 0; x < transformations.length; x++)
        {
            double tmp[] = LinAlg.subtract(new double[]{transformations[x][3][0], 
                    transformations[x][3][1], transformations[x][3][2]},average);

            try
            {
                variance = LinAlg.add(elementMultiplication(tmp, tmp), variance);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        
        return LinAlg.magnitude(variance);
    }
    
    private double[] elementMultiplication(double[] a, double[] b) throws Exception
    {
        double c[] = new double[a.length];
        
        if(a.length != b.length)
        {
            throw new Exception("Arrays not of equal size");
        }
        
        for(int x = 0; x < a.length; x++)
        {
            c[x] = a[x] * b[x];
        }
        
        return c;
    }
    
    
    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "colors", "gray8", "gray8 or gray16");
        opts.addInt('f', "fps", 15, "set the max fps to publish");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: Tracks LEDs across multiple cameras.");
            opts.doHelp();
            System.exit(1);
        }

        if (ImageSource.getCameraURLs().size() == 0)
        {
            System.out.println("No cameras found.  Are they plugged in?");
            System.exit(1);
        }

        boolean loRes = opts.getString("resolution").contains("lo");
        boolean color16 = opts.getString("colors").contains("16");
        int fps = opts.getInt("fps");



        
        
        // XXX ***************************** FOR TESTING
        Camera cameras[] = new Camera[3]; // TODO get this?
        cameras[0] = new Camera("dc1394://b09d01008c3f6a", new double[]{0,0,0,0,0,0}); // has J
        cameras[1] = new Camera("dc1394://b09d01008b51b8", new double[]{0,0,0,0,0,0}); // 0 
        cameras[2] = new Camera("dc1394://b09d01008b51ab", new double[]{0,0,0,0,0,0}); // 1
        // XXX ***************************** FOR TESTING
        

        
        
        new LEDTracker(cameras, loRes, color16, fps, new double[]{477.5, 477.5}, new double[]{376,240}, new double[]{0,0,0,0,0}, 0.0, true); // XXX magic numbers
    }
    
    public void kill()
    {
        run = false;
    }

}
