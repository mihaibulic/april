package edu.umich.mihai.camera;

import java.util.Comparator;

public class CameraComparator implements Comparator<Camera>
{
    @Override
    public int compare(Camera camera1, Camera camera2)
    {
        int tagCount1 = camera1.getTagCount();
        int tagCount2 = camera2.getTagCount();
        int value = 0;

        if (tagCount1 > tagCount2)
        {
            value = 1;
        }
        else if (tagCount1 < tagCount2)
        {
            value = -1;
        }

        return value;
    }
}
