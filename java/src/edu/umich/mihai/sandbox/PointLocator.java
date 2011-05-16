package edu.umich.mihai.sandbox;

import java.awt.Color;
import java.util.Random;
import javax.swing.JFrame;
import edu.umich.mihai.vis.VisCamera;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisWorld;

public class PointLocator extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    private static final int MIN = 5;
    private static final int MAX = 25;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("buff");
    
    private static double[] total = {0,0};
    
    public PointLocator(double[][] points)
    {
        super("Point Locator");
        int length = points[0].length;
        double[] locationCentroid = calculateCentroid(points);
        double[] locationItt = calculateItt(points);
        
        double[] diff = LinAlg.subtract(printDistance(points, locationCentroid), printDistance(points, locationItt));  
        System.out.println(diff[0] + "\t" + diff[1] + (diff[0] > 0.05 ? "\t *********************" : diff[1] > 0.13 ? "\t ^^^^^^^^^^^^^^^^^" : ""));
        total = LinAlg.add(total, diff);

        if(diff[0] < 0 || diff[1] < 0)
        {
            add(vc);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1000,1000);
            drawPoints(points, locationCentroid, locationItt);
            setVisible(true);

            System.out.println("\n");
            for(int a = 0; a < points.length; a++)
                for(int b = 0; b < length; b++)
                    System.out.print(points[a][b] + "\t");
            System.out.println("---");
            
            for(int b = 0; b < length; b++)
                System.out.print(locationCentroid[b] + "\t");
            System.out.println("---");
            
            for(int b = 0; b < length; b++)
                System.out.print(locationItt[b] + "\t");
            System.out.println();
            
            throw new RuntimeException("centroid better then itt!");
        }
        
        return;
    }
    
    private double[] printDistance(double[][] points, double[] location)
    {
        double[][] distance = new double[points.length][2];
        double[] total = {0,0};
        
        for(int y = 0; y < points.length; y++)
        {
            distance[y][0] = LinAlg.distance(points[y], location, 3);
            distance[y][1] = angleDiff(points[y], location);
            total = LinAlg.add(total, distance[y]);
        }
        System.out.print(total[0] + "\t" + total[1] + "\t") ;
        
        return total;
    }
    
    static class Distance extends Function
    {
        double[][] points;
        int length;
        
        public Distance(double[][] points)
        {
            this.points = points;
            length = points[0].length;
        }
        
        @Override
        public double[] evaluate(double[] location, double[] distance)
        {
            if(distance == null)
            {
                distance = new double[length];
                for(int a = 0; a < length; a++)
                {
                    distance[a] = 0;
                }
            }
            
            for(int b = 0; b < points.length; b++)
            {
                double d = LinAlg.distance(points[b], location, 3 );
                double t = angleDiff(points[b], location);
                for(int a = 0; a < length; a++)
                {
                    distance[a] += (a<3 ? d : t);
                }
            }
            distance = LinAlg.scale(distance, 1./points.length);

            return distance;
        }
    }        
    
    private static double angleDiff(double[] a, double[] b)
    {
        double diff = 0;
        
        for(int x = 3; x < a.length; x++)
        {
            diff += Math.pow(a[x]-b[x],2);
        }
        
        return Math.sqrt(diff);
    }
    
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
    
    public static double[] calculateItt(double[][] points)
    {
        Distance d = new Distance(points);
        int length = points[0].length;
        
        double loction[] = calculateCentroid(points);
        
        boolean skip[] = new boolean[length];
        for(int a = 0; a < skip.length; a++)
        {
            skip[a] = false;
        }
        
        double delta = 0.0001;
        double[] eps = new double[length];
        for(int i = 0; i < eps.length; i++)
        {
            eps[i] = delta;
        }
        
        double J[][] = NumericalJacobian.computeJacobian(d, loction, eps);
        double oldJ[][] = J.clone();
        double oldOldJ[][] = J.clone();
        
        while(!shouldStop(skip))
        {
            J = NumericalJacobian.computeJacobian(d, loction, eps);
            
            for(int a = 0; a < length; a++)
            {
                if(!skip[a])
                {
                    if(Math.abs(J[a][a]) < 0.01 || (oldJ[a][a]*J[a][a] < 0 && oldJ[a][a]*oldOldJ[a][a] < 0))
                    {
                        skip[a] = true;
                    }
                    else
                    {
                        loction[a] -= 0.01*J[a][a];
                    }
                }
            }
            
            oldOldJ = oldJ.clone();
            oldJ = J.clone();
        }
        
        return loction;
    }
    
    private static boolean shouldStop(boolean[] skip)
    {
        boolean stop = true;
        
        for(int a = 0; a < skip.length; a++)
        {
            if(!skip[a])
            {
                stop = false;
                break;
            }
        }
            
        return stop;
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

    private void drawPoints(double[][] points, double[] locationCentroid, double[] locationItt)
    {
        double size = 0.01;
        
        for(double[] p : points)
        {
            vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(p),
                      new VisCamera(Color.black, size)));
        }
        
        vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(locationCentroid),
                new VisCamera(Color.GREEN, size)));

        vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(locationItt),
                new VisCamera(Color.BLUE, size)));
        
        vb.switchBuffer();
    }
    
    public static void main(String[] args)
    {
        int ran = 1000;
        int length = 6;
        for(int x = 0; x < ran; x++)
        {
            Random rand = new Random();
            int size = rand.nextInt(MAX-MIN)+MIN;
            double[][] points = new double[size][length];
            for(int a = 0; a < size; a++)
            {
                for(int b = 0; b < length; b++)
                  {
                    if(b < 3)
                        points[a][b] = rand.nextGaussian()*0.10;
                    else
                        points[a][b] = rand.nextGaussian()*0.33;
                  }
            }
            System.out.print(size + "\t");
            new PointLocator(points);
        }
        
        System.out.println("\n\n\n-----\ntotal translation (inches):" + ((100*(total[0])))/2.54 + "\tave (inches)" + ((100*(total[0]/ran)))/2.54);
        System.out.println("total rotation (deg):" + (total[1])*(180/Math.PI) + "\tave (deg)" + (total[1]/ran)*(180/Math.PI));
        
        System.exit(0);
//        double[][] points = {{1,1,0,-.01,0,0},{1,2,0.1,0,0.15,0},{1,3,0,-.03,0,-.1},{6,2,0,0.05,0,0.09}};
//        double[][] points = {{0,2,0,0,0,0},{1,3,0,0,0,0},{2,4,0,0,0,0},{3,0,0,0,0,0}};
//        new PointLocator(points);
    }

}
