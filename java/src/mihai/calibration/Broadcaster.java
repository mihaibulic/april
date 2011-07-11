package mihai.calibration;

import java.awt.LayoutManager;
import javax.swing.JPanel;

public abstract class Broadcaster extends JPanel
{
    private static final long serialVersionUID = 1L;
    
    private Listener listener;
    private int id;
    
    public interface Listener
    {
        public void handle(int id, boolean ready, String ...info);
    }
    
    public abstract void displayMsg(String msg, boolean error);
    public abstract void go(String configPath, String ...urls);
    public abstract void stop();
    
    public Broadcaster(int id, LayoutManager layout)
    {
        super(layout);
        this.id = id;
    }
    
    public void setListener(Listener listener)
    {
        this.listener = listener;
    }
    
    public void alertListener()
    {
        alertListener(true, "");
    }
    
    public void alertListener(boolean ready)
    {
        alertListener(ready, "");
    }
    
    public void alertListener(String info)
    {
        alertListener(true, info);
    }
    
    public void alertListener(boolean ready, String info)
    {
        System.out.println("ALERT - " + id + "\t" + (ready ? "true" : "false") + "\t" + info);
        listener.handle(id, ready, info);
    }
}