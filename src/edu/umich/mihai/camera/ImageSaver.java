package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import javax.imageio.ImageIO;
import april.lcmtypes.image_path_t;
import lcm.lcm.LCM;

public class ImageSaver extends Thread
{
    private BlockingQueue<BufferedImage> queue;
    private LCM lcm = LCM.getSingleton();

    private String url;
    private HashMap<String, Integer> urls = new HashMap<String, Integer>();
    
    private String outputDir = "";
    private int saveCounter = 0;
    
    public ImageSaver(BlockingQueue<BufferedImage> queue, String url, String outputDir)
    {
        this.queue = queue;
        this.url = url;
        
        urls.put("dc1394://b09d01008b51b8", 0);
        urls.put("dc1394://b09d01008b51ab", 1);
        urls.put("dc1394://b09d01008b51b9", 2);
        urls.put("dc1394://b09d01009a46a8", 3);
        urls.put("dc1394://b09d01009a46b6", 4);
        urls.put("dc1394://b09d01009a46bd", 5);
        urls.put("dc1394://b09d01008c3f62", 10);
        
        this.outputDir = outputDir + "cam" + urls.get(url);
        // ensure that the directory exists
        File dir = new File(this.outputDir);
        dir.mkdirs();
    }
    
    public void run()
    {
        while (true)
        {
            image_path_t imagePath = new image_path_t();

            try
            {
                imagePath.img_path = saveImage(queue.take());
            } catch (Exception e)
            {
                e.printStackTrace();
            }

            lcm.publish("cam" + urls.get(url), imagePath);
        }
    }

    private String saveImage(BufferedImage image) throws Exception
    {
        String filepath = outputDir + File.separator + "IMG" + saveCounter;

        if (image == null)
        {
            throw new Exception("image is not ready");
        }

        ImageIO.write(image, "png", new File(filepath));

        saveCounter++;

        return filepath;
    }
}
