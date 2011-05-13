package edu.umich.mihai.sandbox;

import java.awt.Color;

import javax.swing.JFrame;

import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisSphere;
import april.vis.VisWorld;

public class PointLocator extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    private static final int SIZE = 20;
    private static final int LENGTH = 6;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("buff");
    
    public PointLocator(double[][] points)
    {
        super("Point Locator");
        if(points[0].length != LENGTH) throw new RuntimeException("points need to have 6 elements (xyzrpy)");
     
        add(vc);
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000,1000);
        setVisible(true);

        System.out.println("Potential locations: ");
        for(double[] a : points)
        {
            for(double b : a)
                System.out.print(b + "\t");
            System.out.println();    
        }
        System.out.println("---");
        
        double[] locationCentroid = calculateCentroid(points);
        double[] locationItt = calculateItt(points, 0.1);
        
        System.out.println("Final location (centroid): ");
        for(double a : locationCentroid)
            System.out.print(a + "\t");
        System.out.println();
        
        System.out.println("Final location (itt): ");
        for(double a : locationItt)
            System.out.print(a + "\t");
        System.out.println();
        
        System.out.println("CENTROID:");
        getDistance(points, locationCentroid);
        System.out.println("\n\nITT:");
        getDistance(points, locationItt);
        
        drawPoints(points, locationCentroid, locationItt);
    }
    
    private double[] getDistance(double[][] points, double[] location)
    {
        double[] distance = new double[points.length];
        double total = 0;
        
        for(int y = 0; y < points.length; y++)
        {
            distance[y] = Math.sqrt(LinAlg.squaredDistance(points[y], location, 3));
            total += distance[y];
            System.out.println(y + "\t" + distance[y]);
        }
        System.out.println("total: " + total);
        
        return distance;
    }
    
    class Distance extends Function
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
                distance = new double[LENGTH];
                for(int a = 0; a < LENGTH; a++)
                    distance[a] = 0;
            }
            
            for(int a = 0; a < LENGTH; a++)
            {
                for(int b = 0; b < points.length; b++)
                {
                    distance[a] += Math.pow(location[a]-points[b][a],2);
//                    distance[a] += Math.abs(points[b][a]-location[a]);
                }
            }

            return distance;
        }
    }
    
    private boolean shouldStop(double[][] points, double current[], double old[])
    {
        double diff = 0;
     
        for(int a = 0; a < points.length; a++)
        {
            diff += LinAlg.distance(current, points[a]) -
                    LinAlg.distance(old, points[a]);
        }
        
        return diff < 0;
    }
    
    private double[] calculateItt(double[][] points, double threshold)
    {
        double delta = 1;
        double[] eps = new double[LENGTH];
        for(int i = 0; i < eps.length; i++)
            eps[i] = delta;
        
        Distance d = new Distance(points);
        double[] current = calculateCentroid(points);//LinAlg.add(old, eps);
        double old[] = LinAlg.subtract(current,eps);
        
        double r[] = LinAlg.scale(d.evaluate(old), d.evaluate(current));
        
        int count = 0;
        do
        {
            count++;

            // compute jacobian
            double _J[][] = NumericalJacobian.computeJacobian(d, current, eps);
            Matrix J = new Matrix(_J);

          
            System.out.println("\nResid");
            for(int a = 0; a < r.length; a++)
                System.out.print(r[a] + "\t");
            System.out.println();
            
            // least squares step
            Matrix JTJ = J.transpose().times(J);
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTJ.solve(JTr);
            
            // adjust guess
            System.out.println("\nDx");
            for (int i = 0; i < dx.getRowDimension(); i++) 
            {
                old[i] = current[i];
                System.out.print(dx.get(i,0) + "\t");
                current[i] += dx.get(i,0);
            }
            
            // compute residual
            r = LinAlg.subtract(d.evaluate(old), d.evaluate(current));
        }while(count < 1000);
//        }while(!shouldStop(points, current, old) || count == 1);
        
        System.out.println(count + "*************");
        
        return old;
    }
    
    private double[] calculateCentroid(double[][] points)
    {
        if(points[0].length != LENGTH) throw new RuntimeException("points need to have 6 elements (xyzrpy)");
        
        double average[] = new double[LENGTH];
               
        for(int b = 0; b < LENGTH; b++)
            average[b] = 0;
          
        for(int b = 0; b < LENGTH; b++)
        {
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
        double size = 0.05;
        
        for(double[] p : points)
        {
            vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(p),
                      new VisSphere(size, Color.black)));
        }
        
        vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(locationCentroid),
                new VisSphere(2*size, Color.GREEN)));

        vb.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(locationItt),
                new VisSphere(2*size, Color.BLUE)));
        
        vb.switchBuffer();
    }
    
    public static void main(String[] args)
    {
//        double[][] points = new double[SIZE][LENGTH];
//        Random rand = new Random();
//        
//        for(int a = 0; a < SIZE; a++)
//            for(int b = 0; b < LENGTH; b++)
//                points[a][b] = rand.nextGaussian()+5;
//        
      
        double[][] points = {{1,3,0,0,0,0},{1,2,0,0,0,0},{1,1,0,0,0,0},{6,2,0,0,0,0}};
        
        new PointLocator(points);
    }

}
