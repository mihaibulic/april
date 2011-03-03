package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

public class ImageReader
{
    private final BlockingQueue<BufferedImage> queue;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;

    // private SyncErrorDetector syncDetector;
    // private String outputDir;
    private int imCounter;
    private int imModulus;

    private TagDetector td;
    private ArrayList<TagDetection> detections;
    private boolean detectionsFlag = false;
    private boolean consume;

    public ImageReader(String url) throws Exception
    {
        this(new ArrayBlockingQueue<BufferedImage>(100), url, "", false, false, 15, false);
    }
    
    public ImageReader(String url, boolean loRes,boolean color16, int maxfps) throws Exception
    {
        this(new ArrayBlockingQueue<BufferedImage>(100), url, "", loRes, color16, maxfps, false);
    }
    
    public ImageReader(BlockingQueue<BufferedImage> queue, String url, String outputDir, boolean loRes, boolean color16, int maxfps, boolean consume) throws Exception
    {
        this.queue = queue;
        this.consume = consume;

        if (maxfps > (loRes ? 120 : 60))
        {
            throw new Exception("FPS is too large.  It must be less then or equal to 120 if resolution is low, or 60 is resolution is high");
        }

        imModulus = (loRes ? 120 : 60) / maxfps;

        isrc = ImageSource.make(url);

        // 760x480 8 = 0
        // 760x480 16 = 1
        // 380x240 8 = 2
        // 380x240 16 = 3
        // converts booleans to 1/0 and combines them into an int
        isrc.setFormat(Integer.parseInt("" + (loRes ? 1 : 0) + (color16 ? 1 : 0), 2));

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

//            syncDetector.addTimePointGreyFrame(imageBuffer); // XXX

//            int syncResult = syncDetector.verify();
//
//            if (syncResult == SyncErrorDetector.RECOMMEND_ACTION)
//            {
//                toggleImageSourceFormat(isrc);
//                makeSyncDetector();
//                continue;
//            }
//
//            if (syncResult == SyncErrorDetector.SYNC_BAD)
//            {
//                toggleImageSourceFormat(isrc);
//                continue;
//            }

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
//    private void makeSyncDetector()
//    {
//        syncDetector = new SyncErrorDetector(10, 0.001, 0.01, 0.0, 1, false);
//    }

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
    
    public ArrayList<TagDetection> getTagDetections(int images) throws InterruptedException
    {
        ArrayList<TagDetection> detections = new ArrayList<TagDetection>();
        
        for(int x = 0; x < images; x++)
        {
            detectionsFlag = true;
            
            synchronized(detections)
            {
                while(detectionsFlag)
                {
                    detections.wait();
                }
            }
            this.detections.addAll(detections);
        }
        
        return this.detections;
    }
    
}
