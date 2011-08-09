package april.random;

import java.util.Comparator;
import april.calibration.Camera;


/**
 * Compares two Cameras and returns 1 if camera1 has more tagdetections then camera2
 * 
 * @author Mihai Bulic
 *
 */
public class CameraComparator implements Comparator<Camera>
{
    public int compare(Camera camera1, Camera camera2)
    {
        int tagCount1 = camera1.getTagCount();
        int tagCount2 = camera2.getTagCount();
        int value = 0;

        if (tagCount1 < tagCount2)
        {
            value = 1;
        }
        else if (tagCount1 > tagCount2)
        {
            value = -1;
        }

        return value;
    }
}
