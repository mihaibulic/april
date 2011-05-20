package mihai.tracker;

public class SpaceObjectDetection
{
    public boolean singularity = false;
    
    public long timeStamp;
    
    // the ID of the object
    public int id;
    
    // Transformation to get from origin to this object
    public double[][] transformation;
    
    // xyzrpy to get to object from origin
    public double[] xyzrpy;
        
    public SpaceObjectDetection(boolean singularity)
    {
        this.singularity = singularity;
    }
    
    public SpaceObjectDetection(int id, long timestamp, double[] xyzrpy)
    {
        this.id = id;
        this.timeStamp = timestamp;
        this.xyzrpy = xyzrpy;
    }
}
