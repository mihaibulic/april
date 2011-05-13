package edu.umich.mihai.util;

import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;

public class PointDistortion
{
    private final int LENGTH_FC = 2;
    private final int LENGTH_CC = 2;
    private final int LENGTH_KC = 5;

    private int width;
    private int height;
    private double fc[];
    private double cc[];
    private double kc[];
    private double alpha;
    
    private double threshold;
    private Distort d;

    public PointDistortion(double fc[], double cc[], double kc[], double alpha, int width, int height, double threshold)
    {
        if(fc.length != LENGTH_FC) throw new ArrayIndexOutOfBoundsException("Focal length array contains " + fc.length + " elements (should have " + LENGTH_FC + ")");
        if(cc.length != LENGTH_CC) throw new ArrayIndexOutOfBoundsException("Principal point array contains " + cc.length + " elements (should have " + LENGTH_CC + ")");
        if(kc.length != LENGTH_KC) throw new ArrayIndexOutOfBoundsException("Distortion parameter array contains " + kc.length + " elements (should have " + LENGTH_KC + ")");

        this.fc = fc;
        this.cc = cc;
        this.kc = kc;
        this.alpha = alpha;
        this.width = width;
        this.height = height;
        this.threshold = threshold;
        d = new Distort();
    }
    
    class Distort extends Function
    {
        @Override
        public double[] evaluate(double[] p, double[] distorted)
        {
            if(distorted==null)
            {
                distorted = new double[p.length];
            }

            double centered[] = {(p[0]-cc[0])/fc[0],(p[1]-cc[1])/fc[1]};
            double r2 = centered[0]*centered[0] + centered[1]*centered[1];
            double scale = 1 + kc[0]*r2 + kc[1]*r2*r2 + kc[4]*r2*r2*r2;
            double scaled[] = {centered[0]*scale, centered[1]*scale};
            double tangential[] =
                        {2 * kc[2] * centered[0] * centered[1] +
                         kc[3] * (r2 + 2 * centered[0] * centered[0]),
                         kc[2] * (r2 + 2 * centered[1] * centered[1]) +
                         2 * kc[3] * centered[0] * centered[1]};

            scaled[0] = scaled[0]+tangential[0];
            scaled[1] = scaled[1]+tangential[1];
            
            distorted[0] = fc[0] * (scaled[0] + alpha * scaled[1]) + cc[0];
            distorted[1] = scaled[1] * fc[1] + cc[1];

            return distorted;
        }
    }
    
    public double[] undistort(double[] dp)
    {
        double udp[] = {376,240};
        double eps[] = {1,1};
        double r[] = LinAlg.subtract(dp, d.evaluate(udp));
        int count = 0;
        
        while((r[0]*r[0] > threshold || r[1]*r[1] > threshold) && ++count < 50)
        {
            // compute jacobian
            double _J[][] = NumericalJacobian.computeJacobian(d, udp, eps);
            Matrix J = new Matrix(_J);
          
            Matrix dx = J.solve(Matrix.columnMatrix(r));
            
            // adjust guess
            for (int i = 0; i < dx.getRowDimension(); i++) 
            {
                udp[i] += dx.get(i,0);
              
                // guard against spilling outside of image
                if(udp[i] < 0) udp[i] = 0;
            }
            // guard against spilling outside of image
            if(udp[0] > width-1) udp[0] = width-1;
            if(udp[1] > height-1) udp[1] = height-1;
            
            // compute residual
            r = LinAlg.subtract(dp, d.evaluate(udp));
        }
        
        return udp;
    }
    
    public double[] distort(double[] p)
    {
        double centered[] = {(p[0]-cc[0])/fc[0],(p[1]-cc[1])/fc[1]};
        double r2 = centered[0]*centered[0] + centered[1]*centered[1];
        double scale = 1 + kc[0]*r2 + kc[1]*r2*r2 + kc[4]*r2*r2*r2;
        double scaled[] = {centered[0]*scale, centered[1]*scale};
        double tangential[] =
                    {2 * kc[2] * centered[0] * centered[1] +
                     kc[3] * (r2 + 2 * centered[0] * centered[0]),
                     kc[2] * (r2 + 2 * centered[1] * centered[1]) +
                     2 * kc[3] * centered[0] * centered[1]};

        scaled[0] = scaled[0]+tangential[0];
        scaled[1] = scaled[1]+tangential[1];
        
        double result[] = {fc[0] * (scaled[0] + alpha * scaled[1]) + cc[0],
                           scaled[1] * fc[1] + cc[1]};

        return result;
    }
}
