package mihai.util;

import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;

public class PointLocator
{
    public static double[] calculateItt(double[][][] transformations)
    {
        int length = 6;
        double[][] points = new double[transformations.length][length];
        
        for(int x = 0; x < transformations.length; x++)
        {
            points[x] = LinAlg.matrixToXyzrpy(transformations[x]);
        }
        
        return calculateItt(points);
    }
    
    /**
     * 
     * @param points
     * @return
     */
    
    public static double[] calculateItt(double[][] points)
    {
        int length = points[0].length;
        double threshold = 0.000001; // found experimentally 
        int ittLimit = 100;          // found experimentally 

        double location[] = calculateCentroid(points);

        Distance distance = new Distance(points);

        double[] eps = new double[length];
        boolean skip[] = new boolean[length];
        for(int i = 0; i < eps.length; i++)
        {
            eps[i] = 0.0001;
            skip[i] = false;
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
            if(distance == null)
            {
                distance = new double[2];
                for(int a = 0; a < distance.length; a++)
                {
                    distance[a] = 0;
                }
            }
            
            for(int b = 0; b < points.length; b++)
            {
                distance[0] += Math.sqrt(sq(points[b][0]-location[0])+sq(points[b][1]-location[1])+sq(points[b][2]-location[2]));
                distance[1] += Math.sqrt(sq(points[b][3]-location[3])+sq(points[b][4]-location[4])+sq(points[b][5]-location[5]));
            }
            distance = LinAlg.scale(distance, 1./points.length);

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

    public static double[] calculateCentroid(double[][] points)
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
}
