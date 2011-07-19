package mihai.calibration;

import java.awt.BorderLayout;

public class NullPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    public NullPanel()
    {
        super(new BorderLayout());
    }
    @Override
    public void go(String configPath, String[] urls)
    {}

    @Override
    public void stop()
    {}
    
    @Override
    public void displayMsg(String msg, boolean error)
    {}
    
    @Override
    public void showDirections(boolean show)
    {}
}
