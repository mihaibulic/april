package edu.umich.mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.JFrame;
import edu.umich.mihai.lcmtypes.led_t;
import lcm.lcm.LCM;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisData;
import april.vis.VisDataFillStyle;
import april.vis.VisDataLineStyle;
import april.vis.VisSphere;
import april.vis.VisWorld;

public class LEDTracker extends CameraComparator implements Track.Listener
{
    private final double version = 0.1;
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbRays = vw.getBuffer("rays");
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbLeds = vw.getBuffer("leds");
    private boolean display;

    private double[] cc;
    private double[] fc;

    private Object lock = new Object();
    private boolean ledsReady = false;
    private int id;
    private ArrayList<LEDDetection> newLeds;
    private ArrayList<LEDDetection> leds[];

    private LCM lcm = LCM.getSingleton();

    private boolean run = true;
    
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
        leds = new ArrayList[cameras.length];
        newLeds = new ArrayList<LEDDetection>();
        
        System.out.println("LEDTracker-Constructor: Starting tracks...");
        for (int x = 0; x < tracks.length; x++)
        {
            leds[x] = new ArrayList<LEDDetection>();
            
            try
            {
               tracks[x] = new Track(x, cameras[x].getUrl(), cameras[x].getTransformationMatrix(), loRes, color16, fps, fc, cc, kc, alpha);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
            
            tracks[x].addListener(this);
            tracks[x].start();
        }
        
        showGui(cameras);
        
        run();
    }
    
    public void run()
    {
        ArrayList<LEDDetection> detections = new ArrayList<LEDDetection>();
        
        if(display) System.out.println("LEDTracker-run: Display started. Tracking LEDs...");
        else        System.out.println("LEDTracker-run: Tracks started. Tracking LEDs...");
        
        while(run)
        {
            synchronized(lock)
            {
                try
                {
                    while(!ledsReady)
                    {
                        lock.wait();
                    }
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
                
                leds[id].clear();
                leds[id].addAll(newLeds);
            }
            
            for(int x = 0; x < leds.length; x++)
            {
                for(int y = 0; y < leds[x].size(); y++)
                {
                    detections.clear();
                    detections.add(leds[x].get(y));

                    for(int a = x+1; a < leds.length; a++)
                    {
                        for(int b = 0; b < leds[a].size(); b++)
                        {
                            if(leds[x].get(y).id == leds[a].get(b).id)
                            {
                                detections.add(leds[a].get(b));
                                leds[a].remove(b);
                                break;
                            }
                        }
                    }
                    
                    LEDDetection tmp = triangulate(syncDetections(detections));
                    if(!tmp.singularity)
                    {
                        System.out.println("LEDTracker-run: Detection seen (id " + tmp.id+" @ "+tmp.xyz[0]+", "+tmp.xyz[1]+", "+tmp.xyz[2] + ")");
                        
                        led_t led = new led_t();
                        led.id = tmp.id;
                        led.xyz = tmp.xyz;
                        
                        lcm.publish("led"+tmp.id, led);
                        
                        if(display)
                        {
                            vbLeds.addBuffered(new VisChain(LinAlg.translate(tmp.xyz),
                                new VisCircle(0.1, new VisDataFillStyle(Color.black)), new VisSphere(0.01, Color.green)));
                        }
                    }
                }
            }
            
            if(display)
            {
                vbLeds.switchBuffer();
            }
            
            ledsReady = false;
        }
    }
    
    private ArrayList<LEDDetection> syncDetections(ArrayList<LEDDetection> unsynced)
    {
        ArrayList<LEDDetection> synced = new ArrayList<LEDDetection>();
        double mostRecent = Double.MIN_VALUE;
        
        for(LEDDetection led : unsynced)
        {
            if(led.timeStamp > mostRecent)
            {
                mostRecent = led.timeStamp;
            }
        }
        
        for(LEDDetection led : unsynced)
        {
            if(led.timeStamp + (0.075) >= mostRecent) 
            {
                synced.add(led);
            }
        }
        
        return synced;
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

        double theta;
        double phi; 
        
        double transformations[][][] = new double[ld.size()][4][4];
        double locations[][] = new double[ld.size()][3];
        
        double distance[] = new double[ld.size()];
        double lastDistance[] = new double[ld.size()];
        double auxDelta[][];
        
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
        }
        
        for(int z = 0; z < ld.size(); z++)
        {
            boolean mainGo = true;
            double mainDistance = ave(transformations, transformations[z]);
            double oldDistance = 1 + mainDistance;
            while(mainGo)
            {
                mainGo = false;
                
                double[][] mainDelta = LinAlg.translate(0, 0, ((mainDistance - oldDistance)>0 ? 0.001 : -0.001));
                transformations[z] = LinAlg.matrixAB(transformations[z], mainDelta);
                
                double temp = oldDistance;
                oldDistance = mainDistance;
                mainDistance = ave(transformations, transformations[z]);
                    
                if(temp-oldDistance != 0 && (oldDistance-mainDistance)/(temp-oldDistance) > 0)
                {
                    mainGo = true; 
                }
                
                for(int x = 0; x < ld.size(); x++)
                {
                    boolean auxGo = true;
                    distance[x] = LinAlg.distance(calculateAve(transformations), matrixToXyz(transformations[x]));
                    lastDistance[x] = distance[x]+1;
                    while(x != z && auxGo)
                    {
                        auxGo = false;

                        auxDelta = LinAlg.translate(0, 0, ((distance[x] - lastDistance[x])>0 ? 0.001 : -0.001));
                        transformations[x] = LinAlg.matrixAB(transformations[x], auxDelta);

                        double tmp = lastDistance[x];  
                        lastDistance[x] = distance[x];
                        distance[x] = LinAlg.distance(matrixToXyz(transformations[z]), matrixToXyz(transformations[x]));
                        
                        if(tmp-lastDistance[x] != 0 && (lastDistance[x]-distance[x])/(tmp-lastDistance[x]) > 0)
                        {
                           auxGo = true; 
                        }
                        
                    }
                }
                
            }
            
            locations[z] = matrixToXyz(transformations[z]);
        }
        
        if(display)
        {
            for(int x = 0; x < ld.size(); x++)
            {
                vbRays.addBuffered(new VisChain(transformations[x], new VisSphere(0.01, Color.cyan)));
            }
            vbRays.switchBuffer();
        }
        
        return new LEDDetection(calculateAve(locations), ld.get(0).id);
    }
    
