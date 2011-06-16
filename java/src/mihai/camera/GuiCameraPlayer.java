package mihai.camera;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.util.GetOpt;

public class GuiCameraPlayer extends JFrame
{
    private static final long serialVersionUID = 1L;

    public GuiCameraPlayer(String configPath, int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        CameraPlayerPanel camplayer = new CameraPlayerPanel(0, columns);
        camplayer.go(configPath);
        add(camplayer);
        setVisible(true);
    }
    
    public GuiCameraPlayer(int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new CameraPlayerPanel(columns));
        setVisible(true);
    }
    
    public static void main(String[] args) throws CameraException, IOException, ConfigException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addBoolean('a', "all", true, "LCM mode: display images from all cameras published via lcm");
        opts.addBoolean('s', "standAlone", true, "Standalone mode: will capture images from all cameras");
        opts.addString('n', "config", System.getenv("APRIL_CONFIG")+ File.separator + "camera.config", "location of config file (standalone mode only)");
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
            new GuiCameraPlayer(opts.getString("config"), opts.getInt("columns"));
        }
        else if(opts.getBoolean("all"))
        {
            new GuiCameraPlayer(opts.getInt("columns"));
        }
    }
}
