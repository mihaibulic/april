package edu.umich.mihai.camera;

import april.config.Config;
import april.jmat.LinAlg;

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
    
	public static double[] undistort(double pixel[], double fc[], double cc[], double kc[], double alpha)
    {
	    final int LENGTH_FC = 2;
        final int LENGTH_CC = 2;
        final int LENGTH_KC = 5;
        
        // indices for lookup in kc[]
        final int KC1 = 0;//^2
        final int KC2 = 1;//^4
        final int KC3 = 2;//^tangential
        final int KC4 = 3;//^tangential
        final int KC5 = 4;//^6

        if(fc.length != LENGTH_FC) throw new ArrayIndexOutOfBoundsException("Focal length array contains " + fc.length + " elements (should have " + LENGTH_FC + ")");
        if(cc.length != LENGTH_CC) throw new ArrayIndexOutOfBoundsException("Principal point array contains " + cc.length + " elements (should have " + LENGTH_CC + ")");
        if(kc.length != LENGTH_KC) throw new ArrayIndexOutOfBoundsException("Distortion parameter array contains " + kc.length + " elements (should have " + LENGTH_KC + ")");
        
        double p[] = LinAlg.resize(pixel, 2);

        double centered[] = LinAlg.subtract(p, cc);
        centered[0] = centered[0] / fc[0];
        centered[1] = centered[1] / fc[1];

        double r2 = LinAlg.normF(centered);

        double scale  = 1 +
            (kc[KC1] * r2 +
             kc[KC2] * Math.pow(r2, 2) +
             kc[KC5] * Math.pow(r2, 3));

        double scaled[] = LinAlg.scale(centered, scale);
        double tangential[] =
                    {2 * kc[KC3] * centered[0] * centered[1] +
                     kc[KC4] * (r2 + 2 * centered[0] * centered[0]),
                     kc[KC3] * (r2 + 2 * centered[1] * centered[1]) +
                     2 * kc[KC4] * centered[0] * centered[1]};

        LinAlg.plusEquals(scaled, tangential);
        double result[] = {fc[0] * (scaled[0] + alpha * scaled[1]) +
                           cc[0],
                           scaled[1] * fc[1] + cc[1]};

        return result;
    }
	
	public static double[] distort(double pixel[], double fc[], double cc[], double kc[], double alpha)
    {
	    final int LENGTH_FC = 2;
        final int LENGTH_CC = 2;
        final int LENGTH_KC = 5;
        
        // indices for lookup in kc[]
        final int KC1 = 0;//^2
        final int KC2 = 1;//^4
        final int KC3 = 2;//^tangential
        final int KC4 = 3;//^tangential
        final int KC5 = 4;//^6

        if(fc.length != LENGTH_FC) throw new ArrayIndexOutOfBoundsException("Focal length array contains " + fc.length + " elements (should have " + LENGTH_FC + ")");
        if(cc.length != LENGTH_CC) throw new ArrayIndexOutOfBoundsException("Principal point array contains " + cc.length + " elements (should have " + LENGTH_CC + ")");
        if(kc.length != LENGTH_KC) throw new ArrayIndexOutOfBoundsException("Distortion parameter array contains " + kc.length + " elements (should have " + LENGTH_KC + ")");
           
        double p[] = LinAlg.resize(pixel, 2);

        double centered[] = LinAlg.subtract(p, cc);
        centered[0] = centered[0] / fc[0];
        centered[1] = centered[1] / fc[1];

        double r2 = LinAlg.normF(centered);

        double scale  = 1 +
            (kc[KC1] * r2 +
             kc[KC2] * Math.pow(r2, 2) +
             kc[KC5] * Math.pow(r2, 3));

        double scaled[] = LinAlg.scale(centered, scale);
        double tangential[] =
                    {2 * kc[KC3] * centered[0] * centered[1] +
                     kc[KC4] * (r2 + 2 * centered[0] * centered[0]),
                     kc[KC3] * (r2 + 2 * centered[1] * centered[1]) +
                     2 * kc[KC4] * centered[0] * centered[1]};

        LinAlg.plusEquals(scaled, tangential);
        double result[] = {fc[0] * (scaled[0] + alpha * scaled[1]) +
                           cc[0],
                           scaled[1] * fc[1] + cc[1]};

        return result;
    }
	
	
}
