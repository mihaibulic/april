package mihai.tracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JFrame;
import lcm.lcm.LCM;
import mihai.camera.CamUtil;
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
import april.vis.VisDataFillStyle;
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
    private boolean display;
    private boolean verbose;
    
    private Object lock = new Object();
    private boolean newObjects = false;
    private ArrayList< ArrayList<ImageObjectDetection> > objectsL = new ArrayList<ArrayList<ImageObjectDetection> >();
    private ArrayList< HashMap<Integer, ImageObjectDetection> > objectsH = new ArrayList< HashMap<Integer, ImageObjectDetection> > ();
    
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
        ArrayList<String> urls = ImageSource.getCameraURLs();
        ArrayList<Track> tracks = new ArrayList<Track>();
        for(String url : urls)
        {
        	Track test = new Track(config.getChild(CamUtil.getUrl(config, url)), url);
        	if(test.isGood())
        	{
        		objectsL.add(new ArrayList<ImageObjectDetection>());
        		test.addListener(this);
        		test.start();
        	}
        }
        if(verbose) System.out.println("done");

        if(display) showGui(tracks);
        
        run();
    }
    
    private void showGui(ArrayList<Track> tracks)
    {
        System.out.println("ObjectTracker-showGUI: Tracks started. Starting display...");
        
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
        ArrayList<ImageObjectDetection> detections = new ArrayList<ImageObjectDetection>();
        int size;

        synchronized(lock)
        {
            size = objectsL.size();
        }
        
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
            }
            
            for(int x = 0; x < size; x++)
            {
                int detectionCount;
                synchronized(lock)
                {
                  detectionCount = objectsL.get(x).size();
                }
                
                for(int y = 0; y < detectionCount; y++)
                {
                    ImageObjectDetection find;
                    
                    synchronized(lock)
                    {
                      find = objectsL.get(x).get(y);
                    }
                    
                    detections.clear();
                    detections.add(find);
                    
                    for(int z = 0; z < size; z++)
                    {
                        if(x!=z)
                        {
                            ImageObjectDetection object;
                            synchronized(lock)
                            {
                              object = objectsH.get(z).get(find.id);
                            }
                            
                            if(object != null)
                            {
                                detections.add(object);
                            }
                        }
                    }
                    
                    SpaceObjectDetection found = triangulate(syncDetections(detections));
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
                        }
                    }
                }
            }
            
            if(display)
            {
                vbObjects.switchBuffer();
            }
            
        }
    }

    // FIXME sync up images
    private ArrayList<ImageObjectDetection> syncDetections(ArrayList<ImageObjectDetection> unsynced)
    {
        return unsynced;
    }
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
    
    class Distance extends Function
    {
        ArrayList<ImageObjectDetection> objects;
        int length = 1;
        
        public Distance(ArrayList<ImageObjectDetection> objects)
        {
            this.objects = objects;
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
                for(int x = 0; x < distances.length; x++)
                {
                    distances[x] = 0;
                }
            }

            for(int a = 0; a < objects.size(); a++)
            {
                ImageObjectDetection object = objects.get(a);

                double theta = -1*Math.atan((object.uv[0]-object.cc[0])/object.fc[0]);
                double phi = -1*Math.atan((object.uv[1]-object.cc[1])/object.fc[1]);
                double[][] M = LinAlg.matrixAB(LinAlg.matrixAB(object.transformation, LinAlg.rotateY(theta)), LinAlg.rotateX(phi));
                double[] rayEndPoint = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(new double[]{0,0,100})));
                double[] rayStartPoint = LinAlg.matrixToXyzrpy(M);
                System.out.println(theta  + "\t..." + phi);
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
        return (oldR != null && LinAlg.distance(r,oldR, 3) < threshold);
    }
    
    private SpaceObjectDetection triangulate(ArrayList<ImageObjectDetection> objectDetections)
    {
        if(objectDetections.size() < 2) return new SpaceObjectDetection(true);
        
        int length = 6;
        double threshold = 0.000001;
        int ittLimit = 100;

        double location[] = {0,0,0,0,0,0};// = calculateCentroid(points);
        
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
            System.out.println(r[0] + "\t" + r[1] + "\t" + r[2] + "\t" + r[3]);
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
        
        System.out.println(count);
        
        // FIXME (timeStamp should be an average or something)
        return new SpaceObjectDetection(objectDetections.get(0).id, objectDetections.get(0).timeStamp, location);
    }
    
