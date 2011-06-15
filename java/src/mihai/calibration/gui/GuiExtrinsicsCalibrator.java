package mihai.calibration.gui;

import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import javax.swing.JFrame;
import mihai.calibration.ExtrinsicsCalibrator;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.GetOpt;

public class GuiExtrinsicsCalibrator extends JFrame
{
    private static final long serialVersionUID = 1L;

    public GuiExtrinsicsCalibrator(Config config, boolean display, boolean verbose) throws ConfigException, CameraException, IOException, InterruptedException
    {
        super("Extrinsics Calibrator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        add(new ExtrinsicsCalibrator(config, display, verbose));
        setVisible(true);
    }
    
    public static void main(String[] args) throws IOException, ConfigException, CameraException, InterruptedException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("APRIL_CONFIG")+File.separator+"camera.config", "location of config file");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) ");
        opts.addString('t', "tagSize", "", "size of tags used in meters (overrides config framerate)");
        opts.addBoolean('d', "display", true, "if true will display a GUI with the camera and tag locations");
        opts.addBoolean('v', "verbose", true, "if true will print out more information regarding calibrator's status");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: Calibrate relative positions of multiple cameras.");
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
        if(!opts.getString("tagSize").isEmpty())
        {
            config.setDouble("tagSize", Double.parseDouble(opts.getString("tagSize")));
        }

        if (ImageSource.getCameraURLs().size() == 0) 
        {
            throw new CameraException(CameraException.NO_CAMERA);
        }

        new GuiExtrinsicsCalibrator(config, opts.getBoolean("display"), opts.getBoolean("verbose"));
    }
}
