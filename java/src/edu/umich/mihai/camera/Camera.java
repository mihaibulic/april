package edu.umich.mihai.camera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import edu.umich.mihai.led.TagComparator;
import edu.umich.mihai.sandbox.PointLocator;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.Util;

/**
 * Tracks a camera's tag detections and coordinates relative to a "main" camera
 * 
 * @author Mihai Bulic
 *
 */
public class Camera implements ImageReader.Listener
{
    private int mainIndex;
    private ImageReader ir;
    private ArrayList<TagDetection> detections;
    private ArrayList< Tag > tags;
    private ArrayList<double[]> potentialPositions;
    private double[] position;
    private double[] stdDev;

    private int imageCount = 0;
    private ArrayList<byte[]> imageBuffers;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[];
    private double kc[];
    private double alpha;
    
    public Camera(Config config, String url) throws CameraException, IOException, ConfigException
    {
        Util.verifyConfig(config);
    	
    	position = config.requireDoubles("xyzrpy");
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
    	
        detections = new ArrayList<TagDetection>();
        potentialPositions = new ArrayList<double[]>();
        ir = new ImageReader(config.getRoot(), url);
    }
    
    class Tag
    {
        double[] xyzrpy;
        int id;
        
        public Tag(double[] xyzrpy, int id)
        {
            this.xyzrpy = xyzrpy;
            this.id = id;
        }
    }
    
    public boolean isGood()
    {
    	return ir.isGood();
    }
    
    public void aggregateTags(int size, double tagSize) throws InterruptedException
    {
        imageCount = size;
        imageBuffers = new ArrayList<byte[]>();
        
        this.ir.addListener(this);
        ir.start();
        
        synchronized(imageBuffers)
        {
            while(imageBuffers.size() < size)
            {
                imageBuffers.wait();
            }
        }
        
        TagDetector td = new TagDetector(new Tag36h11(), fc, cc, kc, alpha);
        for(byte[] buffer: imageBuffers)
        {
            detections.addAll(td.process(ImageConvert.convertToImage(format, width, height, buffer), cc));
        }
        
        Collections.sort(detections, new TagComparator());
        
        int lastId = -1;
        int x = 0;
        
        int end = 0;
        tags = new ArrayList< Tag >();
        for(int start = 0; start < detections.size(); start=end)
        {
            int last_id = detections.get(start).id;
            int id = last_id;
            
            while( last_id==id && ++end < detections.size())
            {
                id = detections.get(end).id;
            }
            
            double[][] points = new double[end-start][6];
            for(int b = 0; b < points.length; b++)
            {
                double[][] M = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, detections.get(start+b).homography);
                points[b] = LinAlg.matrixToXyzrpy(M);
            }
            
            tags.add(new Tag(PointLocator.calculateItt(points),last_id));
        }
        
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
    
    public void addCorrespondence(double[][] matrix)
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
    
    public Tag getDetection(int index)
    {
        return tags.get(index);
    }

    public ArrayList<Tag> getDetections()
    {
        return tags;
    }
    
    public double[] getFocal()
    {
    	return fc;
    }
    
    public int getCameraId()
    {
        return ir.getCameraId();
//        return 0;
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
        return ir;
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
        return ir.getUrl();
    }
    
    public void setMain(int main)
    {
        mainIndex = main;
    }
    
    public void setPosition()
    {
        double[][] points = new double[potentialPositions.size()][6];
        position = PointLocator.calculateItt(potentialPositions.toArray(points));
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
    
    public void handleImage(byte[] image, ImageSourceFormat ifmt, double timeStamp, int camera)
    {
        if(imageBuffers.size() >= imageCount)
        {
            ir.kill();
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
