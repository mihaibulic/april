package april.util;

import java.util.ArrayList;
import aprilO.jmat.Function;
import aprilO.jmat.LinAlg;
import aprilO.jmat.Matrix;
import aprilO.jmat.NumericalJacobian;

public class PointUtil
{
    public static double[] getLocation(double[][][] transformations)
    {
        int length = 6;
        double[][] points = new double[transformations.length][length];
        
        for(int x = 0; x < transformations.length; x++)
        {
            points[x] = LinAlg.matrixToXyzrpy(transformations[x]);
        }
        
        return getLocation(points);
    }
    
    public static double[] getLocation(double[][] points)
    {
        int length = points[0].length;
        double threshold = 0.000001; // found experimentally 
        int ittLimit = 100;          // found experimentally 

        double location[] = getCentroid(points);

        Distance distance = new Distance(points);

        double[] eps = new double[length];
        for(int i = 0; i < eps.length; i++)
        {
            eps[i] = 0.0001;
        }
        
        int count = 0;
        double[] r = LinAlg.scale(distance.evaluate(location), -1);
        double[] oldR = null;
        while(!shouldStop(r, oldR, threshold) && ++count < ittLimit)
        {
            double[][] _J = NumericalJacobian.computeJacobian(distance, location, eps);
            Matrix J = new Matrix(_J);
            
            Matrix JTtimesJplusI = J.transpose().times(J).plus(Matrix.identity(length, length));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTtimesJplusI.solve(JTr);
            
            for(int i = 0; i < length; i++)
            {
                location[i] += 0.1*dx.get(i,0); // scaling by 0.1 helps keep results stable 
            }
            
            oldR = r.clone();
            r = LinAlg.scale(distance.evaluate(location), -1);
        }
        
        return location;
    }
    
    private static class Distance extends Function
    {
        double[][] points;
        
        public Distance(double[][] points)
        {
            this.points = points;
        }
        
        @Override
        public double[] evaluate(double[] location, double[] distance)
        {
            distance = new double[]{0};
            for(double[] p : points)
            {
                distance[0] += LinAlg.squaredDistance(p, location);
            }
            
            return distance;
        }
    }        
    
    private static boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        return (oldR != null && LinAlg.distance(r,oldR) < threshold);
    }
    
    private static double sq(double a)
    {
        return a*a;
    }

    public static double[] getCentroid(double[][][] matricies)
    {
        double[][] points = new double[matricies.length][6];
        
        for(int x = 0; x < matricies.length; x++)
        {
            points[x] = LinAlg.matrixToXyzrpy(matricies[x]);
        }
        
        return getCentroid(points);
    }
    
    public static double[] getCentroid(double[][] points)
    {
        int length = points[0].length;
        double average[] = new double[length];
               
        for(int b = 0; b < length; b++)
        {
            average[b] = 0;
            for(int a = 0; a < points.length; a++)
            {
                average[b] += points[a][b];
            }
            average[b] /= points.length;
        }
        
        return average;
    }
    
    /**
     * @param points
     * @return {px, py, t, e} where
     *      (px, py) = point on the fitted line
     *      t = theta value of the line
     *      e = error of fit
     */
    public static double[] fitLine(ArrayList<double[]> points)
    {
        double line[] = null;
        
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
            XXminusYY += (p[0]*p[0])-(p[1]*p[1]); 
            xy += p[0]*p[1]; // (x^2 - y^2)/xy
            points.set(i, p);
        }
        
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
            
            line = new double[]{ave[0], ave[1], roots[0], errors[0]/points.size()};
            if(errors[0] > errors[1])
            {
                line[2] = roots[1];
                line[3] = errors[1]/points.size();
            }
        }
        else // XXX must fix because this is not right
        {
            System.out.println("*");
            line = new double[]{ave[0], ave[1], 0, 0};
        }
        
        return line;
    }
    
    public static void print(double[] point)
    {
        for(double p : point)
        {
            System.out.print(p + "\t");
        }
        System.out.println("");
    }
}
