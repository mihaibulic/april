package mihai.sandbox;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.JFrame;
import mihai.camera.Tag;
import mihai.util.PointLocator;
import mihai.vis.VisCamera;
import april.jmat.LinAlg;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisWorld;

public class TagPinPointTest extends JFrame
{
    private static final long serialVersionUID = 1L;
    Color[] colors = {Color.black, Color.red, Color.green, Color.blue, Color.gray, Color.cyan, Color.DARK_GRAY, Color.LIGHT_GRAY, Color.magenta, Color.orange, Color.pink, Color.YELLOW, Color.WHITE};
    
    public TagPinPointTest()
    {
        super("TPP");
        ArrayList<Tag> detections = new ArrayList<Tag>();
        Random rand = new Random();
        
        int length = 6;
        int size = 20;
        for(int x = 0; x < size; x++)
        {
            double[] tag = new double[length];
            double off = x/4;
            for(int b = 0; b < length; b++)
            {
                if(b < 3)
                    tag[b] = off+rand.nextGaussian()*0.05;
                else
                    tag[b] = off+rand.nextGaussian()*0.15;
            }
            detections.add(new Tag(tag, x/4));
        }
        
        int end = 0;
        ArrayList<Tag> locations = new ArrayList<Tag>();
        for(int start = 0; start < detections.size(); start=end)
        {
            int last_id = detections.get(start).getId();
            int id = last_id;
            
            while( last_id==id && ++end < detections.size())
            {
                id = detections.get(end).getId();
            }
            
            double[][] points = new double[end-start][6];
            for(int b = 0; b < points.length; b++)
            {
                points[b] = detections.get(start+b).getXyzrpy();
            }
            
            locations.add(new Tag(PointLocator.calculateItt(points), last_id)); 
        }
        
        VisWorld vw = new VisWorld();
        VisCanvas vc = new VisCanvas(vw);
        VisWorld.Buffer vb = vw.getBuffer("buff");
        
        add(vc);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000,1000);
        drawPoints(vb, detections, locations);
        setVisible(true);

    }
 
    private void drawPoints(VisWorld.Buffer vb, ArrayList<Tag> fin, ArrayList<Tag> f)
    {
        double size = 0.01;
        
        for(int x = 0; x < f.size(); x++)
        {
            vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(f.get(x).getXyzrpy()), new VisCamera(colors[f.get(x).getId()], size*2)));
        }
        
        for(int x = 0; x < fin.size(); x++)
        {
            vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(fin.get(x).getXyzrpy()),
                      new VisCamera(colors[fin.get(x).getId()], size)));
        }
        
        vb.switchBuffer();
    }
    
    public static void main(String[] args)
    {
        new TagPinPointTest();
    }

}
