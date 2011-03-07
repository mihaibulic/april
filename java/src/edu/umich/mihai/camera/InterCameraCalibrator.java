package edu.umich.mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.TagDetection;
import april.util.GetOpt;
import april.vis.VisCamera;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

/**
 * 
 * Gives camera to camera coordinates given that tags are spread out in the view of multiple cameras
 * 
 * @author Mihai Bulic
 *
 */
public class InterCameraCalibrator
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbTags = vw.getBuffer("tags");
    private boolean display;

    private final double version = 0.3;
    private double[] fc;
    private double tagSize;
    private HashMap<String, Integer> knownUrls = new HashMap<String, Integer>();

    private Camera cameras[];

    public InterCameraCalibrator()
    {
        // get tagsize and fc from txt file 
    }
    
    /**
     * 
     * @param tagSize - the length of the edge of a tag in meters
     * @param fc - the x,y axis focal lengths of the cameras to be used
     * @throws Exception - If a camera sees no tags or if there is a cycle 
     *          i.e. camera0 and camera1 see each other; camera2 and camera3 see each other;
     *          but camera0/1 do not see camera2/3
     */
    public InterCameraCalibrator(double tagSize, double[] fc) throws Exception
    {
        this(false, false, 15, tagSize, fc, false);
    }
    
    /**
     * 
     * @param loRes - true iff low resolution is desired from the cameras
     * @param color16 - true iff 16 colors, instead of 8, are desired from the cameras
     * @param fps - max fps from the cameras
     * @param tagSize - the length of the edge of a tag in meters
     * @param fc - the x,y axis focal lengths of the cameras to be used
     * @throws Exception - If a camera sees no tags or if there is a cycle 
     *          i.e. camera0 and camera1 see each other; camera2 and camera3 see each other;
     *          but camera0/1 do not see camera2/3
     */
    public InterCameraCalibrator(boolean loRes, boolean color16, int fps, double tagSize, double[] fc) throws Exception
    {
        this(loRes, color16, fps, tagSize, fc, false);
    }
    
    public InterCameraCalibrator(boolean loRes, boolean color16, int fps, double tagSize, double[] fc, boolean display) throws Exception
    {
        this.fc = fc;
        this.tagSize = tagSize;
        this.display = display;
        ArrayList<String> currentUrls = organizeURLS();
        cameras = new Camera[currentUrls.size()];

        System.out.println("ICC-Constructor: starting imagereaders...");
        setKnownUrls();
        for (int x = 0; x < cameras.length; x++)
        {
            ImageReader ir = new ImageReader(currentUrls.get(x), loRes, color16, fps);
            ir.start();
            cameras[x] = new Camera(ir, knownUrls.get(currentUrls.get(x)));
        }
        
        run();
    }

    public void run() throws CameraException
    {
        System.out.println("ICC-run: imagereaders started. aggregating tags...");

        // aggregate tag detections
        for (Camera camera : cameras)
        {
            System.out.println("ICC-run: aggregating tags of camera " + camera.getIndex());
            camera.addDetections();
        }

        System.out.println("ICC-run: tags aggregated. finding coordinates...");

        Arrays.sort(cameras, new CameraComparator());

        findCoordinates();

        System.out.println("ICC-run: coordinates found. finding intercam pos...");

        findInterCamPos();

        if(display)
        {
            System.out.println("ICC-run: intercam pos found. gr..."); // XXX
            jf = new JFrame("Inter Camera Calibrater v" + version);
            jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            jf.setLayout(new BorderLayout());
            jf.add(vc, BorderLayout.CENTER);
            jf.setSize(1000, 500);

            System.out.println("ICC-run: display set up. graphing tags/cameras..."); // XXX
            
            String output = "";
            for (Camera cam : cameras)
            {
                Color color = (cam.getIndex() == 12 ? Color.blue : Color.red);
                double[] pos = cam.getPosition();
                output += "camera: " + cam.getIndex() + "\n";
                output += "(x,y,z): " + pos[0] + ", " + pos[1] + ", " + pos[2] + "\n";
                output += "(r,p,y): " + pos[3] + ", " + pos[4] + ", " + pos[5] + "\n\n";
                double[][] camM = cam.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
                ArrayList<TagDetection> tags = cam.getDetections();
                for (TagDetection tag : tags)
                {
                    double tagM[][] = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, tag.homography);
                    vbTags.addBuffered(new VisChain(camM, tagM, new VisRectangle(tagSize, tagSize, 
                            new VisDataLineStyle(color, 2))));
                }
                cam.getReader().kill();
            }

            System.out.println(output);
            showGui(output);
        }
    }

    private void showGui(String output)
    {
        vbTags.switchBuffer();
        vbCameras.switchBuffer();
        jf.setVisible(true);
        JOptionPane.showMessageDialog(jf, output);
    }

    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "colors", "gray8", "gray8 or gray16");
        opts.addInt('f', "fps", 15, "set the max fps to publish");
        opts.addString('l', "log", "default.log", "name of lcm log");
        opts.addString('d', "dir", "/tmp/imageLog/", "Path to save images");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480");

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

        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        boolean loRes = opts.getString("resolution").contains("lo");
        boolean color16 = opts.getString("colors").contains("16");
        int fps = opts.getInt("fps");

        new InterCameraCalibrator(loRes, color16, fps, 0.1275, new double[] {477.5, 477.5}, true); // XXX magic numbers
    }

    private void findCoordinates() throws CameraException
    {
        for (int cam = 1; cam < cameras.length; cam++)
        {
            if (cameras[cam].getTagCount() == 0) throw new CameraException(CameraException.NO_TAGS);

            for (int main = 0; main < cameras.length; main++)
            {
                if (main != cam)
                {
                    findCoordinates(main, cameras[main], cameras[cam]);
    
                    if (cameras[cam].isCertain())
                    {
                        break;
                    }
                }
            }

            if (!cameras[cam].isCertain()) throw new CameraException(CameraException.UNCERTAIN);
        }
    }

    private void findCoordinates(int main, Camera mainCam, Camera auxCam)
    {
        int mainIndex = 0;
        int auxIndex = 0;
        TagDetection auxTags[] = auxCam.getDetections().toArray(new TagDetection[1]);
        TagDetection mainTags[] = mainCam.getDetections().toArray(new TagDetection[1]);
        double mainM[][] = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, mainTags[mainIndex].homography);
        double auxM[][];

        auxCam.setMain(main);
        auxCam.clearCoordinates();

        while (mainIndex < mainTags.length && auxIndex < auxTags.length)
        {
            auxM = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, auxTags[auxIndex].homography);
            mainM = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, mainTags[mainIndex].homography);

            if (auxTags[auxIndex].id == mainTags[mainIndex].id)
            {
                auxCam.addCoordinates(LinAlg.matrixAB(mainM, LinAlg.inverse(auxM)));
                mainIndex++;
                auxIndex++;
            }
            else if (auxTags[auxIndex].id > mainTags[mainIndex].id)
            {
                mainIndex++;
            }
            else
            {
                auxIndex++;
            }
        }
    }

    private void findInterCamPos() throws CameraException
    {
        cameras[0].setPosition(new double[] { 0, 0, 0, 0, 0, 0 }, 0);
        for (int cam = 1; cam < cameras.length; cam++)
        {
            cameras[cam].setPosition();
            while (cameras[cam].getMain() != 0)
            {
                if (cameras[cam].getMain() == cam) throw new CameraException(CameraException.CYCLE);

                double[][] pos = cameras[cam].getTransformationMatrix();
                double[][] posToOldMain = cameras[cameras[cam].getMain()].getTransformationMatrix();
                double[][] posToNewMain = LinAlg.matrixAB(pos, posToOldMain);

                cameras[cam].setPosition(posToNewMain, cameras[cameras[cam].getMain()].getMain());
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
    public Camera[] getCameras()
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
            if(index == cam.getIndex())
            {
                position = cam.getPosition();
            }
        }
        
        return position;
    }
    
    private ArrayList<String> organizeURLS()
    {
        ArrayList<String> urls = ImageSource.getCameraURLs();

        for (int x = 0; x < urls.size(); x++)
        {
            if (!urls.get(x).contains("dc1394"))
            {
                urls.remove(x);
            }
        }

        return urls;
    }

    private void setKnownUrls()
    {
        knownUrls.put("dc1394://b09d01008b51b8", 0);
        knownUrls.put("dc1394://b09d01008b51ab", 1);
        knownUrls.put("dc1394://b09d01008b51b9", 2);
        knownUrls.put("dc1394://b09d01009a46a8", 3);
        knownUrls.put("dc1394://b09d01009a46b6", 4);
        knownUrls.put("dc1394://b09d01009a46bd", 5);

        knownUrls.put("dc1394://b09d01008c3f62", 10); // in lab
        knownUrls.put("dc1394://b09d01008c3f6a", 11); // has J on it
        knownUrls.put("dc1394://b09d01008e366c", 12); // unmarked
    }
}
