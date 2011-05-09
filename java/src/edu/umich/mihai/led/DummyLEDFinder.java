package edu.umich.mihai.led;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import edu.umich.mihai.camera.TagDetector;
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
        td = new TagDetector(new Tag36h11(), fc, cc, kc, alpha);
    }
    
    public ArrayList<LEDDetection> getLedUV(byte[] buffer, int width, int height, String format)
    {
        BufferedImage image = ImageConvert.convertToImage(format, width, height, buffer);
        ArrayList<TagDetection> tags = td.process(image, new double[] {image.getWidth()/2, image.getHeight()/2});
        ArrayList<LEDDetection> detections = new ArrayList<LEDDetection>();
        
        for (int x = 0; x < tags.size(); x++)
        {
            detections.add(new LEDDetection(tags.get(x).id, tags.get(x).cxy, fc, cc));
        }

        return detections;
    }
}
