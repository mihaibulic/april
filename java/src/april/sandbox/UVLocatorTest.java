package april.sandbox;

import java.awt.Color;
import java.util.Random;
import april.util.PointUtil;
import april.vis.VisGuiSkeleton;
import april.jmat.LinAlg;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisDataFillStyle;

public class UVLocatorTest extends VisGuiSkeleton
{
    private static final long serialVersionUID = 1L;

    public UVLocatorTest(int u, int v, int spread)
    {
        super(0,0,752,480);
        
        Random rand = new Random();
        double[][] points = new double[10][2]; 
        for(int x = 0; x < points.length; x++)
        {
            points[x] = new double[]{u+spread*rand.nextGaussian(), v+spread*rand.nextGaussian()};
            vb.addBuffered(new VisChain(LinAlg.translate(points[x][0], points[x][1]), 
                    new VisCircle(1, new VisDataFillStyle(Color.red))));
        }
        double[] p = PointUtil.getLocation(points);
        System.out.println("*"+p[0] + "\t" + p[1]);
        vb.addBuffered(new VisChain(LinAlg.translate(p[0], p[1]), 
                new VisCircle(1, new VisDataFillStyle(Color.blue))));
        
        vb.switchBuffer();
    }
    
    public static void main(String[] args)
    {
        new UVLocatorTest(Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
    }

}
