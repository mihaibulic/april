package mihai.sandbox;

import java.awt.Color;
import java.util.Random;
import javax.swing.JFrame;
import mihai.vis.VisCamera;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisWorld;

public class PointLocatorTest extends JFrame
{
    private static final long serialVersionUID = 1L;
    
    private static double[] total = {0,0};
    
    private PointLocatorTest(double[][] points)
    {
        super("Point Locator");
        int length = points[0].length;
        double[] locationCentroid = calculateCentroid(points);
        double[] locationItt = calculateItt(points);
        
        double[] diff = LinAlg.subtract(getDiffernce(points, locationCentroid), getDiffernce(points, locationItt));  
        System.out.println(diff[0] + "\t" + diff[1] + (diff[0] > 0.05 ? "\t *********************" : diff[1] > 0.13 ? "\t ^^^^^^^^^^^^^^^^^" : ""));
        total = LinAlg.add(total, diff);

//        if(diff[0] < 0 || diff[1] < 0)
//        {
            VisWorld vw = new VisWorld();
            VisCanvas vc = new VisCanvas(vw);
            VisWorld.Buffer vb = vw.getBuffer("buff");
            
            add(vc);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(1000,1000);
            drawPoints(vb, points, locationCentroid, locationItt);
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

//            throw new RuntimeException("centroid better then itt!");
//        }
        
//        return;
    }
    
    private static double[] getDiffernce(double[][] points, double[] location)
    {
        double[][] distance = new double[points.length][2];
        double[] total = {0,0};
        
        for(int y = 0; y < points.length; y++)
        {
            distance[y][0] = LinAlg.distance(points[y], location, 3);
            distance[y][1] = getRotationalDifference(points[y], location);
            total = LinAlg.add(total, distance[y]);
        }
        
        return total;
    }
    
    private static double getRotationalDifference(double[] a, double[] b)
    {
        double diff = 0;
        
        for(int x = 3; x < a.length; x++)
        {
            diff += Math.pow(a[x]-b[x],2);
        }
        
        return Math.sqrt(diff);
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

    private void drawPoints(VisWorld.Buffer vb, double[][] points, double[] locationCentroid, double[] locationItt)
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
//        int run = 1000;
//        int min = 5;
//        int max = 25;
//        int length = 6;
//        
//        for(int x = 0; x < run; x++)
//        {
//            Random rand = new Random();
//            int size = rand.nextInt(max-min)+min;
//            double[][] points = new double[size][length];
//            for(int a = 0; a < size; a++)
//            {
//                for(int b = 0; b < length; b++)
//                {
//                    if(b < 3)
//                        points[a][b] = rand.nextGaussian()*0.10;
//                    else
//                        points[a][b] = rand.nextGaussian()*0.33;
//                }
//            }
//            new PointLocatorTest(points);
//        }
//        
//        System.out.println("\n\n\n"+((100*(total[0]/run)))/2.54 + "\t" + (total[1]/run)*(180/Math.PI));
        
//        System.exit(0);
        double[][] points = {{1,1,0,-.01,0,0},{1,2,0.1,0,0.15,0},{1,3,0,-.03,0,-.1},{6,2,0,0.05,0,0.09}};
//        double[][] points = {{1,1,0,0,0,0},{1,2,0,0,0,0},{1,3,0,0,0,0},{6,2,0,0,0,0}};
//        double[][] points = {{0,2,0,0,0,0},{1,3,0,0,0,0},{2,4,0,0,0,0},{3,0,0,0,0,0}};
        new PointLocatorTest(points);
    }

}
