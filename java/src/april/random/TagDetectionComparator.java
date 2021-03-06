package april.random;

import java.util.Comparator;
import april.tag.TagDetection;

/**
 * Returns 1 iff the ID of tag1 is greater then tag2's ID
 * 
 * @author Mihai Bulic
 *
 */
public class TagDetectionComparator implements Comparator<TagDetection>
{
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
