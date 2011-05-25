package mihai.tracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import lcm.lcm.LCM;
import mihai.lcmtypes.object_t;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import mihai.vis.VisCamera;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.util.GetOpt;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisData;
import april.vis.VisDataFillStyle;
import april.vis.VisDataLineStyle;
import april.vis.VisSphere;
import april.vis.VisWorld;

/**
 * Tracks objects in 3d space given object detections from multiple cameras and known extrinsic camera parameters
 * 
 * @author Mihai Bulic
 *
 */
public class ObjectTracker extends JFrame implements Track.Listener
{
    private static final long serialVersionUID = 1L;
    private boolean run = true;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbObjects = vw.getBuffer("objects");
    private VisWorld.Buffer vbRays = vw.getBuffer("rays");
    
    private boolean display;
    private boolean verbose;
    
    private Object lock = new Object();
    private boolean newObjects = false;
    private ArrayList<Track> tracks;
    private ObjectManager objectManager;
    
    private LCM lcm = LCM.getSingleton();

    private Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
    
    
    // TODO detect boundary tags and publish under boundary channel (use int[256][3])
    
    public ObjectTracker(Config config, boolean display, boolean verbose) throws ConfigException, CameraException, IOException 
    {
        super("Object Tracker");
        
    	Util.verifyConfig(config);

        this.display = display;
        this.verbose = verbose; 
        
        if(verbose) System.out.print("ObjectTracker-Constructor: starting imagereaders...");
        objectManager = new ObjectManager();
        ArrayList<String> urls = ImageSource.getCameraURLs();
        tracks = new ArrayList<Track>();
        for(String url : urls)
        {
        	Track test = new Track(config.getChild(Util.getSubUrl(config, url)), url);
        	if(test.isGood())
        	{
        		test.addListener(this);
        		test.start();
        		tracks.add(test);
        	}
        }
        if(verbose) System.out.println("done");

        if(display) showGui(tracks);
        
        run();
    }
    
    private void showGui(ArrayList<Track> tracks)
    {
        if(verbose) System.out.println("ObjectTracker-showGUI: Tracks started. Starting display...");
        
        add(vc, BorderLayout.CENTER);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        
        for(int x = 0; x < tracks.size(); x++)
        {
            double[][] camM = tracks.get(x).getTransformationMatrix();
            vbCameras.addBuffered(new VisChain(camM, new VisCamera(colors[x], 0.08)));
        }
        vbCameras.switchBuffer();

        setVisible(true);
    }
    
