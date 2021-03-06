package april.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import april.camera.util.CameraException;
import april.camera.util.TagDetector2;
import april.random.TagDetectionComparator;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import april.util.PointUtil;
import april.config.Config;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;

/**
 * Tracks a camera's tag detections and coordinates relative to a master camera
 * 
 * @author Mihai Bulic
 * @deprecated
 */
public class Camera
{
    private int masterIndex;
    private CameraDriver driver;
    private ArrayList<TagDetection> detections;
    private ArrayList< Tag > tagsL;
    private ArrayList<double[]> potentialPositions;
    private double[] xyzrpy;
    private double[][] matrix;

    private ArrayList<BufferedImage> images;
    
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[];
    private double kc[];
    private double alpha;
    
    private HashMap< Integer, double[]> tagsH = new HashMap<Integer, double[]>();
    
    public class Tag
    {
        public double[] xyzrpy;
        public double[][] matrix;
        public int id;
        
        @Override
        public boolean equals(Object a)
        {
            return (id == ((Tag)a).id);
        }
    }
    
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
        ConfigUtil2.verifyConfig(config);
        driver = new CameraDriver(url, config);

        config = config.getRoot().getChild(CameraDriver.getSubUrl(url));
    	xyzrpy = config.requireDoubles("xyzrpy");
    	matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
    	
        detections = new ArrayList<TagDetection>();
        potentialPositions = new ArrayList<double[]>();
    }
    
    public void aggregateTags(int size, double tagSize) throws InterruptedException
    {
        images = new ArrayList<BufferedImage>();
        
        driver.start();
        while(images.size() < size)
        {
            images.add(driver.getFrameImage());
        }
        driver.kill();
        
        TagDetector2 td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
        for(BufferedImage image: images)
        {
            detections.addAll(td.process(image, cc));
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
            
            double[] tagXyzrpy = PointUtil.getLocation(points);
            Tag t = new Tag();
            t.xyzrpy = tagXyzrpy;
            t.id = last_id;
            tagsL.add(t);
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
    
    public CameraDriver getDriver()
    {
        return driver;
    }

    public String getUrl()
    {
        return driver.getUrl();
    }

    public String getCameraId()
    {
        return driver.getCameraId();
    }
    
    public int getMain()
    {
        return masterIndex;
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
        return driver.isGood();
    }
    
    public void kill() throws InterruptedException
    {
        driver.kill();
    }
    
    public void setMaster(int master)
    {
        masterIndex = master;
    }
    
    public void setPosition()
    {
        double[][] points = new double[potentialPositions.size()][6];
        xyzrpy = PointUtil.getLocation(potentialPositions.toArray(points));
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
    }
    
    public void setPosition(double[] xyzrpy)
    {
        this.xyzrpy = xyzrpy;
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        masterIndex = -1; // camera positions are now not relative to one another anymore
    }
    
    public void setPosition(double[] xyzrpy, int master)
    {
        this.xyzrpy = xyzrpy;
        matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        this.masterIndex = master;
    }
    
    public void setPosition(double[][] matrix, int master)
    {
        this.matrix = matrix;
        this.xyzrpy = LinAlg.matrixToXyzrpy(matrix);
        this.masterIndex = master;
    }
}

