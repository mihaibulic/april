package mihai.camera;

import java.awt.Toolkit;
import java.io.IOException;
import javax.swing.JFrame;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.GetOpt;

public class GuiIntrinsicsCalibrator extends JFrame
{
    private static final long serialVersionUID = 1L;

    public GuiIntrinsicsCalibrator(Config config, String configPath, String url) throws IOException, ConfigException, CameraException
    {
        super("Intrinsics Calibrator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new IntrinsicsCalibrator(config, configPath, url));
        setVisible(true);
    }
    
    public static void main(String[] args) throws IOException, ConfigException, CameraException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('u', "url", "dc1394://", "url of camera to use (only need to set if multiple dc1394 cameras are connected)");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate)");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: allows user to discover intrinsic parameters of camera experimentally.");  
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
        
        String url = opts.getString("url");
        for(String u : ImageSource.getCameraURLs())
        {
            if(u.contains(url))
            {
                url = u;
                break;
            }
        }
        
        new GuiIntrinsicsCalibrator(config, opts.getString("config"), url);
    }
}
