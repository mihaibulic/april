package april.random;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import april.calibration.Broadcaster;

public class IntroPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;
    
    public IntroPanel() throws IOException
    {
        super(new BorderLayout());

        JPanel directionsPanel = new JPanel(new GridLayout(11, 1, 0, 0));
        JLabel empty1 = new JLabel(" ");
        empty1.setFont(new Font("Serif", Font.PLAIN, 72));
        JLabel empty2 = new JLabel(" ");
        empty1.setFont(new Font("Serif", Font.PLAIN, 32));
        
        directionsPanel.add(empty1);
        directionsPanel.add(new JLabel("This wizard is broken up into five main steps:"));
        directionsPanel.add(new JLabel("    1. Basic setup - selecting a configuration file to use for the setup"));
        directionsPanel.add(new JLabel("    2. Intrinsics calibration - quick intrinsic calibration step to be done for each camera"));
        directionsPanel.add(new JLabel("    3. Camera Viewer - view each camera's video to line up calibration targets"));
        directionsPanel.add(new JLabel("    4. Extrinsics calibration - calibrating the camera-to-camera positions"));
        directionsPanel.add(new JLabel("    5. Test Drive - use the object tracker to test your ability to track objects in space"));
        directionsPanel.add(new JLabel("            (this can be used seperately from the rest of the calibrator as well)"));
        directionsPanel.add(empty2);
        directionsPanel.add(new JLabel("Press next to continue...", JLabel.CENTER));

        String logo = System.getenv("APRIL_DOCS");
        logo += (logo.endsWith(File.separator) ? "" : File.separator) + "april-logo.png";
        
        JPanel aprilPanel = new JPanel();
        JLabel aprilLabel = new JLabel(new ImageIcon(logo,
                "The APRIL Robotics Laboratory at the University of Michigan"+
        		" investigates Autonomy, Perception, Robotics, Interfaces, and Learning," +
                "and is part of the Computer Science and Engineering department. " + 
                "It is led by Assistant Professor Edwin Olson. Copyright (C) 2010."));
        aprilLabel.setBorder(new EtchedBorder(EtchedBorder.LOWERED));
        aprilPanel.add(aprilLabel);

        add(directionsPanel, BorderLayout.CENTER);
        add(aprilPanel, BorderLayout.SOUTH);
    }

    protected ImageIcon createImageIcon(String path, String description)
    {
        java.net.URL imgURL = getClass().getResource(path);
        if (imgURL != null)
        {
            return new ImageIcon(imgURL, description);
        }
        else
        {
            System.err.println("Couldn't find file: " + path);
            return null;
        }
    }

    @Override
    public void go(String configPath)
    {
        alertListener(true);
    }
    @Override
    public void kill()
    {
        //This method doesn't do anything since this panel doesn't do anything
    }
    @Override
    public void displayMsg(String msg, boolean error)
    {
        // no error messages to display
    }
    
    public void showDisplay(boolean show)
    {
        // no optional directions to show
    }
}
