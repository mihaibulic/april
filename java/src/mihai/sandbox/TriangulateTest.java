package mihai.sandbox;

import java.awt.Color;
import java.util.ArrayList;
import javax.swing.JFrame;
import mihai.tracker.ImageObjectDetection;
import mihai.tracker.SpaceObjectDetection;
import mihai.vis.VisCamera;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisData;
import april.vis.VisDataFillStyle;
import april.vis.VisDataLineStyle;
import april.vis.VisSphere;
import april.vis.VisWorld;

public class TriangulateTest extends JFrame
{
    private static final long serialVersionUID = 1L;

    public TriangulateTest()
    {
        super("triangulate test");
        
        ArrayList<ImageObjectDetection> objs = new ArrayList<ImageObjectDetection>();
        
        //int id, long timeStamp, double[] uv, double[] fc, double[] cc
        
        int id = 0;
        long t = System.currentTimeMillis();
        double[] fc = {477,477};
        double[] cc = {752/2,480/2};

        double[] uv1 = {376, 240};
        double[] uv2 = {376, 240};
        double[] uv3 = {376, 240};
        double[] uv4 = {376, 240};
        
        ImageObjectDetection o1 = new ImageObjectDetection(id, t, uv1, fc, cc);
        ImageObjectDetection o2 = new ImageObjectDetection(id, t, uv2, fc, cc);
        ImageObjectDetection o3 = new ImageObjectDetection(id, t, uv3, fc, cc);
        ImageObjectDetection o4 = new ImageObjectDetection(id, t, uv4, fc, cc);

        double[] xyz1 = new double[]{0,0,0,0,Math.PI,0};
        double[] xyz2 = new double[]{0,0,4,0,0,0};
        double[] xyz3 = new double[]{-2,0,2,0,-Math.PI/2,0};
        double[] xyz4 = new double[]{2,0,2,0,Math.PI/2,0};
        
        double[][] t1 = LinAlg.xyzrpyToMatrix(xyz1);
        double[][] t2 = LinAlg.xyzrpyToMatrix(xyz2);
        double[][] t3 = LinAlg.xyzrpyToMatrix(xyz3);
        double[][] t4 = LinAlg.xyzrpyToMatrix(xyz4);
        
        o1.transformation = t1;
        o2.transformation = t2;
        o3.transformation = t3;
        o4.transformation = t4;
        
        objs.add(o1);
        objs.add(o2);
        objs.add(o3);
        objs.add(o4);
        
        VisWorld vw = new VisWorld();
        VisCanvas vc = new VisCanvas(vw);
        VisWorld.Buffer vb = vw.getBuffer("buff");
        
        add(vc);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1000,1000);
        
        vb.addBuffered(new VisChain(t1, new VisCamera(Color.black, 0.08)));
        vb.addBuffered(new VisChain(t2, new VisCamera(Color.red, 0.08)));
        vb.addBuffered(new VisChain(t3, new VisCamera(Color.green, 0.08)));
        vb.addBuffered(new VisChain(t4, new VisCamera(Color.blue, 0.08)));

        ArrayList<double[]> ray1 = new ArrayList<double[]>();
        ArrayList<double[]> ray2 = new ArrayList<double[]>();
        ArrayList<double[]> ray3 = new ArrayList<double[]>();
        ArrayList<double[]> ray4 = new ArrayList<double[]>();
        
        ray1.add(xyz1);
        ray2.add(xyz2);
        ray3.add(xyz3);
        ray4.add(xyz4);
        
        ray1.add(new double[]{0,0,2,0,0,0});
        ray2.add(new double[]{0,0,2,0,0,0});
        ray3.add(new double[]{0,0,2,0,0,0});
        ray4.add(new double[]{0,0,2,0,0,0});
        
        vb.addBuffered(new VisData(ray1, new VisDataLineStyle(Color.green, 2)));
        vb.addBuffered(new VisData(ray2, new VisDataLineStyle(Color.green, 2)));
        vb.addBuffered(new VisData(ray3, new VisDataLineStyle(Color.green, 2)));
        vb.addBuffered(new VisData(ray4, new VisDataLineStyle(Color.green, 2)));
        setVisible(true);
        
        SpaceObjectDetection track = triangulate(objs);
        
        vb.addBuffered(new VisChain(track.transformation, new VisSphere(0.03, new VisDataFillStyle(Color.magenta))));
        vb.switchBuffer();
        
