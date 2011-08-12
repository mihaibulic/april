package april.sandbox;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JFrame;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.lcm.image_t_util;
import april.lcmtypes.image_t;
import april.vis.VisCanvas;
import april.vis.VisImage;
import april.vis.VisWorld;

public class PandaViewer implements LCMSubscriber
{
    static LCM lcm = LCM.getSingleton();

    private boolean run = true;
    
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    
    private Object lock = new Object();
    private boolean ready = false;
    private BufferedImage im;
    
    public PandaViewer() throws Exception
    {
        lcm.subscribeAll(this);
        showGUI();
        run();
    }
    
    public void showGUI()
    {
        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1000, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        vc.getViewManager().viewGoal.fit2D(new double[]{0,0}, new double[]{752,480});
    }
    
    public void run()
    {
        while(run)
        {
            synchronized(lock)
            {
                while(!ready)
                {
                    try
                    {
                        lock.wait();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
                vbImage.addBuffered(new VisImage(im));
            }
            vbImage.switchBuffer();
        }
    }
    
    public static void main(String[] args) throws Exception
    {
        new PandaViewer();
    }

    public void kill()
    {
        run = false;
    }

    public void messageReceived(LCM arg0, String channel, LCMDataInputStream ins)
    {
        if(channel.equals("pandaOut"))
        {
            System.out.println("*");
            try
            {
                synchronized(lock)
                {
                    im = image_t_util.decode(new image_t(ins));
                    ready = true;
                    lock.notify();
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            }
            
        }
        
    }
}
