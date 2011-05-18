package edu.umich.mihai.camera;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import edu.umich.mihai.led.TagComparator;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.util.ConfigException;
import edu.umich.mihai.util.PointLocator;
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
    private ArrayList< Tag > tagsL;
    private ArrayList<double[]> potentialPositions;
    private double[] position;

    private int imageCount = 0;
    private ArrayList<byte[]> imageBuffers;
    private int width = 0;
    private int height = 0;
    private String format = "";
    
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[];
    private double kc[];
    private double alpha;
    
    private HashMap< Integer, double[]> tagsH = new HashMap<Integer, double[]>();
    
    // XXX temp
    public Camera(ArrayList<Tag> tagsL, HashMap< Integer, double[]> tagsH, double[] position)
    {
        this.tagsL = tagsL;
        this.tagsH = tagsH;
        this.position = position;
    }
    
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
        
        TagDetector2 td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
        for(byte[] buffer: imageBuffers)
        {
            detections.addAll(td.process(ImageConvert.convertToImage(format, width, height, buffer), cc));
        }
        
        Collections.sort(detections, new TagComparator());
        
        
        int end = 0;
        tagsL = new ArrayList< Tag >();
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
            
            tagsL.add(new Tag(PointLocator.calculateItt(points), last_id));
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
    
    public Tag getTag(int index)
    {
        return tagsL.get(index);
    }

    public double[] getTagH(int id)
    {
        return tagsH.get(id);
    }
    
    public HashMap<Integer,double[]> getTagsH()
    {
        return tagsH;
    }
    
    public ArrayList<Tag> getTags()
    {
        return tagsL;
    }
    
    public double[] getFocal()
    {
    	return fc;
    }
    
    public int getCameraId()
    {
        return ir.getCameraId();
    }
    
    public int getMain()
    {
        return mainIndex;
    }
    
    public double[] getXyzrpy()
    {
        return position;
    }
    
    public ImageReader getReader()
    {
        return ir;
    }

    public int getTagCountH()
    {
        return tagsH.size();
    }
    
    public int getTagCount()
    {
        return tagsL.size();
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
        return (potentialPositions.size() > 0);
    }
}

