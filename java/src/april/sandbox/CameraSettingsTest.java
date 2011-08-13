package april.sandbox;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import april.vis.VisGuiSkeleton;
import aprilO.jcam.ImageConvert;
import aprilO.jcam.ImageSource;
import aprilO.jcam.ImageSourceFormat;
import aprilO.vis.VisImage;

public class CameraSettingsTest extends VisGuiSkeleton
{
    private static final long serialVersionUID = 1L;

//    private JSlider = new 
    
    public CameraSettingsTest()
    {
        super(0,0,752,480);
        
        vc.setBackground(Color.magenta);
        
        run();
    }
    
    public void run()
    {
        ImageSource isrc = null;
        ImageSourceFormat ifmt = null;
        try
        {
            isrc = ImageSource.make(ImageSource.getCameraURLs().get(1));
            isrc.setFormat(0);
            ifmt = isrc.getCurrentFormat();
            isrc.start();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        BufferedImage im = null;
        while(true)
        {
            im = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, isrc.getFrame());
            vb.addBuffered(new VisImage(im));
            vb.switchBuffer();
        }
        
    }
    
    public static void main(String[] args)
    {
        new CameraSettingsTest();
    }

}
