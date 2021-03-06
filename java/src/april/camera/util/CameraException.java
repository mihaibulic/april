package april.camera.util;

public class CameraException extends Exception
{
    private static final long serialVersionUID = 1L;
    
    public static final int NULL_IMAGESOURCE = 0;
    public static final int NO_CAMERA = 1;
    public static final int NO_TAGS = 2;
    public static final int UNCERTAIN = 3;
    public static final int CYCLE = 4;
    public static final int UNKNOWN_URL = 5;
    public static final int FPS = 6;
    public static final int FILE_TOO_LARGE = 7;
    
    public CameraException(int err)
    {
        String msg = "Camera Exception: ";
        
        switch(err)
        {
            case NULL_IMAGESOURCE:
                msg += "The imagesource is null.";
                break;
            case NO_CAMERA:
                msg += "No cameras found.";
                break;
            case NO_TAGS:
                msg += "No tags found.";            
                break;
            case UNCERTAIN:
                msg += "Unable to calculate intercamera coordinates due to high uncertainty.";
                break;
            case CYCLE:
                msg += "Camera to camera calibration has halted due to a cycle in camera to camera positions.";
                break;
            case UNKNOWN_URL:
                msg += "Camera URL is unknown.";
                break;
            case FPS:
                msg += "FPS is too large. It must be less then or equal to 120 if resolution is low, or 60 is resolution is high.";
                break;
            case FILE_TOO_LARGE:
                msg += "This file is too large to open.";
                break;
            default:
                msg += "Unknown error";
        }
        
        System.err.println(msg);
    }
}