    private double ave(double[][][] transformations, double[][] main)
    {
        double distance = 0;
        
        for(int x = 0; x < transformations.length; x++)
        {
            distance += LinAlg.distance(new double[]{main[0][3], main[1][3], main[2][3]},  
                    new double[]{transformations[x][0][3], transformations[x][1][3], transformations[x][2][3]});
        }
        
        return distance/transformations.length;
    }

    private double[] calculateAve(double[][] locs)
    {
        double average[] = new double[]{0,0,0};
        
        for(int x = 0; x < locs.length; x++)
        {
            average = LinAlg.add(average, locs[x]);
        }
        
        return LinAlg.scale(average, 1.0/locs.length);
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

        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        boolean loRes = opts.getString("resolution").contains("lo");
        boolean color16 = opts.getString("colors").contains("16");
        int fps = opts.getInt("fps");

/**
 * camera: 0
(x,y,z): 0.06450273846631908, -0.25507166312989354, -0.020171972628530654
(r,p,y): -0.3129196070369205, 0.1750399043671647, -2.660501114757434

 */
        
        
        // XXX ***************************** FOR TESTING
        Camera cameras[] = new Camera[2]; // TODO get this?
        cameras[0] = new Camera(0,"dc1394://b09d01008b51b8", new double[]{0.06450273846631908, -0.25507166312989354, -0.020171972628530654, -0.3129196070369205, 0.1750399043671647, -2.660501114757434}); // 0
        cameras[1] = new Camera(1,"dc1394://b09d01008b51ab", new double[]{0,0,0,0,0,0}); // 1
        // XXX ***************************** FOR TESTING

        
     // XXX magic numbers
        new LEDTracker(cameras, loRes, color16, fps, new double[]{477.5, 477.5}, new double[]{376,240}, new double[]{0,0,0,0,0}, 0.0, true); 
    }
    
    public void kill()
    {
        run = false;
    }

    @Override
    public void handleDetections(ArrayList<LEDDetection> leds, int id)
    {
        synchronized(lock)
        {
            newLeds.clear();
            newLeds.addAll(leds);
            this.id = id;
            ledsReady = true;
            lock.notify();
        }        
    }
}
