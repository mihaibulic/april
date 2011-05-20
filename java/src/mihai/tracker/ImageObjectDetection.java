package mihai.tracker;

/**
 * id, timestamp, and uv are set by the ObjectFinder (finds objects in image)
 * transformation and xyz are set by the ObjectTracker (triangulates objectss in 3d space using multiple cameras)
 * 
 * @author Mihai Bulic
 *
 */
public class ImageObjectDetection
{
    public long timeStamp;
    
    // the ID of the object
    public int id;
    
    // Pixel coordinates of center of object detection
    public double[] uv;
    
    // transformation to spotting camera's location from origin (NOT to object)
    public double[][] transformation;
    
    public double[] fc;
    public double[] cc;
    
    public ImageObjectDetection(int id, long timeStamp, double[] uv, double[] fc, double[] cc)
    {
        this.id = id;
        this.timeStamp = timeStamp;
        this.uv = uv;
        this.fc = fc;
        this.cc = cc;
    }
}
