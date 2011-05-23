package mihai.tracker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;

import mihai.camera.TagDetector2;
import april.jcam.ImageConvert;
import april.tag.Tag36h11;
import april.tag.TagDetection;

/**
 * Temp replacement for LED tracker/finder (to be made by Ryan Anderson).
 * This dummy implementation uses tags and reports the center of the tag as the LED's location
 * 
 * @author Mihai Bulic
 *
 */
public class DummyLEDFinder
{
    private TagDetector2 td;
    private double[] fc;
    private double[] cc;
    
    private int width;
    private int height;
    private String format;
    
    /**
     * Finds the LEDs and returns array of coordinates in pixel space along with IDs
     * 
     * @param image - Image in which to find LEDs
     * @return arraylist of led locations
     */
    public DummyLEDFinder(double[] fc, double[] cc, double[] kc, double alpha, int width, int height, String format)
    {
    	this.fc = fc;
    	this.cc = cc;
    	this.width = width;
    	this.height = height;
    	this.format = format;

    	td = new TagDetector2(new Tag36h11(),fc, cc, kc, alpha);
    }
    
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
}
