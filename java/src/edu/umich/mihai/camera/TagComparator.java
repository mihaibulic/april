package edu.umich.mihai.camera;

import java.util.Comparator;
import april.tag.TagDetection;

public class TagComparator implements Comparator<TagDetection>
{
    @Override
    public int compare(TagDetection tag1, TagDetection tag2)
    {
        int id1 = tag1.id;
        int id2 = tag2.id;
        int value = 0;
        
        if (id1 > id2)
        {
            value = 1;
        }
        else if (id1 < id2)
        {
            value = -1;
        }

        return value;
    }
}
