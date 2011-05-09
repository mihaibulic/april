package edu.umich.mihai.sandbox;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import edu.umich.mihai.camera.CamUtil;
import edu.umich.mihai.camera.CameraException;
import edu.umich.mihai.camera.ImageReader;
import edu.umich.mihai.misc.ConfigException;
import edu.umich.mihai.camera.TagDetector;

public class TagTest implements ImageReader.Listener
{
	private TagDetector td;
    private TagDetector tdOld;
	double tagSize;
	double[] fc;
	double[] cc;
	double[] kc;
	double alpha;
	
	public TagTest() throws IOException, CameraException, ConfigException
	{
		String url = "dc1394://b09d01009a46a8";
		
		Config config = new ConfigFile(System.getenv("CONFIG")+"/camera.config");
		tagSize = config.requireDouble("tagSize");
		ImageReader ir = new ImageReader(config, url);
		ir.addListener(this);

		config = config.getChild(CamUtil.getUrl(config, url));
		fc = config.requireDoubles("fc");
		cc = config.requireDoubles("cc");
		kc = config.requireDoubles("kc");
		alpha = config.requireDouble("alpha");
		
		td = new TagDetector(new Tag36h11(), fc, cc, kc, alpha);
		tdOld = new TagDetector(new Tag36h11());
		
		ir.start();
	}
	
	public static void main(String[] args) throws IOException, CameraException, ConfigException 
	{
		new TagTest();
	}

	public void handleImage(byte[] image, ImageSourceFormat ifmt, double timeStamp, int camera) 
	{
	    BufferedImage im = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, image);
		ArrayList<TagDetection> tags = td.process(im, new double[] {ifmt.width/2, ifmt.height/2});
		ArrayList<TagDetection> tagsOld = tdOld.process(im, new double[] {ifmt.width/2, ifmt.height/2});
		
        for (int x = 0; x < tags.size(); x++)
        {
            double[] xyz = LinAlg.matrixToXyzrpy(CameraUtil.homographyToPose(fc[0], fc[1], tagSize, tags.get(x).homography));
            double[] xyzOld = LinAlg.matrixToXyzrpy(CameraUtil.homographyToPose(fc[0], fc[1], tagSize, tagsOld.get(x).homography));
            
            for(int y = 0; y < 6; y++)
            {
            	System.out.print((xyz[y]-xyzOld[y]) + "\t\t\t");
            }
            System.out.print('\n');
        }
	}

	
}
