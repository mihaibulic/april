package mihai.tracker;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFrame;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.GetOpt;

public class GuiObjectTracker extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    public GuiObjectTracker(String configPath, boolean display) throws ConfigException, CameraException, IOException
    {
        super("Object Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        
        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);
        
        ObjectTrackerPanel otp = new ObjectTrackerPanel(0, display);
        
        ArrayList<String> allUrls = ImageSource.getCameraURLs();
        ArrayList<String> urls = new ArrayList<String>();
        Config config = new ConfigFile(configPath);
        Util.verifyConfig(config);
        
        for(String url : allUrls)
        {
            if(Util.isValidUrl(config, url))
            {
                urls.add(url);
            }
        }
        otp.go(configPath, urls.toArray(new String[urls.size()]));
        
        add(otp);
        setVisible(true);
    }

    public static void main(String[] args) throws Exception
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("APRIL_CONFIG")+File.separator+"camera.config", "location of config file");
        opts.addBoolean('d', "display", true, "if true will display a GUI with the camera and tag locations");
        
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
        
        new GuiObjectTracker(opts.getString("config"), opts.getBoolean("display")); 
    }
}