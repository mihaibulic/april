package mihai.camera;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import lcm.lcm.LCM;
import mihai.lcmtypes.image_path_t;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.util.TimeUtil;

public class ImageSaverSimple extends Thread
{
    private String dir, url, name;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private LCM lcm;
    
    public ImageSaverSimple(String url, String dir, int id)
    {
        System.out.println(url.substring(url.lastIndexOf("/")+1));
        name = "camera" + id;
        dir += (!dir.endsWith(File.separator) ? File.separator : "") + name; 
        new File(dir).mkdir();

        this.dir = dir;
        this.url = url;
        
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
                new FileOutputStream(new File(dir+File.separator+"IMG" + saveCounter)).write(imageBuffer);
                
                image_path_t img = new image_path_t();
                img.img_path = dir+File.separator+"IMG" + saveCounter++;
                img.format = ifmt.format;
                img.width = ifmt.width;
                img.height = ifmt.height;
                img.utime = TimeUtil.utime();
                img.id = url;
                lcm.publish(name, img);
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
