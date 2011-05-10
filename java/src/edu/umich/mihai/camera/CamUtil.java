package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import april.config.Config;
import april.jcam.ImageConvert;

public class CamUtil 
{
    public static String getUrl(Config config, String url)
    {
        String prefix = config.requireString("default_url");
        
        if(url.contains(prefix))
        {
            url = url.substring(url.indexOf(prefix) + prefix.length());
        }

        return url;
    }
    
    public static BufferedImage undistortImage(BufferedImage rawImage, double[] fc, double[] cc, double[] kc, double alpha)
    {
        BufferedImage correctImage = rawImage;
        double px[] = new double[2];
        int height = rawImage.getHeight();
        int width = rawImage.getWidth();

        for(int y = 0; y < height; y++)
        {
            for(int x = 0; x < width; x++)
            {
                px = CamUtil.distort(new double[] {x, y}, fc, cc, kc, alpha);
                correctImage.setRGB(x, y, rawImage.getRGB((int)px[0], (int)px[1]));
            }
        }
        
        return correctImage;
    }
    
    public static BufferedImage undistortImage(byte[] rawBuffer, double[] fc, double[] cc, double[] kc, double alpha, int width, int height, String format)
    {
        byte correctBuffer[] = new byte[rawBuffer.length];
        
        double[] correctPixel = new double[2];
        for(int n = 0; n < rawBuffer.length; n++)
        {
            correctPixel = CamUtil.distort(new double[] {n%width, n/width}, fc, cc, kc, alpha);
            correctBuffer[n] = rawBuffer[(int)correctPixel[0] + (int)correctPixel[1]*width]; 
        }
        
        BufferedImage correctImage = ImageConvert.convertToImage(format, width, height, correctBuffer);

        return correctImage;
    }
	
	public static double[] distort(double p[], double fc[], double cc[], double kc[], double alpha)
    {
        final int LENGTH_FC = 2;
        final int LENGTH_CC = 2;
        final int LENGTH_KC = 5;

        if(fc.length != LENGTH_FC) throw new ArrayIndexOutOfBoundsException("Focal length array contains " + fc.length + " elements (should have " + LENGTH_FC + ")");
        if(cc.length != LENGTH_CC) throw new ArrayIndexOutOfBoundsException("Principal point array contains " + cc.length + " elements (should have " + LENGTH_CC + ")");
        if(kc.length != LENGTH_KC) throw new ArrayIndexOutOfBoundsException("Distortion parameter array contains " + kc.length + " elements (should have " + LENGTH_KC + ")");
        
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
