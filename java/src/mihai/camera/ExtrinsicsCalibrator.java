package mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import javax.swing.JFrame;
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
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

/**
 * Gives camera to camera coordinates given that tags are spread out in the view of multiple cameras
 * 
 * @author Mihai Bulic
 *
 */
public class ExtrinsicsCalibrator extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbTags = vw.getBuffer("tags");
    private Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};

    private double tagSize;
    
    private ArrayList <Camera> cameras;
    
    public ExtrinsicsCalibrator(Config config) throws ConfigException, CameraException, IOException, InterruptedException
    {
        this(config, false, false);
    }
    
    public ExtrinsicsCalibrator(Config config, boolean display, boolean verbose) throws ConfigException, CameraException, IOException, InterruptedException
    {
        super("Extrinsics Calibrator");
        
        Util.verifyConfig(config);
        
    	tagSize = config.requireDouble("tagSize");
        ArrayList<String> urls = ImageSource.getCameraURLs();

        if(verbose) System.out.print("ICC-Constructor: starting imagereaders...");
        cameras = new ArrayList<Camera>();
        for(String url : urls)
        {
            Camera test = new Camera(config.getChild(CamUtil.getUrl(config, url)), url);
            if(test.isGood())
            {
                cameras.add(test);
            }
        }
        if(verbose) System.out.println("done");
        
        if(verbose) System.out.println("ICC-run: Aggregating tags...");
        for (Camera camera : cameras)
        {
            if(verbose) System.out.print("ICC-run: aggregating tags of camera " + camera.getCameraId() + "...");
            camera.aggregateTags(5, tagSize);
            if(verbose) System.out.println("done (found " + camera.getTagCount() + " tags)");
        }

        if(verbose) System.out.print("ICC-run: Resolving initial extrinsics solution...");
        Collections.sort(cameras, new CameraComparator());
        getAllCorrespondences();
        initializeExtrinsics();
        if(verbose) System.out.println("done");
        
        if(verbose) System.out.print("ICC-run: Resolving itterative extrinsics solution...");
        calculateItt();
        if(verbose) System.out.println("done");
        
        if(display || verbose)
        {
            for(int x = 0; x < cameras.size(); x++)
            {
                Camera cam = cameras.get(x);
                
                if(verbose)
                {
                    double[] pos = cam.getXyzrpy();
                    System.out.println("camera: " + cam.getCameraId());
                    System.out.println("(x,y,z): " + pos[0] + ", " + pos[1] + ", " + pos[2]);
                    System.out.println("(r,p,y): " + pos[3] + ", " + pos[4] + ", " + pos[5] + "\n\n");
                }
         
                if(display)
                {
                    double[][] camM = cam.getTransformationMatrix();
                    vbCameras.addBuffered(new VisChain(camM, new VisCamera(colors[x], 0.08)));
                    
                    for (Tag tag : cam.getTags())   
                    {
                        double tagM[][] = tag.getTransformationMatrix();
                        vbTags.addBuffered(new VisChain(camM, tagM, new VisRectangle(tagSize, tagSize, 
                                new VisDataLineStyle(colors[x], 2))));
                    }
                }
            }
            
            if(display)
            {
                showGui();
            }
        }
    }

    private void showGui()
    {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        add(vc, BorderLayout.CENTER);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
    	
        vbTags.switchBuffer();
        vbCameras.switchBuffer();
        setVisible(true);
    }

    public static void main(String[] args) throws IOException, ConfigException, CameraException, InterruptedException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) ");
        opts.addString('t', "tagSize", "", "size of tags used in meters (overrides config framerate)");
        opts.addBoolean('d', "display", true, "if true will display a GUI with the camera and tag locations");
        opts.addBoolean('v', "verbose", true, "if true will print out more information regarding calibrator's status");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: Calibrate relative positions of multiple cameras.");
            opts.doHelp();
            System.exit(1);
        }
        
        Config config = new ConfigFile(opts.getString("config"));
        Util.verifyConfig(config);
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
    	if(!opts.getString("tagSize").isEmpty())
    	{
    		config.setDouble("tagSize", Double.parseDouble(opts.getString("tagSize")));
    	}

        if (ImageSource.getCameraURLs().size() == 0) 
        {
            throw new CameraException(CameraException.NO_CAMERA);
        }

        new ExtrinsicsCalibrator(config, opts.getBoolean("display"), opts.getBoolean("verbose"));
    }

    private void getAllCorrespondences() throws CameraException
    {
    	if(cameras.get(0).getTagCount() == 0)
        {
    	    throw new CameraException(CameraException.NO_TAGS);
	    }
    	
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            boolean found = false;
            if (cameras.get(cam).getTagCount() == 0) 
            {
                throw new CameraException(CameraException.NO_TAGS);
            }

            for (int main = 0; main < cameras.size() && !found; main++)
            {
                if (main != cam)
                {
                    getCorrespondence(main, cameras.get(main), cameras.get(cam));
                    found = cameras.get(cam).isCertain();
                    
                    if(found) 
                    {
                        break;
                    }
                }
            }
            
            if (!found)
            {
                throw new CameraException(CameraException.UNCERTAIN);
            }
        }
    }

    private void getCorrespondence(int main, Camera mainCam, Camera auxCam)
    {
        int mainIndex = 0;
        int auxIndex = 0;
        double mainM[][];
        double auxM[][];
        ArrayList<Tag> mainTags = mainCam.getTags();
        ArrayList<Tag> auxTags = auxCam.getTags();
        
        auxCam.setMain(main);
        auxCam.clearPotentialPositions();

        while (mainIndex < mainTags.size() && auxIndex < auxTags.size())
        {
            if (auxTags.get(auxIndex).getId() == mainTags.get(mainIndex).getId())
            {
                mainM = mainTags.get(mainIndex).getTransformationMatrix();
                auxM = auxTags.get(auxIndex).getTransformationMatrix();
                
                auxCam.addCorrespondence(LinAlg.matrixAB(mainM, LinAlg.inverse(auxM)));
                mainIndex++;
                auxIndex++;
            }
            else if (auxTags.get(auxIndex).getId() > mainTags.get(mainIndex).getId())
            {
                mainIndex++;
            }
            else
            {
                auxIndex++;
            }
        }
    }

    private void initializeExtrinsics() throws CameraException
    {
        cameras.get(0).setPosition(new double[] { 0, 0, 0, 0, 0, 0 }, 0);
        
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            cameras.get(cam).setPosition();
            while (cameras.get(cam).getMain() != 0)
            {
                if (cameras.get(cam).getMain() == cam) throw new CameraException(CameraException.CYCLE);

                double[][] pos = cameras.get(cam).getTransformationMatrix();
                double[][] posToOldMain = cameras.get(cameras.get(cam).getMain()).getTransformationMatrix();
                double[][] posToNewMain = LinAlg.matrixAB(pos, posToOldMain);

                cameras.get(cam).setPosition(posToNewMain, cameras.get(cameras.get(cam).getMain()).getMain());
            }
        }
    }
    
    private void calculateItt()
    {
        double threshold = 0.00000001; // experimentally derived
        int ittLimit = 1000;           // experimentally derived
        int length = 6;                // xyzrpy
        int size = length * cameras.size();
        Distance distance = new Distance(cameras);

        double locations[] = new double[size];
        double eps[] = new double[size];
        for (int i = 0; i < cameras.size(); i++) 
        {
            double[] pos = cameras.get(i).getXyzrpy();
            for(int o = 0; o < 6; o++)
            {
                locations[i*length+o] = pos[o];
                eps[i*length+o] = 0.000001; // units: meters
            }
        }
        
        double[] r = LinAlg.scale(distance.evaluate(locations), -1);
        double[] oldR = null;
        
        int count = 0;
        while(!shouldStop(r, oldR, threshold) && ++count < ittLimit)
        {
            double[][] _J = NumericalJacobian.computeJacobian(distance, locations, eps);
            Matrix J = new Matrix(_J);
            
            Matrix JTtimesJplusI = J.transpose().times(J).plus(Matrix.identity(size, size));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTtimesJplusI.solve(JTr);
            
            for (int i = 0; i < locations.length; i++)
            {
                locations[i] += 0.1*dx.get(i,0); // 0.1 helps stabilize results
            }
            
            oldR = r.clone();
            r = LinAlg.scale(distance.evaluate(locations), -1);
        }
        
        for(int x = 0; x < cameras.size(); x++)
        {
            double[] position = new double[length];
            for(int y = 0; y < length; y++)
            {
                position[y] = locations[x*length+y];
            }
            cameras.get(x).setPosition(position);
        }
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        boolean stop = true;
        int length = 2;
        
        if(oldR != null)
        {
            for(int x = 0; x < r.length/length; x++)
            {
                double[] cur = new double[length];
                double[] old = new double[length];
                for(int y = 0; y < length; y++)
                {
                    cur[y] = r[x*length+y];
                    old[y] = oldR[x*length+y];
                }
                
                if(LinAlg.distance(cur, old) > threshold)
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
    
    class Distance extends Function
    {
        ArrayList<Camera> cameras;;
        HashMap<Integer,double[]>[] tagsH;
        ArrayList<Tag>[] tagsL;
        
        @SuppressWarnings("unchecked") // can't infer generic when making arrays of HashMaps or ArrayLists
        public Distance(ArrayList<Camera> cameras)
        {
            this.cameras = cameras;
            tagsH = new HashMap[cameras.size()];
            tagsL = new ArrayList[cameras.size()];
            
            for(int c = 0; c < cameras.size(); c++)
            {
                tagsH[c] = cameras.get(c).getTagsH();
                tagsL[c] = cameras.get(c).getTags();
            }
        }
        
        @Override
        public double[] evaluate(double[] location)
        {
            return evaluate(location, null);
        }
        
        @Override
        public double[] evaluate(double[] location, double[] distance)
        {
            int length = 6;
            
            if(distance == null)
            {
                distance = new double[location.length/3];
                for(int a = 0; a < distance.length; a++)
                {
                    distance[a] = 0;
                }
            }
            
            for(int c = 0; c < cameras.size(); c++)
            {
                int count = 0;
                double[] curCamera = new double[length];
                for(int x = 0; x < length; x++)
                {
                    curCamera[x] = location[c*length+x];
                }
                for(int t = 0; t < tagsL[c].size(); t++)
                {
                    double[] curTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(curCamera), tagsL[c].get(t).getTransformationMatrix()));
                    for(int o = 0; o < cameras.size(); o++)
                    {
                        if(o!=c)
                        {
                            double[] otherTag = tagsH[o].get(tagsL[c].get(t).getId());
                            if(otherTag != null)
                            {
                                count++;
                                
                                double[] otherCamera = new double[length];
                                for(int x = 0; x < length; x++)
                                {
                                    otherCamera[x] = location[o*length+x];
                                }
                                otherTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(otherCamera), LinAlg.xyzrpyToMatrix(otherTag)));
                                
                                distance[2*c] += Math.sqrt(sq(curTag[0]-otherTag[0])+sq(curTag[1]-otherTag[1])+sq(curTag[2]-otherTag[2]));
                                distance[2*c+1] += Math.sqrt(sq(curTag[3]-otherTag[3])+sq(curTag[4]-otherTag[4])+sq(curTag[5]-otherTag[5]));
                            }
                        }
                    }
                }
                if(count != 0)
                {
                    distance[c*2] /= count;
                    distance[c*2+1] /= count;
                }
            }
            
            return distance;
        }
        
        public double sq(double a)
        {
            return a*a;
        }
    }  
    
    /**
     * Once the constructor is run, call this method to retrieve an 
     * array of objects of type Camera
     * 
     * @return Camera[] - array of objects of type Camera 
     *      (contains useful information regarding each camera
     *      such as its relative position and url)
     */
    public ArrayList<Camera> getCameras()
    {
        return cameras;
    }
}

