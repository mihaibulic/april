package mihai.tracker;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import mihai.camera.ImageReader;
import mihai.camera.TagDetector2;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.tag.Tag36h11;
import april.tag.TagDetection;

/**
 * Tracks a given objects in 3D space (used by ObjectTracker)
 * 
 * @author Mihai Bulic
 *
 */
public class Track extends Thread implements ImageReader.Listener
{
    ArrayList<Listener> listeners = new ArrayList<Listener>();

    private double[][] transformation;
    private int id;
    private double fc[]; // Focal length, in pixels, [X Y]
    private double cc[]; // Principal point, [X Y] 
    private double kc[]; // Distortion
    private double alpha;// Skew

    private ImageReader ir;

    // pull out into subclass
    private TagDetector2 td;
    private int width;
    private int height;
    private String format;
    
    public interface Listener
    {
        public void handleDetections(ArrayList<ImageObjectDetection> objects, double[][] transformation);
    }
    
    public Track(Config config, String url) throws ConfigException, CameraException, IOException
    {
    	Util.verifyConfig(config);

    	id = config.requireInt("id");
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
        transformation = LinAlg.xyzrpyToMatrix(config.requireDoubles("xyzrpy"));
        
    	ir = new ImageReader(config.getRoot(), url);
    	ir.addListener(this);
    	
        // pull out into subclass
    	width = ir.getWidth();
    	height = ir.getHeight();
    	format = ir.getFormat();
    	td = new TagDetector2(new Tag36h11(),fc, cc, kc, alpha);
    }

    // force override
    public ArrayList<ImageObjectDetection> getObjectUV(byte[] buffer)
    {
        BufferedImage image = ImageConvert.convertToImage(format, width, height, buffer);
        ArrayList<TagDetection> tags = td.process(image, cc);
        ArrayList<ImageObjectDetection> detections = new ArrayList<ImageObjectDetection>();
        long time = System.currentTimeMillis();
        
        for (TagDetection tag: tags)
        {
            detections.add(new ImageObjectDetection(tag.id, time, tag.cxy, fc, cc));
        }

        return detections;
    }
    
    public int getIndex()
    {
    	return ir.getCameraId();
    }
    
    public double[][] getTransformationMatrix()
    {
    	return transformation;
    }
    
    public boolean isGood()
    {
    	return ir.isGood();
    }
    
    public void start()
    {
        ir.start();
    }
    
    public void kill()
    {
        ir.kill();
    }

    public void addListener(Listener listener)
    {
        listeners.add(listener);
    }

    // force override
    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        ArrayList<ImageObjectDetection> objects = getObjectUV(image);
        
        if(objects.size() > 0)
        {
            for(ImageObjectDetection object : objects)
            {
                object.timeStamp = timeStamp;
                object.cameraM = transformation;
                object.cameraID = id;
            }
            
            for (Listener listener : listeners)
            {
                listener.handleDetections(objects, transformation);
            }
        }
    }
}
