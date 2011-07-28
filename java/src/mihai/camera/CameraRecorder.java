package mihai.camera;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import april.config.Config;
import april.config.ConfigFile;
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
    public static void main(String[] args) throws IOException, CameraException, InterruptedException, ConfigException
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('d', "dir", "", "Path to save images");
        opts.addString('l', "log", "", "name of lcm log");
        
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
        
        Config config = new ConfigFile(opts.getString("config"));
        ConfigUtil.verifyConfig(config);
        String dir = opts.getString("dir").isEmpty() ? config.requireString("dir") : opts.getString("dir");
        String log = opts.getString("log").isEmpty() ? config.requireString("log") : opts.getString("log");
        
        if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        dir += (!dir.endsWith("/") ? "/" : "");
        new File(dir).mkdirs();
        
        Runtime.getRuntime().exec("lcm-logger " + dir + log);
        
        ArrayList<ImageSaver> iss = new ArrayList<ImageSaver>();
        while(true)
        {
            ArrayList<String> urls = ImageSource.getCameraURLs();
            
            for (int x = 0; x < urls.size(); x++)
            {
                try
                {
                    CameraDriver test = new CameraDriver(urls.get(x), config);
                    if(test.isGood())
                    {
                        ImageSaver is = new ImageSaver(test, dir);
                        is.start();
                        iss.add(is);
                    }
                } catch (Exception e)
                {
                    e.printStackTrace();
                }
            }
            Thread.sleep(1000);
        }
    }
}
