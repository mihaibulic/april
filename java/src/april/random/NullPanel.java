package april.random;

import java.awt.BorderLayout;
import april.calibration.Broadcaster;

public class NullPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    public NullPanel()
    {
        super(new BorderLayout());
    }
    @Override
    public void go(String configPath)
    {}

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
