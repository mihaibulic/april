package edu.umich.mihai.camera;

public class LEDDetection
{
    public boolean singularity = false;
    
    public double timeStamp;
    
    // the ID of the LED
    public int id;
    
    // Pixel coordinates of center of LED detection
    public double[] uv = new double[2];
    
    // Transformation to get from origin to this led
    public double[][] transformation = new double[4][4];
    
    // XYZ translation to get to LED from origin
    public double[] xyz = new double[3];
        
    public LEDDetection()
    {
    }
    
    public LEDDetection(boolean singularity)
    {
        this.singularity = singularity;
    }
    
    public LEDDetection(double[] xyz, int id)
    {
        this.xyz = xyz;
        this.id = id;
    }
    
    public LEDDetection(int id, double[] uv)
    {
        this.id = id;
        this.uv = uv;
    }
}
