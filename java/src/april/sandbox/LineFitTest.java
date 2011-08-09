package april.sandbox;

import java.util.ArrayList;
import april.jmat.LinAlg;

public class LineFitTest
{
    public static void main(String[] args)
    {
        ArrayList<double[]> line = new ArrayList<double[]>(5);

        double[][] corners = {{-7,-1},{-5,-5},{7,1},{5,5}};

        for(double[] corner : corners)
        {
            line.add(corner);
        }
        
        System.out.println(fitLine(line));
    }

    public static double fitLine(ArrayList<double[]> points)
    {
        double ave[] = {0,0};
        for(double[] p : points)
        {
            ave = LinAlg.add(p, ave);
        }
        ave = LinAlg.scale(ave, 1.0/points.size());
        
        double XXminusYY = 0;
        double xy = 0;
        for(int i = 0; i < points.size(); i++)
        {
            double p[] = LinAlg.subtract(points.get(i), ave);
            System.out.println(p[0] + "\t" + p[1] + "\t" + xy);
            XXminusYY += (p[0]*p[0])-(p[1]*p[1]); 
            xy += p[0]*p[1]; // (x^2 - y^2)/xy
            points.set(i, p);
        }
        
        double error = 0;
        if(xy != 0)
        {
            double a = XXminusYY/xy;
            
            double roots[] = { Math.atan((-a + Math.sqrt(4+sq(a)))/2), 
                               Math.atan((-a - Math.sqrt(4+sq(a)))/2) };
    
            double errors[] = {0,0};
            for(int i = 0; i < roots.length; i++)
            {
                for(double[] p: points)
                {
                    //y'  =  -x sin(q) + y cos(q)
                    errors[i] += sq(-p[0]*Math.sin(roots[i]) + p[1]*Math.cos(roots[i]));  
                }
            }
            
            System.out.println("angle: " + 180.*((errors[0] < errors[1]) ? roots[0] : roots[1])/Math.PI);
            error = Math.min(errors[0], errors[1]);
        }
        else // special case: all points have either the same x or the same y values (clearly this means the error of the line is 0)
        {
            error = 0;
        }
        
        return error;
    }
    
    private static double sq(double a)
    {
        return a*a;
    }
    
}
