package edu.umich.mihai.camera;

public class CameraException extends Exception
{
    private static final long serialVersionUID = 1L;
    
    public static final int NO_CAMERA = 1;
    public static final int NO_TAGS = 2;
    public static final int UNCERTAIN = 3;
    public static final int CYCLE = 4;
    public static final int UNKNOWN_URL = 5;
    public static final int FPS = 6;
    
    public static final int MAX_LO_RES = 120; // max framerate on low resolution for pt grey firefly camera
    public static final int MAX_HI_RES = 60; // max framerate on high resolution for pt grey firefly camera
    
    public CameraException(int err)
    {
        String msg = "Camera Exception: ";
        
        switch(err)
        {
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
            default:
                msg += "Unknown error";
        }
        
        System.err.println(msg);
        System.exit(1);
    }
}
