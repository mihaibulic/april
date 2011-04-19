package edu.umich.mihai.camera;

import april.config.Config;
import april.jmat.LinAlg;

public class CamUtil 
{
    // required calibration parameter lengths
    static final public int LENGTH_FC = 2;
    static final public int LENGTH_CC = 2;
    static final public int LENGTH_KC = 5;

    // indices for lookup in kc[]
    static final public int KC1 = 0;//^2
    static final public int KC2 = 1;//^4
    static final public int KC3 = 2;//^tangential
    static final public int KC4 = 3;//^tangential
    static final public int KC5 = 4;//^6

    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion, [kc1 kc2 kc3 kc4 kc5 kc6]
    private double alpha; // Skew
	
    public static int getIntProperty(Config config, String url, String property) throws ConfigException
    {
    	return getIntProperty(config, url, property, 1)[0];
    }
    
    public static int[] getIntProperty(Config config, String url, String property, int size) throws ConfigException
    {
    	int propertyValues[] = new int[size];
    	
    	String[] urls = config.getStrings("urls");
    	int[] allProps = config.getInts(property);
    	
    	for(int x = 0; x < urls.length; x++)
    	{
    		if(url.compareTo(urls[x]) == 0)
    		{
    			for(int y = 0; y < size; y++)
    			{
    				propertyValues[y] = allProps[x*size+y];
    			}
    		}
    	}
    	
    	return propertyValues;
    }
    
    public static double[] getDoubleProperty(Config config, String url, String property, int size) throws ConfigException
    {
    	double propertyValues[] = new double[size];
    	
    	String[] urls = config.getStrings("urls");
    	double[] allProps = config.getDoubles(property);
    	
    	for(int x = 0; x < urls.length; x++)
    	{
    		if(url.compareTo(urls[x]) == 0)
    		{
    			for(int y = 0; y < size; y++)
    			{
    				propertyValues[y] = allProps[x*size+y];
    			}
    		}
    	}
    	
    	return propertyValues;
    }
    
    // XXX set parameters??
	public double[] undistort(double pixel[])
    {
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
	
	public double[] distort(double pixel[])
    {
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
