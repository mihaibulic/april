package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.Toolkit;
import javax.swing.JFrame;

public class CalibTest extends JFrame implements Broadcaster.Listener
{
    private static final long serialVersionUID = 1L;
    String url;
    Broadcaster cal;
    
    public CalibTest(String url)
    {
        super("Calibrate");
        setSize(Toolkit.getDefaultToolkit().getScreenSize());

        this.url = url;
        cal = new IntrinsicsPanel(0);
        cal.setListener(this);
        cal.go("/home/april/mihai/config/camera.config", url);
        add(cal, BorderLayout.CENTER);
        setVisible(true);
    }
    
    public static void main(String[] args)
    {
        new CalibTest(args[0]);
    }

    public void handle(int id, boolean ready, String... info)
    {
        cal.go("/home/april/mihai/config/camera.config", url);
    }

}
