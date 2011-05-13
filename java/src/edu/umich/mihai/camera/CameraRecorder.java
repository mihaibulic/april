package edu.umich.mihai.camera;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.Util;

import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.GetOpt;
import april.util.TimeUtil;

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
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate)");
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
        if(!opts.getString("dir").isEmpty())
        {
        	config.setString("dir", opts.getString("dir"));
        }
        if(!opts.getString("log").isEmpty())
        {
        	config.setString("log", opts.getString("log"));
        }
        
        if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);

        String dir = config.getString("dir") + (!config.getString("dir").endsWith("/") ? "/" : "");
        new File(dir).mkdirs();
        
        Runtime.getRuntime().exec("lcm-logger " + dir + config.getString("log"));
        
        ArrayList<String> urls = ImageSource.getCameraURLs();
        ImageReader irs[] = new ImageReader[urls.size()];
        ImageSaver iss[] = new ImageSaver[urls.size()];
        
        for (int x = 0; x < irs.length; x++)
        {
            try
            {
                irs[x] = new ImageReader(config, urls.get(x));
                iss[x] = new ImageSaver(irs[x], urls.get(x), dir);
                iss[x].start();
            } catch (Exception e)
            {
                e.printStackTrace();
            }

        }
        
        TimeUtil.sleep(1500);
        Console console = System.console();
        
        while(true)
        {
	        if(console.readLine("Type q or quit to exit\t").contains("q"))
	        {
	        	for (int x = 0; x < irs.length; x++)
	            {
	        		iss[x].kill();
	        		iss[x].join();
	        		irs[x].kill();
	        		irs[x].join();
	            }
	        	System.exit(0);
	        }
        }
    }
}
