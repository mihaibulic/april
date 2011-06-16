package mihai.calibration.gui;

import java.awt.BorderLayout;
import javax.swing.JLabel;

public class SettingsPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    public SettingsPanel(int id)
    {
        super(id, new BorderLayout());
        
        add(new JLabel("Hi!"), BorderLayout.CENTER);
    }

    @Override
    public void go(String configPath, String... urls)
    {
        alertListener();
    }

    @Override
    public void stop()
    {
    }

}