//    // FIXME make more statistically rigorous
//    private SpaceObjectDetection triangulate(ArrayList<ImageObjectDetection> objectDetections)
//    {
//        if(objectDetections.size() < 2) return new SpaceObjectDetection(true);
//
//        double theta;
//        double phi;
//        
//        double transformations[][][] = new double[objectDetections.size()][4][4];
//        double locations[][] = new double[objectDetections.size()][3];
//        
//        double distance[] = new double[objectDetections.size()];
//        double lastDistance[] = new double[objectDetections.size()];
//        double auxDelta[][];
//        
//        // create rays to walk for each object
//        for(int x = 0; x < objectDetections.size(); x++)
//        {
//        	ImageObjectDetection object = objectDetections.get(x);
//            theta = -1*Math.atan((object.uv[0]-object.cc[0])/object.fc[0]);
//            phi = -1*Math.atan((object.uv[1]-object.cc[1])/object.fc[1]);
//            transformations[x] = LinAlg.matrixAB(object.transformation, LinAlg.rotateY(theta));
//            transformations[x] = LinAlg.matrixAB(transformations[x], LinAlg.rotateX(phi));
//            
//            ArrayList<double[][]> ray = new ArrayList<double[][]>();
//            ray.add(transformations[x]);
//     
//            if(display) vbRays.addBuffered(new VisData(ray, new VisDataLineStyle(Color.green, 2)));
//        }
//        
//        
//        
//        
//        
//        for(int z = 0; z < objectDetections.size(); z++)
//        {
//            boolean mainGo = true;
//            double mainDistance = ave(transformations, transformations[z]);
//            double oldDistance = 1 + mainDistance;
//            while(mainGo)
//            {
//                mainGo = false;
//                
//                double[][] mainDelta = LinAlg.translate(0, 0, ((mainDistance - oldDistance)>0 ? 0.001 : -0.001));
//                transformations[z] = LinAlg.matrixAB(transformations[z], mainDelta);
//                
//                double temp = oldDistance;
//                oldDistance = mainDistance;
//                mainDistance = ave(transformations, transformations[z]);
//                    
//                if(temp-oldDistance != 0 && (oldDistance-mainDistance)/(temp-oldDistance) > 0)
//                {
//                    mainGo = true;
//                }
//                
//                for(int x = 0; x < objectDetections.size(); x++)
//                {
//                    boolean auxGo = true;
//                    distance[x] = LinAlg.distance(PointLocator.calculateItt(transformations), matrixToXyz(transformations[x]));
//                    lastDistance[x] = distance[x]+1;
//                    while(x != z && auxGo)
//                    {
//                        auxGo = false;
//
//                        auxDelta = LinAlg.translate(0, 0, ((distance[x] - lastDistance[x])>0 ? 0.001 : -0.001));
//                        transformations[x] = LinAlg.matrixAB(transformations[x], auxDelta);
//
//                        double tmp = lastDistance[x];  
//                        lastDistance[x] = distance[x];
//                        distance[x] = LinAlg.distance(matrixToXyz(transformations[z]), matrixToXyz(transformations[x]));
//                        
//                        if(tmp-lastDistance[x] != 0 && (lastDistance[x]-distance[x])/(tmp-lastDistance[x]) > 0)
//                        {
//                           auxGo = true; 
//                        }
//                        
//                    }
//                }
//                
//            }
//            
//            locations[z] = matrixToXyz(transformations[z]);
//        }
//        
//        if(display)
//        {
//            for(int x = 0; x < objectDetections.size(); x++)
//            {
//                vbRays.addBuffered(new VisChain(transformations[x], new VisSphere(0.01, Color.cyan)));
//            }
//            vbRays.switchBuffer();
//        }
//        
//        return new SpaceObjectDetection(objectDetections.get(0).id, PointLocator.calculateItt(locations));
//    }
//    
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
    
    public void handleDetections(ArrayList<ImageObjectDetection> newObjectsL, HashMap<Integer,ImageObjectDetection> newObjectsH, int id)
    {
        synchronized(lock)
        {
            objectsL.set(id, newObjectsL);
            objectsH.set(id, newObjectsH);
            
            newObjects = true;
            lock.notify();
        }        
    }
}
