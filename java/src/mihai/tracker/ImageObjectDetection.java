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
    public int objectID;
    public int cameraID;

    public long timeStamp;

    // Pixel coordinates of center of object detection
    public double[] uv;

    // transformation to spotting camera's location from origin (NOT to object)
    public double[][] cameraTransformation;
    
    public double[] fc;
    public double[] cc;
    
    //used only for sandbox testing
    public ImageObjectDetection(int objectID, long timeStamp, double[] uv, double[] fc, double[] cc)
    {
        this.objectID = objectID;
        this.timeStamp = timeStamp;
        this.uv = uv;
        this.fc = fc;
        this.cc = cc;
    }
    
    public ImageObjectDetection(int objectID, int cameraID, long timeStamp, double[] uv, double[][] cameraTransformation, double[] fc, double[] cc)
    {
        this.objectID = objectID;
        this.cameraID = cameraID;
        this.timeStamp = timeStamp;
        this.uv = uv;
        this.cameraTransformation = cameraTransformation;
        this.fc = fc;
        this.cc = cc;
    }
    
    @Override
    public boolean equals(Object other)
    {
    	return (cameraID == ((ImageObjectDetection)other).cameraID);
    }
}
