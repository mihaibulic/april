package edu.umich.mihai.camera;

import java.util.ArrayList;
import java.util.Collections;
import april.jmat.LinAlg;
import april.tag.TagDetection;

public class Camera
{
    private int mainIndex;
    private ImageReader reader;
    private ArrayList<TagDetection> detections;
    private ArrayList<double[]> coordinates;
    private double[] position;
    
    public Camera(ImageReader reader)
    {
        this.reader = reader;
    }
    
    public void addDetections(ArrayList<TagDetection> detections)
    {
        Collections.sort(this.detections, new TagComparator());
        int lastId = -1;
        
        for(TagDetection x : this.detections)
        {
            if(lastId == x.id)
            {
                continue;
            }
            else if(!isInDetections(x))
            {
                detections.add(x);
            }
            
            lastId = x.id;
        }
        
        Collections.sort(detections, new TagComparator());
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
    
    public void addDetections()
    {
        try
        {
            addDetections(reader.getTagDetections(50));
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    public void addCoordinates(double[] coordinate)
    {
        coordinates.add(coordinate);
    }
    
    public ImageReader getReader()
    {
        return reader;
    }
    
    public ArrayList<TagDetection> getDetections()
    {
        return detections;
    }

    public TagDetection getDetection(int index)
    {
        return detections.get(index);
    }
    
    public ArrayList<double[]> getCoordinates()
    {
        return coordinates;
    }
    
    public int getTagCount()
    {
        return detections.size();
    }
    
    private double[] getSquaredStdDev()
    {
        double average[] = new double[6];
        double stdDev[] = new double[6];
        
        for(double[] coordinate : coordinates)
        {
            LinAlg.add(average, coordinate);
        }

        LinAlg.scale(average, 1.0/coordinates.size());
        
        for(double[] coordinate : coordinates)
        {
            double tmp[] = LinAlg.subtract(coordinate, average);
            stdDev = LinAlg.add(LinAlg.xyzrpyMultiply(tmp, tmp), stdDev);
        }
        
        return LinAlg.scale(stdDev, coordinates.size());
    }
    
    public double[] setPosition()
    {
        double position[] = new double[6];
        
        for(double[] coordinate: coordinates)
        {
            LinAlg.add(position, coordinate);
        }
        
        position = LinAlg.scale(position, (1.0/coordinates.size())); 
        
        return position;
    }
    
    public void setPosition(double[] position, int main)
    {
        this.position = position;
        this.mainIndex = main;
    }

    public double[] getPosition()
    {
        return position;
    }
    
    public boolean isCertain()
    {
        if(coordinates.size()==0)
            return false;

        double stdDev[] = getSquaredStdDev();
        double translationErr = Math.sqrt(stdDev[0]) + Math.sqrt(stdDev[1]) + Math.sqrt(stdDev[2]);
        double rotationErr = Math.sqrt(stdDev[3]) + Math.sqrt(stdDev[4]) + Math.sqrt(stdDev[5]);
        
        // certain iff there are at least 5 tagdetections, stdDev of xyz err is < 20cm, and stdDev of rpy err is < 180deg
        return (coordinates.size()>5 && translationErr < 0.20 && rotationErr < Math.PI);
    }
    
    public int getMain()
    {
        return mainIndex;
    }
    
    public void setMain(int main)
    {
        mainIndex = main;
    }
    
}