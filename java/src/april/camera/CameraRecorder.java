package april.camera;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import april.camera.util.CameraException;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.ConfigException;
import april.util.ConfigUtil2;
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
    public static void main(String[] args) throws IOException, CameraException, InterruptedException, ConfigException
    {
        GetOpt opts = new GetOpt();
        Config config = null;

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("APRIL_CONFIG")+"/camera.config", "location of config file");
        opts.addBoolean('s', "simple", false, "ignores config file and records from any URLs given as command line args");
        opts.addString('d', "dir", "/tmp/imageLog/", "Path to save images (for simple mode only)");
        opts.addString('l', "log", "default.log", "name of lcm log (for simple mode only)");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: record video from multiple cameras.");
            System.out.println("lcm-logger is automatically ran and it is saved in the same dir as the images.");
            System.out.println("\nCameras:");
            for(String url : ImageSource.getCameraURLs())
            {
                System.out.println("\t"+url);
            }
            System.out.println();
            opts.doHelp();
            System.exit(1);
        }
        if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);
        
        String dir = opts.getString("dir");
        String log = opts.getString("log");
        if(!opts.getBoolean("simple"))
        {
            config = new ConfigFile(opts.getString("config")).getChild("logging");
            ConfigUtil2.verifyConfig(config);
            
            dir = config.requireString("dir");
            log = config.requireString("log");
        }
        
        dir += (!dir.endsWith(File.separator) ? File.separator : "");
        new File(dir).mkdirs();
        Runtime.getRuntime().exec("lcm-logger " + dir + log);

        while(true)
        {
            ArrayList<String> urls = ImageSource.getCameraURLs();
            Collections.sort(urls);
            
            if(opts.getBoolean("simple"))
            {
                for(String url : args)
                {
                    if(urls.contains(url))
                    {
                        new ImageSaverSimple(url, dir).start();
                    }
                }
            }
            else
            {
                for (String url : urls)
                {
                    try
                    {
                        CameraDriver test = new CameraDriver(url, config);
                        if(test.isGood())
                        {
                            new ImageSaver(test, dir).start();
                        }
                    } catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
            Thread.sleep(1000);
        }
    }
}
