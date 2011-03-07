package edu.umich.mihai.camera;

import java.io.File;
import java.io.IOException;
import april.jcam.ImageSource;
import april.util.GetOpt;

/**
 * 
 * Saves images from cameras onto HDD and publishes the path via an LCM message in real time 
 * 
 * @author Mihai Bulic
 *
 */
public class CameraRecorder
{
    public static void main(String[] args) throws IOException, CameraException
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
            System.out.println("Usage: record video from multiple cameras.");
            System.out.println("lcm-logger is automatically ran and it is saved in the same dir as the images");
            opts.doHelp();
            System.exit(1);
        }
        
        if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        String dir = opts.getString("dir") + (!opts.getString("dir").endsWith("/") ? "/" : "");

        // ensure that the directory exists
        new File(dir).mkdirs();
        
        Runtime.getRuntime().exec("lcm-logger " + dir + opts.getString("log"));
        
        for (String url : ImageSource.getCameraURLs())
        {
            try
            {
                ImageReader ir = new ImageReader(url, opts.getString("resolution").contains("lo"), opts.getString("colors").contains("16"), opts.getInt("fps"));
                (new ImageSaver(ir, url, dir)).start();

                // new CameraDriver(url, dir, opts.getString("resolution").contains("lo"), opts.getString("colors").contains("16"), opts.getInt("fps"), true);
            } catch (Exception e)
            {
                e.printStackTrace();
            }

        }
        
    }
}
