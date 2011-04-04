package edu.umich.mihai.camera;

import java.util.ArrayList;
import java.util.Collections;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

/**
 * 
 * Tracks a camera's tag detections and coordinates relative to the "main" camera
 * 
 * @author Mihai Bulic
 *
 */
public class Camera implements ImageReader.Listener
{
    private int mainIndex;
    private int index;
    private ImageReader reader;
    private ArrayList<TagDetection> detections;
    private ArrayList<double[]> coordinates;
    private double[] position;
    private String url;

    private int imageCount = 0;
    private ArrayList<byte[]> imageBuffers;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
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
    
    public void aggregateTags(int size) throws InterruptedException
    {
        imageCount = size;
        imageBuffers = new ArrayList<byte[]>();
        
        this.reader.addListener(this);
        reader.start();
        
        synchronized(imageBuffers)
        {
            while(imageBuffers.size() < size)
            {
                imageBuffers.wait();
            }
        }
        
        TagDetector td = new TagDetector(new Tag36h11());
        for(byte[] buffer: imageBuffers)
        {
            detections.addAll(td.process(ImageConvert.convertToImage(format, height, width, buffer), new double[] {width/2.0, height/2.0}));
        }
        
        Collections.sort(detections, new TagComparator());
        
        int lastId = -1;
        int x = 0;
        while(x < detections.size())
        {
            if(lastId == detections.get(x).id)
            {
                detections.remove(x);
            }
            else
            {
                lastId = detections.get(x).id;
                x++;
            }
        }
    }
    
    public void addCoordinate(double[] xyzrpy)
    {
        coordinates.add(xyzrpy);
    }
    
    public void addCoordinates(double[][] matrix)
    {
        coordinates.add(LinAlg.matrixToXyzrpy(matrix));
    }

    public void clearCoordinates()
    {
        coordinates.clear();
    }
    
    /**
     * 
     * @return xyzrpy coordinates
     */
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
    
    public void setMain(int main)
    {
        mainIndex = main;
    }
    
    public void setPosition()
    {
        position = new double[]{0,0,0,0,0,0};
        
        for(double[] coordinate: coordinates)
        {
            position = LinAlg.add(position, coordinate);
        }
        position = LinAlg.scale(position, (1.0/coordinates.size())); 
    }
    
    public void setPosition(double[] xyzrpy, int main)
    {
        this.position = xyzrpy;
        this.mainIndex = main;
    }
    
    public void setPosition(double[][] matrix, int main)
    {
        this.position = LinAlg.matrixToXyzrpy(matrix);
        this.mainIndex = main;
    }
    
    @Override
    public void handleImage(byte[] image, ImageSourceFormat ifmt, double timeStamp)
    {
        if(imageBuffers.size() >= imageCount)
        {
            reader.kill();
            synchronized(imageBuffers)
            {
                imageBuffers.notify();
            }
        }
        else
        {
            imageBuffers.add(image);
            width = ifmt.width;
            height = ifmt.height;
            format = ifmt.format;
            imageCount++;
        }
    }
}