        System.out.println(track.xyzrpy[0] + "\t" + track.xyzrpy[1] + "\t" + track.xyzrpy[2]);
    }
    
    class Distance extends Function
    {
        ArrayList<ImageObjectDetection> objects;
        int length = 1;
        
        public Distance(ArrayList<ImageObjectDetection> objects)
        {
            this.objects = objects;
        }
        
        public double[] evaluate(double[] point)
        {
            return evaluate(point, null);
        }
        
        public double[] evaluate(double[] point, double[] distances)
        {
            if(distances == null)
            {
                distances = new double[objects.size()*length];
                for(int x = 0; x < distances.length; x++)
                {
                    distances[x] = 0;
                }
            }

            for(int a = 0; a < objects.size(); a++)
            {
                ImageObjectDetection object = objects.get(a);

                double theta = -1*Math.atan((object.uv[0]-object.cc[0])/object.fc[0]);
                double phi = -1*Math.atan((object.uv[1]-object.cc[1])/object.fc[1]);
                double[][] M = LinAlg.matrixAB(LinAlg.matrixAB(object.transformation, LinAlg.rotateY(theta)), LinAlg.rotateX(phi));
                double[] rayEndPoint = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(new double[]{0,0,100})));
                double[] rayStartPoint = LinAlg.matrixToXyzrpy(M);
                System.out.println(theta  + "\t..." + phi);
                double[] pointLine1 = LinAlg.copy(LinAlg.subtract(point, rayStartPoint), 3);
                double[] pointLine2 = LinAlg.copy(LinAlg.subtract(point, rayEndPoint),3);
                double[] rayVector = LinAlg.copy(LinAlg.subtract(rayEndPoint, rayStartPoint),3);
                distances[a*length] = LinAlg.magnitude(LinAlg.crossProduct(pointLine1, pointLine2))/LinAlg.magnitude(rayVector);
                
//                double[] v = LinAlg.copy(LinAlg.subtract(rayPoint, startPoint),3);
//                double[] w = LinAlg.copy(LinAlg.subtract(point, startPoint),3);
//                double z =  LinAlg.dotProduct(w,v) / LinAlg.dotProduct(v,v);
                    
//                double[] endPoint = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(M, LinAlg.translate(new double[]{0, 0, z})));

//                double pointToRayDistance = LinAlg.distance(endPoint, point,3);
//                double pxToProjPt = Math.sqrt(sq(object.fc[0])+sq(object.uv[0]));
//                double pixelSize = z*pxToProjPt*((1/object.uv[0])-(1/(object.uv[0]+1))); 
//                double slope =   
//                double distance = pointToRayDistance - Math.sqrt(sq(pixelSize) + sq(pixelSize/slope))/2;
            }
            
            return distances;
        }

        /**
         * @param c - center point (xyz of camera)
         * @param p0 - one of the two corners of the pixels 
         * @param p1 - one of the two corners of the pixels 
         * @param q - point in question 
         * @return
         */
        private boolean isBelow(double[] c, double[] p0, double[] p1, double[] q)
        {
            // calculates vector from c to p0, and c to p1
            // then calculates the cross product of those vectors (which is the normal vector of the plane formed by c, p0, and p1
            // if the dot product of the vector from c to p and the normal vector is > 0, the angle between them is < 90 degrees
            // which means they are on the same side.
            
            return LinAlg.dotProduct(LinAlg.crossProduct(
                                        LinAlg.subtract(p0, c), LinAlg.subtract(p1, c)), 
                                     LinAlg.subtract(q, c)) > 0;
        }
        
        private double sq(double a)
        {
            return a*a;
        }
        
    }
    
    private SpaceObjectDetection triangulate(ArrayList<ImageObjectDetection> objectDetections)
    {
        if(objectDetections.size() < 2) return new SpaceObjectDetection(true);
        
        int length = 6;
        double threshold = 0.000001;
        int ittLimit = 100;

        double location[] = {0,0,0,0,0,0};// = calculateCentroid(points);
        
        Distance distance = new Distance(objectDetections);
        
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
            System.out.println(r[0] + "\t" + r[1] + "\t" + r[2] + "\t" + r[3]);
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
        
        System.out.println(count);
        
        // FIXME (timeStamp should be an average or something)
        return new SpaceObjectDetection(objectDetections.get(0).id, objectDetections.get(0).timeStamp, location);
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        return (oldR != null && LinAlg.distance(r,oldR, 3) < threshold);
    }
    
    public static void main(String[] args)
    {
        new TriangulateTest();
    }

}
