package edu.umich.mihai.sandbox;

import java.io.IOException;

import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.TagDetection;
import edu.umich.mihai.camera.Camera;
import edu.umich.mihai.camera.CameraException;
import edu.umich.mihai.camera.ImageReader;

public class TagHomography 
{

	public static void main(String[] args) throws CameraException, IOException, InterruptedException 
	{
		ImageReader ir = new ImageReader(false, false, 15, "dc1394://b09d01009a46a8");
        Camera camera = new Camera(ir, 3);
        camera.aggregateTags(5);
        
        TagDetection tags[] = camera.getDetections().toArray(new TagDetection[1]);

        double[][] mainM = CameraUtil.homographyToPose(477.5, 477.5, 0.1375, tags[0].homography);
        double[][] auxM = CameraUtil.homographyToPose(477.5, 477.5, 0.1375, tags[1].homography);

        double[] xyzrpy = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.inverse(auxM), mainM));

        System.out.println("x: " + xyzrpy[0] + "\ty: " + xyzrpy[1] + "\tz: " +  + xyzrpy[2] );
        System.out.println("r: " + xyzrpy[3] + "\tp: " + xyzrpy[4] + "\ty: " +  + xyzrpy[5] );
	}
}
