package edu.umich.mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import javax.swing.JFrame;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.Util;
import edu.umich.mihai.vis.VisCamera;

/**
 * 
 * Gives camera to camera coordinates given that tags are spread out in the view of multiple cameras
 * 
 * @author Mihai Bulic
 *
 */
public class ExtrinsicsCalibrator
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbTags = vw.getBuffer("tags");

    private double tagSize;
    private HashMap<String, Integer> knownUrls = new HashMap<String, Integer>();

    private ArrayList <Camera> cameras;

    public ExtrinsicsCalibrator(Config config, boolean display) throws Exception
    {
        Util.verifyConfig(config);
        
    	tagSize = config.requireDouble("tagSize");
        ArrayList<String> urls = ImageSource.getCameraURLs();
        cameras = new ArrayList<Camera>();

        System.out.print("ICC-Constructor: starting imagereaders...");
        for(String url : urls)
        {
        	Camera test = new Camera(config.getChild(CamUtil.getUrl(config, url)), url);
        	if(test.isGood())
        	{
        		cameras.add(test);
        	}
        }
        System.out.println("done");
        
        System.out.println("ICC-run: Aggregating tags...");
        for (Camera camera : cameras)
        {
            System.out.print("ICC-run: aggregating tags of camera " + camera.getCameraId() + "...");
            camera.aggregateTags(5, tagSize);
            System.out.println("done (found " + camera.getTagCount() + " tags)");
        }

        System.out.print("ICC-run: Resolving extrinsics...");
        Collections.sort(cameras, new CameraComparator());
        getAllCorrespondences();
        resolveExtrinsics();
        System.out.println("done");
        
        if(display)
        {
            String output = "";
            Random rand = new Random();
            for (Camera cam : cameras)
            {
                Color color = new Color(rand.nextInt(256), 127, 127);
                double[] pos = cam.getPosition();
                
                output += "camera: " + cam.getCameraId() + "\n";
                output += "(x,y,z): " + pos[0] + ", " + pos[1] + ", " + pos[2] + "\n";
                output += "(r,p,y): " + pos[3] + ", " + pos[4] + ", " + pos[5] + "\n\n";
                
                double[][] camM = cam.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
                
                ArrayList<Camera.Tag> tags = cam.getDetections();
                for (Camera.Tag tag : tags)
                {
                    double tagM[][] = LinAlg.xyzrpyToMatrix(tag.xyzrpy);
                    vbTags.addBuffered(new VisChain(camM, tagM, new VisRectangle(tagSize, tagSize, 
                            new VisDataLineStyle(color, 2))));
                }
            }

            // TODO write to config file
            System.out.println(output);
            showGui(output);
        }
    }

    private void showGui(String output)
    {
    	jf = new JFrame("Inter Camera Calibrater");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1000, 500);
    	
        vbTags.switchBuffer();
        vbCameras.switchBuffer();
        jf.setVisible(true);
    }

    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) ");
        opts.addString('t', "tagSize", "", "size of tags used in meters (overrides config framerate)");

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
    	if(!opts.getString("tagSize").isEmpty())
    	{
    		config.setDouble("tagSize", Double.parseDouble(opts.getString("tagSize")));
    	}

        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        new ExtrinsicsCalibrator(config, true);
    }

    private void getAllCorrespondences() throws CameraException
    {
    	if(cameras.get(0).getTagCount() == 0) throw new CameraException(CameraException.NO_TAGS);
    	
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            if (cameras.get(cam).getTagCount() == 0) throw new CameraException(CameraException.NO_TAGS);
            boolean found = false;

            for (int main = 0; main < cameras.size() && !found; main++)
            {
                if (main != cam)
                {
                    getCorrespondence(main, cameras.get(main), cameras.get(cam));
                    found = cameras.get(cam).isCertain();
                }
            }
            
            if (!found) throw new CameraException(CameraException.UNCERTAIN);
        }
    }

    private void getCorrespondence(int main, Camera mainCam, Camera auxCam)
    {
        int mainIndex = 0;
        int auxIndex = 0;
        double mainM[][];
        double auxM[][];
        ArrayList<Camera.Tag> mainTags = mainCam.getDetections();
        ArrayList<Camera.Tag> auxTags = auxCam.getDetections();

        auxCam.setMain(main);
        auxCam.clearPotentialPositions();

        while (mainIndex < mainTags.size() && auxIndex < auxTags.size())
        {
            if (auxTags.get(auxIndex).id == mainTags.get(mainIndex).id)
            {
                mainM = LinAlg.xyzrpyToMatrix(mainTags.get(mainIndex).xyzrpy);
                auxM = LinAlg.xyzrpyToMatrix(auxTags.get(auxIndex).xyzrpy);
                
                auxCam.addCorrespondence(LinAlg.matrixAB(mainM, LinAlg.inverse(auxM)));
                mainIndex++;
                auxIndex++;
            }
            else if (auxTags.get(auxIndex).id > auxTags.get(mainIndex).id)
            {
                mainIndex++;
            }
            else
            {
                auxIndex++;
            }
        }
    }

    private void resolveExtrinsics() throws CameraException
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
    
    /**
     * 
     * @param url - the URL of the camera in question
     * @return - the xyzrpy coordinates of this camera relative to the main camera (index = 0)
     */
    public double[] getCameraPostion(String url)
    {
        return getCameraPostion(knownUrls.get(url));
    }

    /**
     * 
     * @param index - index of the camera in question as printed on it (see urls hashmap of this class)
     * @return - the xyzrpy coordinates of this camera relative to the main camera (index = 0)
     */
    public double[] getCameraPostion(int index)
    {
        double position[] = new double[6]; 
        
        for(Camera cam : cameras)
        {
            if(index == cam.getCameraId())
            {
                position = cam.getPosition();
            }
        }
        
        return position;
    }
}
