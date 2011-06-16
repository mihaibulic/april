package mihai.camera;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.config.Config;
import april.config.ConfigFile;
import april.util.GetOpt;

public class GuiCameraPlayer extends JFrame
{
    private static final long serialVersionUID = 1L;

    public GuiCameraPlayer(Config config, int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new CameraPlayer(config, columns));
        setVisible(true);
    }
    
    public GuiCameraPlayer(int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new CameraPlayer(columns));
        setVisible(true);
    }
    
    public static void main(String[] args) throws CameraException, IOException, ConfigException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addBoolean('a', "all", true, "LCM mode: display images from all cameras published via lcm");
        opts.addBoolean('s', "standAlone", false, "Standalone mode: will capture images from all cameras");
        opts.addString('n', "config", System.getenv("APRIL_CONFIG")+ File.separator + "camera.config", "location of config file (standalone mode only)");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution) (standalone mode only)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting) (standalone mode only)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) (standalone mode only)");
        opts.addInt('x', "columns", 2, "number of columns in which to display camera images (standalone and lcm mode)");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: displays images from camera specified");  
            opts.doHelp();
            System.exit(1);
        }
        
        if(opts.getBoolean("standAlone"))
        {
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
            
            new GuiCameraPlayer(config, opts.getInt("columns"));
        }
        else if(opts.getBoolean("all"))
        {
            new GuiCameraPlayer(opts.getInt("columns"));
        }
    }
}
