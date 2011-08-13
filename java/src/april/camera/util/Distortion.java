package april.camera.util;

import aprilO.image.Homography33;
import aprilO.jmat.Function;
import aprilO.jmat.LinAlg;
import aprilO.jmat.Matrix;
import aprilO.jmat.NumericalJacobian;
import aprilO.tag.TagDetection;

public class Distortion
{
    private final int LENGTH_FC = 2;
    private final int LENGTH_CC = 2;
    private final int LENGTH_KC = 5;

    private double[][] p;
    private double fc[];
    private double cc[];
    private double kc[];
    private double alpha;
    
    private int width;
    private int height;
    
    private double threshold;
    private Distort d;

    public Distortion(double[] state, int width, int height)
    {
        this(new double[]{state[0], state[1]}, 
                new double[]{state[2], state[3]}, 
                new double[]{state[4],state[5],state[6],state[7],state[8]},
                state[9],width,height);
    }
    
    public Distortion(double fcx, double fcy, 
                      double ccx, double ccy, 
                      double kc0, double kc1, double kc2, double kc3, double kc4,
                      double alpha, int width, int height)
    {
        this(new double[]{fcx, fcy}, new double[]{ccx, ccy}, new double[]{kc0,kc1,kc2,kc3,kc4},alpha,width,height);
    }
    
    public Distortion(double fc[], double cc[], double kc[], double alpha, int width, int height)
    {
        this(fc,cc,kc,alpha,width,height,0.1);
    }
    
    public Distortion(double fc[], double cc[], double kc[], double alpha, int width, int height, double threshold)
    {
        if(fc.length != LENGTH_FC) throw new ArrayIndexOutOfBoundsException("Focal length array contains " + fc.length + " elements (should have " + LENGTH_FC + ")");
        if(cc.length != LENGTH_CC) throw new ArrayIndexOutOfBoundsException("Principal point array contains " + cc.length + " elements (should have " + LENGTH_CC + ")");
        if(kc.length != LENGTH_KC) throw new ArrayIndexOutOfBoundsException("Distortion parameter array contains " + kc.length + " elements (should have " + LENGTH_KC + ")");

        this.fc = fc;
        this.cc = cc;
        this.kc = kc;
        this.alpha = alpha;
        this.threshold = threshold;
        
        this.width = width;
        this.height = height;
        
        p = new double[width*height][2];
        
        for(int a = 0; a < width*height; a++)
        {
            p[a][0] = -1;
        }
        
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
    
    public double[] undistort(double x, double y)
    {
        return undistort(new double[] {x,y});
    }
    
    public double[] undistort(double[] dp)
    {
        double udp[] = {width/2,height/2};
        double eps[] = {1,1};
        double r[] = LinAlg.subtract(dp, d.evaluate(udp));
        int count = 0;
        
        while((r[0]*r[0] > threshold || r[1]*r[1] > threshold) && ++count < 100)
        {
            // compute jacobian
            double _J[][] = NumericalJacobian.computeJacobian(d, udp, eps);
            Matrix J = new Matrix(_J);
            Matrix dx = J.solve(Matrix.columnMatrix(r));
            
            // adjust guess
            for (int i = 0; i < dx.getRowDimension(); i++) 
            {
                udp[i] += dx.get(i,0);
            }

            // compute residual
            r = LinAlg.subtract(dp, d.evaluate(udp));
        }
        if(count>=100)System.out.print("*");
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
    
    public byte[] naiveBufferUndistort(byte[] buffer)
    {
        byte[] newBuffer = new byte[buffer.length];
        double px[] = new double[2];
        
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                px = distort(x, y);
                if(((int)px[0] + (int)px[1]*width) >= 0 && 
                        (int)px[0] + (int)px[1]*width < buffer.length)
                {
                    newBuffer[x+y*width] = buffer[(int)px[0] + (int)px[1]*width];
                }
            }
        }        

        return newBuffer;
    }
    
    public double[] distort(double x, double y)
    {
        double distorted[] = new double[2];
        int location = (int)(x+y*width);
        if(p[location][0] > -1)
        {
            distorted = p[location];
        }
        else
        {
            distorted = slowDistort(new double[] {x, y});
            p[location] = distorted;
        }
        
        return distorted;
    }
    
    private double[] slowDistort(double p[])
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
        
        double result[] = {fc[0] * (scaled[0] + alpha * scaled[1]) +
                           cc[0],
                           scaled[1] * fc[1] + cc[1]};

        return result;

    }
    
    public TagDetection tagUndistort(TagDetection tag)
    {
        TagDetection newTag = new TagDetection();

        newTag.code = tag.code;        
        newTag.good = tag.good;
        newTag.hammingDistance = tag.hammingDistance;
        newTag.id = tag.id;
        newTag.obsCode = tag.obsCode;
        newTag.observedPerimeter = tag.observedPerimeter;
        newTag.p = new double[4][];
        newTag.rotation = tag.rotation;

        double pt[][] = tag.p;
        for (int i = 0; i < 4; i++) 
        {
            pt[i] = undistort(pt[i]);
        }
        Quad newQuad = new Quad(pt);
        Quad quad = newQuad;
        
        for (int i = 0; i < 4; i++) 
        {
            newTag.p[(4+i-newTag.rotation)%4] = quad.p[i];
        }

        // compute the homography (and rotate it appropriately)
        newTag.homography = quad.homography.getH();
        newTag.hxy = quad.homography.getCXY();

        if (true) {
            double c = Math.cos(newTag.rotation*Math.PI/2.0);
            double s = Math.sin(newTag.rotation*Math.PI/2.0);
            double R[][] = new double[][] {{ c, -s, 0},
                                           { s,  c, 0},
                                           { 0,  0, 1} };
            newTag.homography = LinAlg.matrixAB(newTag.homography, R);
        }

        newTag.cxy = quad.interpolate01(.5, .5);
        
        return newTag;
    }
    
    class Quad
    {
        private double p[][] = new double[4][];
        private Homography33 homography;

        public Quad(double p[][])
        {
            this.p = p;

            homography = new Homography33(cc[0], cc[1]);
            homography.addCorrespondence(-1, -1, p[0][0], p[0][1]);
            homography.addCorrespondence( 1, -1, p[1][0], p[1][1]);
            homography.addCorrespondence( 1,  1, p[2][0], p[2][1]);
            homography.addCorrespondence(-1,  1, p[3][0], p[3][1]);
        }

        public double[] interpolate01(double x, double y)
        {
            return interpolate(2*x - 1, 2*y - 1);
        }

        public double[] interpolate(double x, double y)
        {
            return homography.project(x, y);
        }
    }
}
