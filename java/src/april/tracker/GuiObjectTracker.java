package april.tracker;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import april.camera.CameraDriver;
import april.camera.util.CameraException;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import april.util.GetOpt;

public class GuiObjectTracker extends JFrame implements ActionListener
{
    private static final long serialVersionUID = 1L;
    
    private ObjectTrackerPanel otp;
    private JCheckBox directions;
    
    public GuiObjectTracker(String configPath, boolean display) throws ConfigException, CameraException, IOException
    {
        super("Object Tracker");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        
        if (ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);
        
        otp = new ObjectTrackerPanel(display);
        otp.showDisplay(true);
        
        ArrayList<String> allUrls = ImageSource.getCameraURLs();
        ArrayList<String> urls = new ArrayList<String>();
        Config config = new ConfigFile(configPath);
        ConfigUtil2.verifyConfig(config);
        
        for(String url : allUrls)
        {
            if(CameraDriver.isValidUrl(config, url))
            {
                urls.add(url);
            }
        }
        otp.go(configPath);
        
        directions = new JCheckBox("show directions", true);
        add(otp, BorderLayout.CENTER);

        directions.addActionListener(this);
        add(directions, BorderLayout.SOUTH);
        
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

    public void actionPerformed(ActionEvent arg0)
    {
        otp.showDisplay(directions.isSelected()); 
    }
}
