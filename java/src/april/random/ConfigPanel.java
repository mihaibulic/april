package april.random;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.filechooser.FileNameExtensionFilter;
import april.calibration.Broadcaster;

public class ConfigPanel extends Broadcaster implements ActionListener
{
    private static final long serialVersionUID = 1L;
    private JFileChooser fileChooser;
    
    public ConfigPanel(String id)
    {
        super(id, new BorderLayout());

        add(new JLabel("Please double click an existing config file to use"), BorderLayout.NORTH);

        FileNameExtensionFilter filter = new FileNameExtensionFilter("config file", "config", "conf", "configure");
        fileChooser = new JFileChooser();
        fileChooser.setFileFilter(filter);
        fileChooser.setControlButtonsAreShown(false);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setDragEnabled(false);

        fileChooser.addActionListener(this);
        
        add(fileChooser, BorderLayout.CENTER);
    }

    public void actionPerformed(ActionEvent event)
    {
        if(fileChooser.getSelectedFile().isFile())
        {
            alertListener(true, fileChooser.getSelectedFile().toString());
        }
    }
    
    public void go(String configPath)
    {
        alertListener(false);
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
