package mihai.tracker;

public class ImageObjectDetection
{
    public int objectID;
    public int cameraID;
    public long timeStamp;
    public double[] uv; // Pixel coordinates of center of object detection
    public double[][] cameraTransformation; // transformation to spotting camera's location from origin (NOT to object)
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
