package edu.umich.mihai.camera;

import java.util.ArrayList;
import java.util.Collections;
import edu.umich.mihai.led.TagComparator;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

/**
 * Tracks a camera's tag detections and coordinates relative to a "main" camera
 * 
 * @author Mihai Bulic
 *
 */
public class Camera implements ImageReader.Listener
{
    private int mainIndex;
    private int index;
    private String url;
    private ImageReader reader;
    private ArrayList<TagDetection> detections;
    private ArrayList<double[]> potentialPositions;
    private double[] position;
    private double[] stdDev;

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
        potentialPositions = new ArrayList<double[]>();
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
            detections.addAll(td.process(ImageConvert.convertToImage(format, width, height, buffer), new double[] {width/2.0, height/2.0}));
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
    
    public void addPotentialPosition(double[] xyzrpy)
    {
        potentialPositions.add(xyzrpy);
    }
    
    public void addPotentialPosition(double[][] matrix)
    {
        potentialPositions.add(LinAlg.matrixToXyzrpy(matrix));
    }

    public void clearPotentialPositions()
    {
        potentialPositions.clear();
    }
    
    /**
     * @return xyzrpy coordinates
     */
    public ArrayList<double[]> getPotentialPositions()
    {
        return potentialPositions;
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
        
        for(double[] coordinate: potentialPositions)
        {
            position = LinAlg.add(position, coordinate);
        }
        position = LinAlg.scale(position, (1.0/potentialPositions.size())); 
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
        }
    }
    
    // FIXME use more robust method here 
    // FIXME eliminate magic numbers/make them parameters rather then hardcoded
    public boolean isCertain()
    {
        if(potentialPositions.size()==0)
            return false;
        
        if(stdDev == null)
        {
            setVariance();
        }
        
        double translationErr = (Math.sqrt(stdDev[0]) + Math.sqrt(stdDev[1]) + Math.sqrt(stdDev[2]))/3;
        double rotationErr = (Math.sqrt(stdDev[3]) + Math.sqrt(stdDev[4]) + Math.sqrt(stdDev[5]))/3;
        
        // certain iff there are at least 5 tagdetections, stdDev of xyz err is < 20cm, and stdDev of rpy err is < 180deg
//        return (coordinates.size()>5 && translationErr < 0.50 && rotationErr < Math.PI);
        return (translationErr < 0.20 && rotationErr < Math.PI/2);
    }
    private void setVariance()
    {
        double average[] = new double[]{0,0,0,0,0,0};
        stdDev = new double[]{0,0,0,0,0,0};
        
        for(double[] coordinate : potentialPositions)
        {
            average = LinAlg.add(average, coordinate);
        }

        average = LinAlg.scale(average, 1.0/potentialPositions.size());
        
        for(double[] coordinate : potentialPositions)
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

}
