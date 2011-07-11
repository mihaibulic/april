package mihai.camera;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.jcam.ImageSource;
import april.util.GetOpt;

/**
 * 
 * Saves images from cameras onto HDD and publishes the path via an LCM message in real time 
 * 
 * @author Mihai Bulic
 *
 */
public class GenericCameraRecorder
{
    public static void main(String[] args) throws IOException, CameraException, InterruptedException, ConfigException
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "gray8", "gray8 or gray16 (overrides config color setting)");
        opts.addInt('f', "fps", 15, "framerate to use if player (overrides config framerate)");
        opts.addString('d', "dir", "/var/tmp", "Path to save images");
        opts.addString('l', "log", "default.log", "name of lcm log");
        
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
        
        if(ImageSource.getCameraURLs().size() == 0 || args.length == 0) throw new CameraException(CameraException.NO_CAMERA);

        String dir = opts.getString("dir") + (!opts.getString("dir").endsWith(File.separator) ? File.separator : "");
        new File(dir).mkdirs();
        
        Runtime.getRuntime().exec("lcm-logger " + dir + opts.getString("log"));
        
        ArrayList<String> urls = ImageSource.getCameraURLs();
        for(String url : args)
        {
            if(urls.contains(url))
            {
                new ImageHandler(url, dir, opts.getInt("fps"), opts.getString("resolution").contains("lo"), opts.getString("colors").contains("16"));
            }
        }
    }
}