    public void run()
    {
        if(display && verbose) System.out.println("ObjectTracker-run: Display started. Tracking objects...");
        else if(verbose) System.out.println("ObjectTracker-run: Tracks started. Tracking objects...");
        while(run)
        {
            synchronized(lock)
            {
                while(!newObjects)
                {
                    try
                    {
                        lock.wait();
                    } catch(InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                newObjects = false;
            
	            for(int id : objectManager.getIds())
	            {
                    SpaceObjectDetection found = triangulate(syncDetections(objectManager.getObjects(id)));
                    if(!found.singularity)
                    {
                        if(verbose)
                        {
                            System.out.println("ObjectTracker-run: Detection seen (id " + found.id+" @ " +
                                    found.xyzrpy[0]+", "+found.xyzrpy[1]+", "+found.xyzrpy[2] + ", " + 
                                    found.xyzrpy[3]+", "+found.xyzrpy[4]+", "+found.xyzrpy[5] + ")");
                        }
                        
                        object_t object = new object_t();
                        object.id = found.id;
                        object.utime = (long) found.timeStamp;
                        object.xyzrpy = found.xyzrpy;
                        object.transformation = found.transformation;
                        
                        lcm.publish("object"+found.id, object);
                        
                        if(display)
                        {
                            // TODO add granularity for what is displayed (only objects, certainty bubble, rays, etc.)
                            vbObjects.addBuffered(new VisChain(LinAlg.translate(found.xyzrpy),
                                new VisCircle(0.1, new VisDataFillStyle(Color.black)), new VisSphere(0.01, Color.green)));
                            vbObjects.switchBuffer();
                        }
                        objectManager.clearObjects(id);
                    }
	            }
	            if(display)
	            {
	            }
	            
	        }
        }
    }

    // FIXME sync up images
    private ArrayList<ImageObjectDetection> syncDetections(ArrayList<ImageObjectDetection> unsynced)
    {
//    private ArrayList<ImageObjectDetection> syncDetections(ArrayList<ImageObjectDetection> unsynced)
//    {
//        ArrayList<ImageObjectDetection> synced = new ArrayList<ImageObjectDetection>();
//        double mostRecent = Double.MIN_VALUE;
//        
//        for(ImageObjectDetection object : unsynced)
//        {
//            if(object.timeStamp > mostRecent)
//            {
//                mostRecent = object.timeStamp;
//            }
//        }
//        
//        for(ImageObjectDetection object : unsynced)
//        {
//            if(object.timeStamp + (0.075) >= mostRecent) 
//            {
//                synced.add(object);
//            }
//        }
//        
//        return synced;
//    }
        return unsynced;
    }
    
    private SpaceObjectDetection triangulate(ArrayList<ImageObjectDetection> objectDetections)
    {
        if(objectDetections.size() < 3) return new SpaceObjectDetection(true);
        
        int length = 6;
        double threshold = 0.00001;
        int ittLimit = 50;

        double location[] = {-1,-1,-3,0,0,0}; // XXX better init
        
        Distance distance = new Distance(objectDetections);
        
        double[] eps = new double[length];
        for(int i = 0; i < eps.length; i++)
        {
            eps[i] = 0.0001;
        }
        
        int count = 0;
        double[] r = LinAlg.scale(distance.evaluate(location), -1);
        double[] oldR = null;
        while(!shouldStop(r, oldR, threshold) && ++count < ittLimit)
        {
            double[][] _J = NumericalJacobian.computeJacobian(distance, location, eps);
            Matrix J = new Matrix(_J);
            
            Matrix JTtimesJplusI = J.transpose().times(J).plus(Matrix.identity(length, length));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTtimesJplusI.solve(JTr);
            
            for(int i = 0; i < length; i++)
            {
                location[i] += 0.1*dx.get(i,0); // scaling by 0.1 helps keep results stable 
            }
            
            oldR = r.clone();
            r = LinAlg.scale(distance.evaluate(location), -1);
        }
        
        // FIXME (timeStamp should be an average or something)
        return new SpaceObjectDetection(objectDetections.get(0).objectID, objectDetections.get(0).timeStamp, location);
    }
    
    class Distance extends Function
    {
        ArrayList<ImageObjectDetection> objects;
        int length = 1;
        
        public Distance(ArrayList<ImageObjectDetection> objects)
        {
            this.objects = objects;
            
            for(ImageObjectDetection object : objects)
            {
                double theta = -1*Math.atan((object.uv[0]-object.cc[0])/object.fc[0]);
                double phi = -1*Math.atan((object.uv[1]-object.cc[1])/object.fc[1]);
                double[][] M = LinAlg.matrixAB(object.cameraTransformation, LinAlg.rotateY(theta));
                M = LinAlg.matrixAB(M, LinAlg.rotateX(phi));
                
                ArrayList<double[]> ray = new ArrayList<double[]>();
                ray.add(LinAlg.matrixToXyzrpy(M));
                ray.add(LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(0,0,-5))));
                vbRays.addBuffered(new VisData(ray, new VisDataLineStyle(Color.green, 2)));            	
            }
            vbRays.switchBuffer();
        }
        
        public double[] evaluate(double[] point)
        {
            return evaluate(point, null);
        }
        
        public double[] evaluate(double[] point, double[] distances)
        {
            if(distances == null)
            {
                distances = new double[objects.size()*length];
            }

            for(int a = 0; a < objects.size(); a++)
            {
                ImageObjectDetection object = objects.get(a);

                double theta = -1*Math.atan((object.uv[0]-object.cc[0])/object.fc[0]);
                double phi = -1*Math.atan((object.uv[1]-object.cc[1])/object.fc[1]);
                double[][] M = LinAlg.matrixAB(LinAlg.matrixAB(object.cameraTransformation, LinAlg.rotateY(theta)), LinAlg.rotateX(phi));
                double[] rayEndPoint = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(new double[]{0,0,100})));
                double[] rayStartPoint = LinAlg.matrixToXyzrpy(M);
                double[] pointLine1 = LinAlg.copy(LinAlg.subtract(point, rayStartPoint), 3);
                double[] pointLine2 = LinAlg.copy(LinAlg.subtract(point, rayEndPoint),3);
                double[] rayVector = LinAlg.copy(LinAlg.subtract(rayEndPoint, rayStartPoint),3);
                distances[a*length] = LinAlg.magnitude(LinAlg.crossProduct(pointLine1, pointLine2))/LinAlg.magnitude(rayVector);
                
//                double[] v = LinAlg.copy(LinAlg.subtract(rayPoint, startPoint),3);
//                double[] w = LinAlg.copy(LinAlg.subtract(point, startPoint),3);
//                double z =  LinAlg.dotProduct(w,v) / LinAlg.dotProduct(v,v);
                    
//                double[] endPoint = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(new double[]{0, 0, z})));

//                double pointToRayDistance = LinAlg.distance(endPoint, point,3);
//                double pxToProjPt = Math.sqrt(sq(object.fc[0])+sq(object.uv[0]));
//                double pixelSize = z*pxToProjPt*((1/object.uv[0])-(1/(object.uv[0]+1))); 
//                double slope =   
//                double distance = pointToRayDistance - Math.sqrt(sq(pixelSize) + sq(pixelSize/slope))/2;
            }
            
