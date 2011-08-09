package april.random;

import java.awt.BorderLayout;
import javax.swing.JLabel;
import april.calibration.Broadcaster;

public class SettingsPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    public SettingsPanel(String id)
    {
        super(id, new BorderLayout());
        
        add(new JLabel("Hi!"), BorderLayout.CENTER);
    }

    @Override
    public void go(String configPath, String...urls)
    {
        alertListener();
    }

    @Override
    public void kill()
    {}

    @Override
    public void displayMsg(String msg, boolean error)
    {}
    
    @Override
    public void showDisplay(boolean show)
    {}
}
