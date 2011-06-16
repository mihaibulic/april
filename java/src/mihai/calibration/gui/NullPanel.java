package mihai.calibration.gui;

import java.awt.BorderLayout;

public class NullPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;

    public NullPanel(int id)
    {
        super(id, new BorderLayout());
    }
    @Override
    public void go(String configPath, String... urls)
    {}

    @Override
    public void stop()
    {}
}
