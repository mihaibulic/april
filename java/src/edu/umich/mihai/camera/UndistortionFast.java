package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;

/**
 * @author Mihai Bulic
 * @deprecated - Unsafe to undistort images this way because it causes aliasing in the image and that segmentation/object detection unstable and unreliable
 */
public class UndistortionFast
{
    final int LENGTH_FC = 2;
    final int LENGTH_CC = 2;
    final int LENGTH_KC = 5;

    private double[][] p;
    private int width;
    private int height;
    private double fc[];
    private double cc[];
    private double kc[];
    private double alpha;
    
    public UndistortionFast(double fc[], double cc[], double kc[], double alpha, int width, int height)
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

        p = new double[width*height][2];
        
        for(int a = 0; a < width*height; a++)
        {
            p[a][0] = -1;
        }
    }
    
    public BufferedImage undistortImage(BufferedImage image)
    {
        BufferedImage correctImage = image;
        
        double px[] = new double[2];
        int color;
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                px = distort(x, y);
                color = image.getRGB((int)px[0], (int)px[1]);
                correctImage.setRGB(x, y, color); 
            }
        }
        
        return correctImage;
    }
   
    public byte[] undistortBuffer(byte[] buffer)
    {
        byte[] newBuffer = new byte[buffer.length];
        double px[] = new double[2];
        
        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                px = distort(x, y);
                newBuffer[x+y*width] = buffer[(int)px[0] + (int)px[1]*width]; 
            }
        }        

        return newBuffer;
    }
    
    public double[] distort(int x, int y)
    {
        double distorted[] = new double[2];
        int location = x+y*width;
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
}
