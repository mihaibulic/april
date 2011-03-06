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
import april.vis.VisCircle;
import april.vis.VisData;
import april.vis.VisDataFillStyle;
import april.vis.VisDataLineStyle;
import april.vis.VisSphere;
import april.vis.VisWorld;

public class LEDTracker extends CameraComparator
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbRays = vw.getBuffer("rays");
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbLeds = vw.getBuffer("leds");
    private boolean display;

    private double[] cc;
    private double[] fc;
    
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
        this.fc = fc;
        this.cc = cc;
        this.display = display;
        Track tracks[] = new Track[cameras.length];
        last = new ArrayList[cameras.length];
        
        triangulatedLEDs = new ArrayList<LEDDetection>();
        
        queues = new ArrayBlockingQueue[tracks.length];
        System.out.println("LEDTracker-Constructor: Starting tracks...");
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
            
            tracks[x].start();
        }
        
        showGui(cameras);
        
        run();
    }
    
    public void run()
    {
        ArrayList<LEDDetection> detections = new ArrayList<LEDDetection>();
        
        if(display)
        {
            System.out.println("LEDTracker-run: Display started. Tracking LEDs...");
        }
        else
        {
            System.out.println("LEDTracker-run: Tracks started. Tracking LEDs...");
        }
        
        while(run)
        {
            for(int x = 0; x < last.length; x++)
            {
                try
                {
                    last[x].clear();
                    last[x] = queues[x].take();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            
            System.out.println("LT-run: last is " + last[0].size() + " by " + last[1].size());
            
            for(int x = 0; x < last.length; x++)
            {
                for(int y = 0; y < last[x].size(); y++)
                {
                    detections.add(last[x].get(y));
                    System.out.println("LT-run: added LED #" + last[x].get(y).id + "("+detections.size()+")");
                    for(int a = x+1; a < last.length; a++)
                    {
                        for(int b = 0; b < last[a].size(); b++)
                        {
                            if(last[x].get(y).id == last[a].get(b).id)
                            {
                                detections.add(last[a].get(b));
                                System.out.println("LT-run: added LED #" + last[a].get(b).id + ":("+detections.size()+")");
                                last[a].remove(b);
                            }
                            else
                            {
                                System.out.println("LT-run: skipped LED #" + last[a].get(b).id + 
                                        ", looking for LED #"+ last[x].get(y).id);
                            }
                        }
                    }
                    
                    LEDDetection tmp = triangulate(detections);
                    if(!tmp.singularity)
                    {
                        System.out.println("LEDTracker-run: Detection seen (id " + tmp.id+" @ "+tmp.xyz[0]+", "+tmp.xyz[1]+", "+tmp.xyz[2] + ")");
                        triangulatedLEDs.add(tmp);
                    }
                    detections.clear();
                }
            }
            
            if(display)
            {
                for(LEDDetection ld : triangulatedLEDs)
                {
                    vbLeds.addBuffered(new VisChain(LinAlg.translate(ld.xyz),
                            new VisCircle(0.1, new VisDataFillStyle(Color.black)), new VisSphere(0.01, Color.green)));
                }
                vbLeds.switchBuffer();
            }
        }
    }
    
    private void showGui(Camera cameras[])
    {
        if(display)
        {
            System.out.println("LEDTracker-showGUI: Tracks started. Starting display...");
            
            jf = new JFrame("LEDTracker v" + version);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setLayout(new BorderLayout());
            jf.add(vc, BorderLayout.CENTER);
            jf.setSize(1000, 500);
            jf.setVisible(true);
            
            for (Camera cam : cameras)
            {
                Color color = (cam.getIndex() == 0 ? Color.blue : Color.red);
                double[][] camM = cam.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
            }
            vbCameras.switchBuffer();
        }
    }
    
    private double[] matrixToXyz(double[][] matrix)
    {
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }
    
    private LEDDetection triangulate(ArrayList<LEDDetection> ld)
    {
        if(ld.size() < 2) return new LEDDetection(true);
        
        double transformations[][][] = new double[ld.size()][4][4];
        double theta;
        double phi; 
        
        double distance[] = new double[ld.size()];
        double lastDistance[] = new double[ld.size()];
        double delta[][];
        
        for(int x = 0; x < ld.size(); x++)
        {
            theta = -1*Math.atan((ld.get(x).uv[0]-cc[0])/fc[0]);
            phi = -1*Math.atan((ld.get(x).uv[1]-cc[1])/fc[1]);
            transformations[x] = LinAlg.matrixAB(ld.get(x).transformation, LinAlg.rotateY(theta));
            transformations[x] = LinAlg.matrixAB(transformations[x], LinAlg.rotateX(phi));
            
            ArrayList<double[]> ray = new ArrayList<double[]>();
            ray.add(LinAlg.matrixToXyzrpy(transformations[x]));
            ray.add(LinAlg.matrixToXyzrpy(LinAlg.matrixAB(transformations[x], LinAlg.translate(0,0,-100))));
            vbRays.addBuffered(new VisData(ray, new VisDataLineStyle(Color.green, 2)));

            distance[x] = LinAlg.distance(calculateAve(transformations), matrixToXyz(transformations[x]));
            lastDistance[x] = distance[x]+1;
        }
        
        boolean go = true;
     // stop when the 99% of detections are within 0.01m (2.5 stddev) or when all detections are ocillating 
        while(2.5*calculateStdDev(transformations) > 0.01 && go) 
        {
            go = false;
            
            System.out.println(calculateStdDev(transformations));
            for(int x = 0; x < ld.size(); x++)
            {
                delta = LinAlg.translate(0, 0, (distance[x] - lastDistance[x]));
                transformations[x] = LinAlg.matrixAB(transformations[x], delta);
            }
            
            for(int x = 0; x < ld.size(); x++)
            {
                double tmp = lastDistance[x];  
                lastDistance[x] = distance[x];
                distance[x] = LinAlg.distance(calculateAve(transformations), matrixToXyz(transformations[x]));
                
                if(tmp-lastDistance[x] != 0 && (lastDistance[x]-distance[x])/(tmp-lastDistance[x]) > 0)
                {
                   go = true; 
                }
            }
        }
        
        if(display)
        {
            for(int x = 0; x < ld.size(); x++)
            {
                vbRays.addBuffered(new VisChain(transformations[x], new VisSphere(0.01, Color.cyan)));
            }
            vbRays.switchBuffer();
        }
        
        return new LEDDetection(calculateAve(transformations), ld.get(0).id);
    }
    
    private double[] calculateAve(double[][][] transformations)
    {
        double average[] = new double[]{0,0,0};
        
        for(int x = 0; x < transformations.length; x++)
        {
            average = LinAlg.add(average, new double[]{transformations[x][0][3], transformations[x][1][3], transformations[x][2][3]});
        }
        
        return LinAlg.scale(average, 1.0/transformations.length);
    }
    
    private double calculateStdDev(double[][][] transformations)
    {
        double average[] = new double[]{0,0,0};
        double variance = 0;
        
        average = calculateAve(transformations);
        
        for(int x = 0; x < transformations.length; x++)
        {
            variance += LinAlg.squaredDistance(new double[]{transformations[x][0][3], 
                    transformations[x][1][3], transformations[x][2][3]},average);
        }
        
        return Math.sqrt(variance/transformations.length);
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
        Camera cameras[] = new Camera[2]; // TODO get this?
        cameras[0] = new Camera(0, "dc1394://b09d01008b51b8", new double[]{0.3811802199440573, -6.312776125748099E-4, 0.11233279316811129, -0.008475163475790602, 0.3174047182526466, -0.02277840164199716}); // 0
//        cameras[1] = new Camera("dc1394://b09d01008b51ab", new double[]{-0.70728, -0.04550, -0.32286, 0.01024, -0.79181, 0.00418}); // 12 (unmarked) 
        cameras[1] = new Camera(12, "dc1394://b09d01008e366c", new double[]{0,0,0,0,0,0}); // 12
        // XXX ***************************** FOR TESTING

        
        
        new LEDTracker(cameras, loRes, color16, fps, new double[]{477.5, 477.5}, new double[]{376,240}, new double[]{0,0,0,0,0}, 0.0, true); // XXX magic numbers
    }
    
    public void kill()
    {
        run = false;
    }

}
