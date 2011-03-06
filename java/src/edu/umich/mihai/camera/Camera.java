package edu.umich.mihai.camera;

import java.util.ArrayList;
import java.util.Collections;
import april.jmat.LinAlg;
import april.tag.TagDetection;

/**
 * 
 * Tracks a camera's tag detections and coordinates relative to the "main" camera
 * 
 * @author Mihai Bulic
 *
 */
public class Camera
{
    private int mainIndex;
    private int index;
    private ImageReader reader;
    private ArrayList<TagDetection> detections;
    private ArrayList<double[]> coordinates;
    private double[] stdDev;
    private double[] position;
    private String url;
    
    public Camera(int index, String url, double[] position)
    {
        this.index = index;
        this.url = url;
        this.position = position;
    }
    
    public Camera(ImageReader reader, int index)
    {
        this.reader = reader;
        this.index = index;
        detections = new ArrayList<TagDetection>();
        coordinates = new ArrayList<double[]>();
        position = new double[6];
        url = reader.getUrl();
    }
    
    public void addCoordinates(double[] coordinate)
    {
        coordinates.add(coordinate);
    }
    
    public void addCoordinates(double[][] coordinate)
    {
        coordinates.add(LinAlg.matrixToXyzrpy(coordinate));
    }

    public void addDetections()
    {
        try
        {
            addDetections(reader.getTagDetections(15));
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    public void addDetections(ArrayList<TagDetection> detections)
    {
        Collections.sort(detections, new TagComparator());
        int lastId = -1;
        
        for(TagDetection x : detections)
        {
            if(lastId == x.id)
            {
                continue;
            }
            else if(!isInDetections(x))
            {
                this.detections.add(x);
            }
            
            lastId = x.id;
        }
        
        Collections.sort(this.detections, new TagComparator());
    }

    public void clearCoordinates()
    {
        coordinates.clear();
    }
    
    private double[] elementMultiplication(double[] a, double[] b) throws Exception
    {
        double c[] = new double[a.length];
        
        if(a.length != b.length)
        {
            throw new Exception("Arrays not of equal size");
        }
        
        for(int x = 0; x < a.length; x++)
        {
            c[x] = a[x] * b[x];
        }
        
        return c;
    }
    
    public ArrayList<double[]> getCoordinates()
    {
        return coordinates;
    }
    
    public TagDetection getDetection(int index)
    {
        return detections.get(index);
    }

    public ArrayList<TagDetection> getDetections()
    {
        return detections;
    }
    
    public int getIndex()
    {
        return index;
    }
    
    public int getMain()
    {
        return mainIndex;
    }
    
    public double[] getPosition()
    {
        return position;
    }
    
    public ImageReader getReader()
    {
        return reader;
    }
    
    public int getTagCount()
    {
        return detections.size();
    }
    
    public double[][] getTransformationMatrix()
    {
        return LinAlg.xyzrpyToMatrix(position);
    }
    
    public String getUrl()
    {
        return url;
    }
    
    public boolean isCertain()
    {
        if(coordinates.size()==0)
            return false;
        
        if(stdDev == null)
        {
            setVariance();
        }
        
        double translationErr = (Math.sqrt(stdDev[0]) + Math.sqrt(stdDev[1]) + Math.sqrt(stdDev[2]))/3;
        double rotationErr = (Math.sqrt(stdDev[3]) + Math.sqrt(stdDev[4]) + Math.sqrt(stdDev[5]))/3;
        
        // certain iff there are at least 5 tagdetections, stdDev of xyz err is < 20cm, and stdDev of rpy err is < 180deg
//        return (coordinates.size()>5 && translationErr < 0.50 && rotationErr < Math.PI); // XXX for testing
        return (translationErr < 0.20 && rotationErr < Math.PI/2);
    }
    
    private boolean isInDetections(TagDetection prospectiveTag)
    {
        boolean inDetections = false;
        
        for(TagDetection tag : detections)
        {
            if(tag.id == prospectiveTag.id)
            {
               inDetections = true;
               break;
            }
        }

        return inDetections;
    }

    public void setMain(int main)
    {
        mainIndex = main;
    }
    
    public double[] setPosition()
    {
        position = new double[]{0,0,0,0,0,0};
        
        for(double[] coordinate: coordinates)
        {
            position = LinAlg.add(position, coordinate);
        }
        position = LinAlg.scale(position, (1.0/coordinates.size())); 

        return position;
    }
    
    public void setPosition(double[] position, int main)
    {
        this.position = position;
        this.mainIndex = main;
    }
    
    public void setPosition(double[][] position, int main)
    {
        this.position = LinAlg.matrixToXyzrpy(position);
        this.mainIndex = main;
    }
    
    private void setVariance()
    {
        double average[] = new double[]{0,0,0,0,0,0};
        stdDev = new double[]{0,0,0,0,0,0};
        
        for(double[] coordinate : coordinates)
        {
            average = LinAlg.add(average, coordinate);
        }

        average = LinAlg.scale(average, 1.0/coordinates.size());
        
        for(double[] coordinate : coordinates)
        {
            double tmp[] = LinAlg.subtract(coordinate, average);

            try
            {
                stdDev = LinAlg.add(elementMultiplication(tmp, tmp), stdDev);
            } catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }
}
