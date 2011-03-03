package edu.umich.mihai.camera;

import java.util.ArrayList;
import java.util.Arrays;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.TagDetection;
import april.util.GetOpt;

public class InterCameraCalibrator
{
    private Camera cameras[];
    
    public InterCameraCalibrator(GetOpt opts) throws Exception
    {
        ArrayList<String> urls = ImageSource.getCameraURLs();
        cameras = new Camera[urls.size()];
        
        for (int x = 0; x < cameras.length; x++)
        {
            try
            {
                ImageReader ir = new ImageReader(urls.get(x), opts.getString("resolution").contains("lo"), opts.getString("colors").contains("16"), opts.getInt("fps"));
                cameras[x] = new Camera(ir);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        //aggregate tag detections
        for(Camera camera : cameras)
        {
            camera.addDetections();
        }
        
        Arrays.sort(cameras, new CameraComparator());
        
        findCoordinates();
        
        findInterCamPos();
    }

    private void findInterCamPos() throws Exception
    {
        cameras[0].setPosition(new double[] {0,0,0,0,0,0}, 0);
        for(int cam = 1; cam <cameras.length; cam++)
        {
            while(cameras[cam].getMain() != 0)
            {
                if(cameras[cam].getMain() == cam) 
                {
                    throw new Exception("Camera to camera calibration has halted due to a cycle in camera to camera positions");
                }
                
                double[][] pos = LinAlg.xyzrpyToMatrix(cameras[cam].getPosition());
                double[][] posToOldMain= LinAlg.xyzrpyToMatrix(cameras[cameras[cam].getMain()].getPosition()); 
                double[][] posToNewMain = LinAlg.matrixAB(pos, posToOldMain); 
                
                cameras[cam].setPosition(LinAlg.matrixToXyzrpy(posToNewMain), cameras[cameras[cam].getMain()].getMain());
            }
        }
    }

    private void findCoordinates()
    {
        for(int cam = 1; cam < cameras.length; cam++)
        {
            for(int main = 0; !cameras[cam].isCertain() && main < cameras.length; main++)
            {
                if(main == cam) continue;
                
                findCoordinates(main, cameras[main].getDetections(), cameras[cam]);
            }
            if(cameras[cam].isCertain())
            {
                cameras[cam].setPosition();
            }
            else
            {
                System.err.println("Unable to calculate intercamera coordinates due to high uncertainty."+
                "  Are the tags to difficult to see?  Has camera calibration been done?");
            }
        }
    }

    private void findCoordinates(int main, ArrayList<TagDetection> tags, Camera camera)
    {
        final double f = 477.5;
        final double tagSize = 5*(2.54/100);
        int index = 0;
        TagDetection auxTags[] = camera.getDetections().toArray(new TagDetection[1]);
        TagDetection mainTags[] = tags.toArray(new TagDetection[1]);
        double mainM[][] = CameraUtil.homographyToPose(f, f, tagSize, mainTags[index].homography);
        
        camera.setMain(main);
        
        for(int y = 0; y < auxTags.length; y++)
        {
            if(auxTags[y].id == mainTags[index].id)
            {
                double auxM[][] = CameraUtil.homographyToPose(f, f, tagSize, auxTags[y].homography);
                double[][] camToCamTransform = LinAlg.matrixAB(LinAlg.inverse(auxM), mainM);
                camera.addCoordinates(LinAlg.matrixToXyzrpy(camToCamTransform));
            }
            else if (auxTags[y].id > mainTags[index].id)
            {
                index++;
                mainM = CameraUtil.homographyToPose(f, f, tagSize, mainTags[index].homography);
            }
        }
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
        
        if(ImageSource.getCameraURLs().size() == 0)
        {
            System.out.println("No cameras found.  Are they plugged in?");
            System.exit(1);
        }
        
        new InterCameraCalibrator(opts);
    }
}
