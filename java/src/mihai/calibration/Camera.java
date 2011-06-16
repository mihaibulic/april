package mihai.calibration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import mihai.camera.ImageReader;
import mihai.camera.TagDetector2;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.PointUtil;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;

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
    private double[] xyzrpy;
    private double[][] matrix;

    private int imageCount = 0;
    private ArrayList<byte[]> imageBuffers;
    private int width;
    private int height;
    private String format;
    
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[];
    private double kc[];
    private double alpha;
    
    private HashMap< Integer, double[]> tagsH = new HashMap<Integer, double[]>();
    
    /**
     * @deprecated temporary only for testing
     */
    public Camera(ArrayList<Tag> tagsL, HashMap< Integer, double[]> tagsH, double[] position)
    {
        this.tagsL = tagsL;
        this.tagsH = tagsH;
        this.xyzrpy = position;
        this.matrix = LinAlg.xyzrpyToMatrix(this.xyzrpy);
    }
    
    public Camera(Config config, String url) throws CameraException, IOException, ConfigException
    {
        Util.verifyConfig(config);
    	
    	xyzrpy = config.requireDoubles("xyzrpy");
    	matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
    	
        detections = new ArrayList<TagDetection>();
        potentialPositions = new ArrayList<double[]>();
        
        ir = new ImageReader(config.getRoot(), url);
        width = ir.getWidth();
        height = ir.getHeight();
        format = ir.getFormat();
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
        ir.kill();
        
        TagDetector2 td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
        for(byte[] buffer: imageBuffers)
        {
            detections.addAll(td.process(ImageConvert.convertToImage(format, width, height, buffer), cc));
        }
        
        Collections.sort(detections, new TagDetectionComparator());
        
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
            
            double[] tagXyzrpy = PointUtil.calculateItt(points); 
            tagsL.add(new Tag(tagXyzrpy, last_id));
            tagsH.put(last_id, tagXyzrpy);
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
    
    public HashMap<Integer,double[]> getTagsHashMap()
    {
        return tagsH;
    }
    
    public ArrayList<Tag> getTagsList()
    {
        return tagsL;
    }
    
    public int getTagCount()
    {
        return tagsL.size();
    }
    
    public double[] getXyzrpy()
    {
        return xyzrpy;
    }
    public double[][] getTransformationMatrix()
    {
        return matrix;
    }
    
    public ImageReader getReader()
    {
        return ir;
    }

    public String getUrl()
    {
        return ir.getUrl();
    }

    public int getCameraId()
    {
        return ir.getCameraId();
    }
    
    public int getMain()
    {
        return mainIndex;
    }

    public double[] getFocal()
    {
        return fc;
    }
    
    /**
     * @return xyzrpy coordinates
     */
    public ArrayList<double[]> getPotentialPositions()
    {
        return potentialPositions;
    }

    // FIXME use more rigerous method here 
    public boolean isCertain()
    {
        return (potentialPositions.size() > 0);
    }
    
    public boolean isGood()
    {
        return ir.isGood();
    }
    
    public void kill() throws InterruptedException
    {
        ir.kill();
    }
    
    public void setMain(int main)
    {
        mainIndex = main;
    }
    
    public void setPosition()
    {
        double[][] points = new double[potentialPositions.size()][6];
        xyzrpy = PointUtil.calculateItt(potentialPositions.toArray(points));
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
    }
    
    public void setPosition(double[] xyzrpy)
    {
        this.xyzrpy = xyzrpy;
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        mainIndex = -1; // camera positions are now not relative to one another anymore
    }
    
    public void setPosition(double[] xyzrpy, int main)
    {
        this.xyzrpy = xyzrpy;
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        this.mainIndex = main;
    }
    
    public void setPosition(double[][] matrix, int main)
    {
        this.matrix = matrix;
        this.xyzrpy = LinAlg.matrixToXyzrpy(matrix);
        this.mainIndex = main;
    }
    
    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        if(imageBuffers.size() >= imageCount)
        {
            synchronized(imageBuffers)
            {
                imageBuffers.notify();
            }
        }
        else
        {
            imageBuffers.add(image);
        }
    }
}

