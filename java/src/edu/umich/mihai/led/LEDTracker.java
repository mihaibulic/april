package edu.umich.mihai.led;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import lcm.lcm.LCM;
import april.config.Config;
import april.config.ConfigFile;
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
import edu.umich.mihai.camera.CamUtil;
import edu.umich.mihai.camera.CameraComparator;
import edu.umich.mihai.lcmtypes.led_t;
import edu.umich.mihai.sandbox.PointLocator;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.Util;
import edu.umich.mihai.vis.VisCamera;

/**
 * Tracks LEDs in 3d space given LEDDetections from multiple cameras and known extrinsic camera perameters
 * 
 * @author Mihai Bulic
 *
 */
public class LEDTracker extends CameraComparator implements Track.Listener
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbRays = vw.getBuffer("rays");
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbLeds = vw.getBuffer("leds");
    private boolean display;

    private Object lock = new Object();
    private boolean ledsReady = false;
    private int id;
    private ArrayList<LEDDetection> newLeds = new ArrayList<LEDDetection>();
    private ArrayList<ArrayList<LEDDetection> > leds = new ArrayList<ArrayList<LEDDetection> >();

    private LCM lcm = LCM.getSingleton();

    private boolean run = true;
    
    // TODO detect boundary tags and publish under boundary channel (use int[256][3])
    
    public LEDTracker(Config config, boolean display) throws ConfigException, CameraException, IOException 
    {
    	Util.verifyConfig(config);

        this.display = display;
    	
        ArrayList<String> urls = ImageSource.getCameraURLs();
        ArrayList<Track> tracks = new ArrayList<Track>();

        System.out.println("ICC-Constructor: starting imagereaders...");
        for(String url : urls)
        {
        	Track test = new Track(config.getChild(CamUtil.getUrl(config, url)), url);
        	if(test.isGood())
        	{
        		leds.add(new ArrayList<LEDDetection>());
        		test.addListener(this);
        		test.start();
        		tracks.add(test);
        	}
        }
        
        showGui(tracks);
        
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
                
                leds.get(id).clear();
                leds.get(id).addAll(newLeds);
            }
            
            for(int x = 0; x < leds.size(); x++)
            {
                for(int y = 0; y < leds.get(x).size(); y++)
                {
                    detections.clear();
                    detections.add(leds.get(x).get(y));

                    for(int a = x+1; a < leds.size(); a++)
                    {
                        for(int b = 0; b < leds.get(a).size(); b++)
                        {
                            if(leds.get(x).get(y).id == leds.get(a).get(b).id)
                            {
                                detections.add(leds.get(a).get(b));
                                leds.get(a).remove(b);
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
                            // TODO add granularity for what is displayed (only LEDs, certainty bubble, rays, etc.)
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
    
    private void showGui(ArrayList<Track> tracks)
    {
        if(display)
        {
            System.out.println("LEDTracker-showGUI: Tracks started. Starting display...");
            
            jf = new JFrame("LEDTracker");
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setLayout(new BorderLayout());
            jf.add(vc, BorderLayout.CENTER);
            jf.setSize(1000, 500);
            jf.setVisible(true);
            
            for (Track track : tracks)
            {
                Color color = (track.getIndex() == 0 ? Color.blue : Color.red);
                double[][] camM = track.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
            }
            vbCameras.switchBuffer();
        }
    }
    
    private double[] matrixToXyz(double[][] matrix)
    {
        return new double[]{matrix[0][3], matrix[1][3], matrix[2][3]};
    }
    
    // FIXME make more statistically rigerous
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
        	LEDDetection led = ld.get(x);
            theta = -1*Math.atan((led.uv[0]-led.cc[0])/led.fc[0]);
            phi = -1*Math.atan((led.uv[1]-led.cc[1])/led.fc[1]);
            transformations[x] = LinAlg.matrixAB(led.transformation, LinAlg.rotateY(theta));
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
                    distance[x] = LinAlg.distance(PointLocator.calculateItt(transformations), matrixToXyz(transformations[x]));
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
        
        return new LEDDetection(PointLocator.calculateItt(locations), ld.get(0).id);
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

//    private double[] calculateAve(double[][][] transformations)
//    {
//        double average[] = new double[]{0,0,0};
//        
//        for(int x = 0; x < transformations.length; x++)
//        {
//            average = LinAlg.add(average, new double[]{transformations[x][0][3], transformations[x][1][3], transformations[x][2][3]});
//        }
//        
//        return LinAlg.scale(average, 1.0/transformations.length);
//    }
    
    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "config", System.getenv("CONFIG")+"/camera.config", "Location of config file to use for settings");

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

        new LEDTracker(new ConfigFile(opts.getString("config")), true); 
    }
    
    public void kill()
    {
        run = false;
    }

    
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