            return distances;
        }

//        /**
//         * @param c - center point (xyz of camera)
//         * @param p0 - one of the two corners of the pixels 
//         * @param p1 - one of the two corners of the pixels 
//         * @param q - point in question 
//         * @return
//         */
//        private boolean isBelow(double[] c, double[] p0, double[] p1, double[] q)
//        {
//            // calculates vector from c to p0, and c to p1
//            // then calculates the cross product of those vectors (which is the normal vector of the plane formed by c, p0, and p1
//            // if the dot product of the vector from c to p and the normal vector is > 0, the angle between them is < 90 degrees
//            // which means they are on the same side.
//            
//            return LinAlg.dotProduct(LinAlg.crossProduct(
//                                        LinAlg.subtract(p0, c), LinAlg.subtract(p1, c)), 
//                                     LinAlg.subtract(q, c)) > 0;
//        }
//        
//        private double sq(double a)
//        {
//            return a*a;
//        }
        
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        boolean stop = true;
        
        if(oldR != null)
        {
            for(int x = 0; x < r.length; x++)
            {
                if(Math.abs(oldR[x] - r[x]) > threshold)
                {
                    stop = false;
                    break;
                }
            }
        }
        else
        {
            stop = false;
        }
        
        return stop;
    }
    
    /**
     * @param args
     * @throws Exception 
     */
    public static void main(String[] args) throws Exception
    {
    	GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) ");
        opts.addBoolean('d', "display", true, "if true will display a GUI with the camera and tag locations");
        opts.addBoolean('v', "verbose", true, "if true will print out more information regarding calibrator's status");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: Track objects in 3D space.");
            opts.doHelp();
            System.exit(1);
        }
        
        Config config = new ConfigFile(opts.getString("config"));
        if(config == null) throw new ConfigException(ConfigException.NULL_CONFIG);
    	if(!opts.getString("resolution").isEmpty())
    	{
    		config.setBoolean("loRes", opts.getString("resolution").contains("lo"));
    	}
    	if(!opts.getString("colors").isEmpty())
    	{
    		config.setBoolean("color16", opts.getString("colors").contains("16"));
    	}
    	if(!opts.getString("fps").isEmpty())
    	{
    		config.setInt("fps", Integer.parseInt(opts.getString("fps")));
    	}

        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        new ObjectTracker(config, opts.getBoolean("display"), opts.getBoolean("verbose")); 
    }
    
    public void kill()
    {
        run = false;
    }
    
    public void handleDetections(ArrayList<ImageObjectDetection> objects, double[][] transformation)
    {
        synchronized(lock)
        {
        	objectManager.addObjects(objects);
            newObjects = true;
            lock.notify();
        }        
    }
}
