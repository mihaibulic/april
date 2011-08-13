package april.vis;

import java.awt.Color;
import java.awt.Toolkit;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import aprilO.vis.VisCanvas;
import aprilO.vis.VisWorld;

public class VisGuiSkeleton extends JFrame
{
    private static final long serialVersionUID = 1L;

    public VisWorld vw = new VisWorld();
    public VisCanvas vc = new VisCanvas(vw);
    public VisWorld.Buffer vb = vw.getBuffer("buff");
    
    public VisGuiSkeleton()
    {
        this(-1,-1,1,1);
    }
    
    public VisGuiSkeleton(double x0, double y0, double x1, double y1)
    {
        getContentPane().setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        getContentPane().add(vc);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setVisible(true);
        
        vc.setBackground(Color.BLACK);
        vc.getViewManager().viewGoal.fit2D(new double[]{x0,y0}, new double[]{x1,y1});
    }
   
    public static void main(String[] args)
    {
        new VisGuiSkeleton();
    }
}
