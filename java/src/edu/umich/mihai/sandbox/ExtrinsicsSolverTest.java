package edu.umich.mihai.sandbox;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import javax.swing.JFrame;
import edu.umich.mihai.camera.Camera;
import edu.umich.mihai.camera.Tag;
import edu.umich.mihai.util.PointLocator.Distance;
import edu.umich.mihai.vis.VisCamera;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

public class ExtrinsicsSolverTest extends JFrame
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("a");
    
    private Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
    
    public ExtrinsicsSolverTest(Camera[] cameras)
    {
        super("EST");
        
        
        
        
        
        
        
        
        
        
        
        for(int x = 0; x < cameras.length; x++)
        {
            vb.addBuffered(new VisChain(cameras[x].getTransformationMatrix(), new VisCamera(colors[x], 0.08)));
            
            for(Tag tag : cameras[x].getTags())
            {
                vb.addBuffered(new VisChain(cameras[x].getTransformationMatrix(), tag.getTransformationMatrix(), new VisRectangle(0.15, 0.15, new VisDataFillStyle(colors[x]))));
            }
        }
        
        showGui();
    }
    
    private void showGui()
    {
        add(vc);
        vb.switchBuffer();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setVisible(true);
    }
    
    public static void main(String[] args)
    {
        double[][] camInit = {{.1,0,-.2,0,.3,0}, {0.2,1.1,-0.2,.3,0,.1}, {0.8,.1,-.05,.1,0.1,-0.1}};
        
        double[][] camAct = {{0,0,0,0,0,0}, {0,1,0,0,0,0}, {1,0,0,0,0,0}};
        double[][] tagsAct = {{0,0,-2,0,0,0}, {0,1,-2,0,0,0}, {1,0,-2,0,0,0}, {1,1,-2,0,0,0}, {-1,0,-2,0,0,0}, 
                              {0,-1,-2,0,0,0}, {-1,-1,-2,0,0,0}, {0.5,0.5,-2,0,0,0}, {-0.5,-0.5,-2,0,0,0}};
        
        int numOfCams = camAct.length;
        int numOfTags = tagsAct.length;
        
        Camera[] cameras = new Camera[numOfCams];
        ArrayList<Tag>[] tags = new ArrayList[numOfCams];
        
        for(int x = 0; x < numOfCams; x++)
        {
            tags[x] = new ArrayList<Tag>();
            
            for(int y = 0; y < numOfTags; y++)
            {
                Tag tag = new Tag(LinAlg.subtract(tagsAct[y], camAct[x]), y);
                tags[x].add(tag);
            }
            cameras[x] = new Camera(tags[x], camInit[x]);
        }
        
        new ExtrinsicsSolverTest(cameras);
        
    }

}
