package april.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import lcm.lcm.LCM;
import april.lcmtypes.image_path_t;
import aprilO.jcam.ImageSource;
import aprilO.jcam.ImageSourceFormat;
import aprilO.util.TimeUtil;

public class ImageSaverSimple extends Thread
{
    private String dir, id, imagePath;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private LCM lcm;
    
    public ImageSaverSimple(String url, String dir)
    {
        this.dir = dir + (!dir.endsWith(File.separator) ? File.separator : "");
        id = CameraDriver.getSubUrl(url);
        imagePath = id + File.separator; 
        System.out.println(id);
        
        new File(this.dir+imagePath).mkdir();
        
        try
        {
            lcm = LCM.getSingleton();
            isrc = ImageSource.make(url);
            ifmt = isrc.getCurrentFormat();
        } catch (IOException e)
        {
            e.printStackTrace();
        }

    }
    
    public void run()
    {
        int saveCounter = 0;
        isrc.start();
        
        while (true)
        {
            byte imageBuffer[] = isrc.getFrame();
            if (imageBuffer == null)
            {
                System.out.println("Err: null frame");
                continue;
            }
            
            try
            {
                String path = imagePath + "IMG" + saveCounter;
                new FileOutputStream(new File(dir+path)).write(imageBuffer);
                saveCounter++;
                
                image_path_t img = new image_path_t();
                img.utime = TimeUtil.utime();
                img.id = id;
                img.dir = dir;
                img.img_path = path;
                img.format = ifmt.format;
                img.width = ifmt.width;
                img.height = ifmt.height;
                lcm.publish("rec " + id, img);
            } catch (FileNotFoundException e)
            {
                e.printStackTrace();
            } catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
