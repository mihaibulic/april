package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import javax.imageio.ImageIO;
import lcm.lcm.LCM;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.lcmtypes.image_path_t;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

/**
* Gets camera images and saves them as necessary
*
* @deprecated use ImageReader and ImageSaver 
*/
public class CameraDriver
{
    private LCM lcm = LCM.getSingleton();

    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private HashMap<String, Integer> urls = new HashMap<String, Integer>();
    private String url;

//    private SyncErrorDetector syncDetector;
    private String outputDir;
    private int imCounter;
    private int imModulus;
    private int saveCounter;
    
    private TagDetector td;

    public CameraDriver(String url) throws Exception
    {
        this(url, "", false, false, 15, false);
    }
    
    public CameraDriver(String url, boolean loRes,boolean color16, int maxfps) throws Exception
    {
        this(url, "", loRes, color16, maxfps, false);
    }
    
    public CameraDriver(String url, String outputDir, boolean loRes, boolean color16, int maxfps, boolean record) throws Exception
    {
        if (maxfps > (loRes ? 120 : 60))
        {
            throw new Exception("FPS is too large.  It must be less then or equal to 120 if resolution is low, or 60 is resolution is high");
        }

        this.url = url;
        imModulus = (loRes ? 120 : 60) / maxfps;
        urls.put("dc1394://b09d01008b51b8", 0);
        urls.put("dc1394://b09d01008b51ab", 1);
        urls.put("dc1394://b09d01008b51b9", 2);
        urls.put("dc1394://b09d01009a46a8", 3);
        urls.put("dc1394://b09d01009a46b6", 4);
        urls.put("dc1394://b09d01009a46bd", 5);
        urls.put("dc1394://b09d01008c3f62", 10);
        urls.put("dc1394://b09d01008c3f6a", 11); // has J on it
        urls.put("dc1394://b09d01008e366c", 12); // unmarked
        
        if(urls.get(url) == null)
        {
            return;
        }
            
        if(record)
        {
            this.outputDir = outputDir + "cam" + urls.get(url);
            // ensure that the directory exists
            File dir = new File(this.outputDir);
            dir.mkdirs();
        }
            
        isrc = ImageSource.make(url);

        // 760x480 8 = 0
        // 760x480 16 = 1
        // 380x240 8 = 2
        // 380x240 16 = 3
        // converts booleans to 1/0 and combines them into an int
        isrc.setFormat(Integer.parseInt("" + (loRes ? 1 : 0)
                + (color16 ? 1 : 0), 2));

        isrc.setFeatureValue(0, 1); // white-balance-manual=1, idx=0
        isrc.setFeatureValue(1, 495); // white-balance-red=495, idx=1
        isrc.setFeatureValue(2, 612); // white-balance-blue=612, idx=2
        isrc.setFeatureValue(3, 0); // exposure-manual=0, idx=3
        isrc.setFeatureValue(5, 0); // brightness-manual=0, idx=5
        isrc.setFeatureValue(7, 0); // gamma-manual=1, idx=7
        isrc.setFeatureValue(9, 1); // timestamps-enable=1, idx=9
        isrc.setFeatureValue(10, 1); // frame-rate-manual=1, idx=10
        isrc.setFeatureValue(11, (loRes ? 120 : 60)); // frame-rate, idx=11

        ifmt = isrc.getCurrentFormat();

        BlockingQueue<BufferedImage> queue = new ArrayBlockingQueue<BufferedImage>(
                100);

        new Reader(queue, record).start();
        
        if (record)
        {
            new Save(queue).start();
        }
    }

    public class Reader extends Thread
    {
        private final BlockingQueue<BufferedImage> queue;
        private ArrayList<TagDetection> detections;
        private boolean detectionsFlag = false;
        private boolean consume;
        
        Reader(BlockingQueue<BufferedImage> queue, boolean consume)
        {
            this.queue = queue;
            this.consume = consume;
        }

        public void run()
        {
            // makeSyncDetector();

            isrc.start();

            while (true)
            {
                byte imageBuffer[] = null;
                BufferedImage image = null;

                imageBuffer = isrc.getFrame();
                if (imageBuffer == null)
                {
                    System.out.println("err getting frame");
                    toggleImageSourceFormat(isrc);
                    continue;
                }

//                syncDetector.addTimePointGreyFrame(imageBuffer); // XXX

//                int syncResult = syncDetector.verify();
//
//                if (syncResult == SyncErrorDetector.RECOMMEND_ACTION)
//                {
//                    toggleImageSourceFormat(isrc);
//                    makeSyncDetector();
//                    continue;
//                }
//
//                if (syncResult == SyncErrorDetector.SYNC_BAD)
//                {
//                    toggleImageSourceFormat(isrc);
//                    continue;
//                }

                imCounter++;

                if (imCounter % imModulus == 0)
                {
                    image = ImageConvert.convertToImage(ifmt.format,ifmt.width, ifmt.height, imageBuffer);

                    if (image == null)
                    {
                        System.out.println("err converting to image");
                        toggleImageSourceFormat(isrc);
                        continue;
                    }

                    try
                    {
                        if(detectionsFlag)
                        {
                            setTagDetections(image);
                        }
                        
                        if(consume)
                        {
                            queue.put(image);
                        }
                    } 
                    catch (IllegalStateException ise)
                    {
                        System.err.println("Queue is full, emptying...");
                        queue.clear();
                        continue;
                    }
                    catch (InterruptedException ie)
                    {
                        ie.printStackTrace();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void toggleImageSourceFormat(ImageSource isrc)
        {
            isrc.stop();
            int currentFormat = isrc.getCurrentFormatIndex();
            isrc.setFormat(currentFormat);

            isrc.start();
        }

        /**
         * Convenience method if sync detector must be remade when toggling isrc
         * format.
         * 
         * samples = 10; // 20 sample history chi2Tolerance = 0.001; // Tolerate
         * Chi^2 error under... minimumSlope = 0.01; // Minimum slope for
         * timestamp timeThresh = 0.0; // Suggest restart after holding bad sync
         * for _ seconds verbosity = 1; // Debugging output level (0=almost
         * none) gui = false;
         **/
//        private void makeSyncDetector()
//        {
//            syncDetector = new SyncErrorDetector(10, 0.001, 0.01, 0.0, 1, false);
//        }

        private void setTagDetections(BufferedImage image) throws Exception
        {
            if (image == null)
            {
                throw new Exception("image is not ready");
            }
            
            if(td == null)
            {
                td = new TagDetector(new Tag36h11());
            }
            
            synchronized(detections)
            {
                detections = td.process(image, new double[] {image.getWidth()/2.0, image.getHeight()/2.0});
                detectionsFlag = false;
                detections.notify();
            }
        }
        
        public ArrayList<TagDetection> getTagDetections() throws InterruptedException
        {
            detectionsFlag = true;
            
            synchronized(detections)
            {
                while(detectionsFlag)
                {
                    detections.wait();
                }
            }
            
            return detections;
        }
        
    }

    public class Save extends Thread
    {
        private final BlockingQueue<BufferedImage> queue;
        
        Save(BlockingQueue<BufferedImage> queue)
        {
            this.queue = queue;
        }

        public void run()
        {
            while (true)
            {
                image_path_t imagePath = new image_path_t();

                try
                {
                    BufferedImage image = queue.take();
                    imagePath.img_path = saveImage(image);
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
}
