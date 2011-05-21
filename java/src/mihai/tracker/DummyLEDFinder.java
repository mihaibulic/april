package mihai.tracker;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import mihai.camera.TagDetector2;
import mihai.util.PointDistortion;
import april.jcam.ImageConvert;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

/**
 * Temp replacement for LED tracker/finder (to be made by Ryan Anderson).
 * This dummy implementation uses tags and reports the center of the tag as the LED's location
 * 
 * @author Mihai Bulic
 *
 */
public class DummyLEDFinder
{
    private PointDistortion pd;
//    private TagDetector2 td;
    private TagDetector td;
    private double[] fc;
    private double[] cc;
    
    /**
     * Finds the LEDs and returns array of coordinates in pixel space along with IDs
     * 
     * @param image - Image in which to find LEDs
     * @return arraylist of led locations
     */
    public DummyLEDFinder(double[] fc, double[] cc, double[] kc, double alpha)
    {
    	this.fc = fc;
    	this.cc = cc;
//        td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
    	td = new TagDetector(new Tag36h11());
        pd = new PointDistortion(fc, cc, kc, alpha, 0.01);
    }
    
    public ArrayList<ImageObjectDetection> getObjectUV(byte[] buffer, int width, int height, String format)
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
