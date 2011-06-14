package mihai.sandbox;

import java.util.ArrayList;
import april.jmat.LinAlg;

public class LineFitTest
{
    public static void main(String[] args)
    {
        ArrayList<double[]> line = new ArrayList<double[]>(5);

        double[][] corners = {{100,100},{100,200},{100,300},{100,400},{100,500}};
        
        line.add(corners[0]);
        line.add(corners[1]);
        line.add(corners[2]);
        line.add(corners[3]);
        line.add(corners[4]);
        
        double result = fitLine(line);
        
        System.out.println(result);
    }

    public static double fitLine(ArrayList<double[]> points)
    {
        ArrayList<double[]> x = new ArrayList<double[]>(points.size());
        ArrayList<double[]> y = new ArrayList<double[]>(points.size());
        
        for(int i = 0; i < points.size(); i++)
        {
            x.add(new double[]{i, points.get(i)[0]});
            y.add(new double[]{i, points.get(i)[1]});
        }
        
        return (LinAlg.fitLine(x)[2] + LinAlg.fitLine(y)[2])/2;
    }
    
}
