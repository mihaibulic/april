package april.calibration;

import java.util.ArrayList;
import aprilO.tag.TagDetection;

public class CornerManager
{
    private final static int CORNERS = 4;
    private final static int MOSAIC_WIDTH = 6;
    private final static int MOSAIC_HEIGHT = 8;
    private final static int MOSAIC_OFFSET = 10;
    
    public final static int SHORT_LINE_TYPE = 100;
    public final static int LONG_LINE_TYPE = 101;
    
    public final static int FIRST_LINE = 200;
    public final static int SECOND_LINE = 201;
    
    public final static int FIRST_POINT = 300;
    public final static int SECOND_POINT = 301;
    
    private double[][] tagCorners;
    
    public CornerManager(ArrayList<TagDetection> tags)
    {
        tagCorners = new double[tags.size()*4][2];
     
        for(int x = 0; x < tags.size(); x++)
        {
            double[] p0 = tags.get(x).interpolate(1, 1);
            double[] p1 = tags.get(x).interpolate(-1, 1);
            double[] p2 = tags.get(x).interpolate(1, -1);
            double[] p3 = tags.get(x).interpolate(-1, -1);

            tagCorners[(tags.get(x).id - MOSAIC_OFFSET) * 4 + 0] = p0;
            tagCorners[(tags.get(x).id - MOSAIC_OFFSET) * 4 + 1] = p1;
            tagCorners[(tags.get(x).id - MOSAIC_OFFSET) * 4 + 2] = p2;
            tagCorners[(tags.get(x).id - MOSAIC_OFFSET) * 4 + 3] = p3;

        }
    }
    
    public double[][] getCorners()
    {
        return tagCorners;
    }
    
    public ArrayList<double[]> getLine(int lineType, int column, int lineNumber)
    {
        ArrayList<double[]> line = new ArrayList<double[]>();
        
        if((lineType == SHORT_LINE_TYPE && column >= MOSAIC_WIDTH) ||
           (lineType == LONG_LINE_TYPE && column >= MOSAIC_HEIGHT) ||
           column < 0)
        {
            throw new RuntimeException("Err: column is invalid");
        }

        if(lineType == SHORT_LINE_TYPE)
        {
            int start = column*MOSAIC_WIDTH*CORNERS;
            int end = start + MOSAIC_WIDTH*CORNERS;
            
            for(int x = start; x < end; x+=CORNERS)
            {
                if(lineType == FIRST_LINE)
                {
                    line.add(tagCorners[x+1]);
                    line.add(tagCorners[x+0]);
                }
                else
                {
                    line.add(tagCorners[x+3]);
                    line.add(tagCorners[x+2]);
                }
            }
        }
        else
        {
            int start = column*MOSAIC_HEIGHT*CORNERS;
            int end = start + MOSAIC_HEIGHT*CORNERS;
            
            for(int x = start; x < end; x+=CORNERS)
            {
                if(lineType == FIRST_LINE)
                {
                    line.add(tagCorners[x+0]);
                    line.add(tagCorners[x+2]);
                }
                else
                {
                    line.add(tagCorners[x+1]);
                    line.add(tagCorners[x+3]);
                }
            }
        }
        
        return line;
    }
    
    public double[] getCorner(int tagId, int lineType, int lineNumber, int point)
    {
        int offset = 0;
        
        if(lineType == SHORT_LINE_TYPE)
        {
            if(lineNumber == FIRST_LINE)
            {
                if(point == FIRST_POINT)
                {
                    offset = 0;
                }
                else if(point == SECOND_POINT)
                {
                    offset = 1;
                }
                else
                {
                    throw new RuntimeException("Err: invalid corner");
                }
            }
            else if(lineNumber == SECOND_LINE)
            {
                if(point == FIRST_POINT)
                {
                    offset = 2;   
                }
                else if(point == SECOND_POINT)
                {
                    offset = 3;   
                }
                else
                {
                    throw new RuntimeException("Err: invalid corner");
                }
            }
            else
            {
                throw new RuntimeException("Err: invalid corner");
            }
        }
        else if(lineType == LONG_LINE_TYPE)
        {
            if(lineNumber == FIRST_LINE)
            {
                if(point == FIRST_POINT)
                {
                    offset = 0;
                }
                else if(point == SECOND_POINT)
                {
                    offset = 2;
                }
                else
                {
                    throw new RuntimeException("Err: invalid corner");
                }
            }
            else if(lineNumber == SECOND_LINE)
            {
                if(point == FIRST_POINT)
                {
                    offset = 1;   
                }
                else if(point == SECOND_POINT)
                {
                    offset = 3;   
                }
                else
                {
                    throw new RuntimeException("Err: invalid corner");
                }
            }
            else
            {
                throw new RuntimeException("Err: invalid corner");
            }
        }
        else
        {
            throw new RuntimeException("Err: invalid corner");
        }
        
        return tagCorners[(tagId - MOSAIC_OFFSET) * offset];
    }
}
