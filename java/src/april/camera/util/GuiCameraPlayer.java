package april.camera.util;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import april.camera.CameraPlayerPanel;
import april.util.ConfigException;
import april.util.GetOpt;

public class GuiCameraPlayer extends JFrame
{
    private static final long serialVersionUID = 1L;

    public GuiCameraPlayer(String configPath, int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        CameraPlayerPanel camplayer = new CameraPlayerPanel(columns, false);
        add(camplayer);
        setVisible(true);
        camplayer.go(configPath);
    }
    
    public GuiCameraPlayer(int columns, String dir) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new CameraPlayerPanel(columns, false, dir));
        setVisible(true);
    }
    
    public static void main(String[] args) throws CameraException, IOException, ConfigException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addInt('x', "columns", 2, "number of columns in which to display camera images (standalone and lcm mode)");
        opts.addString('c', "config", System.getenv("APRIL_CONFIG")+ File.separator + "camera.config", "location of config file (standalone mode only)");
        opts.addBoolean('l', "lcm", false, "display images from all cameras published via lcm");
        opts.addString('d', "dir", null, "LCM mode only: if logs were moved to another folder, set this.");
        
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
        
        if(opts.getBoolean("lcm"))
        {
            new GuiCameraPlayer(opts.getInt("columns"), opts.getString("dir"));
        }
        else
        {
            new GuiCameraPlayer(opts.getString("config"), opts.getInt("columns"));
        }
    }
}
