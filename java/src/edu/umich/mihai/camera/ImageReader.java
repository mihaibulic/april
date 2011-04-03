package edu.umich.mihai.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import magic.camera.util.SyncErrorDetector;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;

public class ImageReader extends Thread
{
    ArrayList<Listener> Listeners = new ArrayList<Listener>();

    private boolean run = true;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private String url;
    private HashMap<String, Integer> urls = new HashMap<String, Integer>();
    
    private SyncErrorDetector sync;
    private boolean firstTime = true;
    
    private int imagesToProcess;
    private boolean tagFlag = false;
    private ArrayList<TagDetection> tags;

    public interface Listener
    {
        public void handleImage(BufferedImage image, double timeStamp);
    }
    
    public ImageReader(String url) throws Exception
    {
        this(url, false, false, 15);
    }
    
    public ImageReader(String url, boolean loRes, boolean color16, int maxfps) throws Exception
    {
        setUrls();
        if(urls.get(url) == null) return;
        this.url = url;

        if (maxfps > (loRes ? 120 : 60)) throw new CameraException(CameraException.FPS);

        setIsrc(loRes, color16, maxfps, url);
        
//          samples         = 10;               // 20 sample history
//          chi2Tolerance   = 0.001;            // Tolerate Chi^2 error under...
//          minimumSlope    = 0.01;             // Minimum slope for timestamp
//          timeThresh      = 0.0;              // Suggest restart after holding bad sync for _ seconds
//          verbosity       = 1;                // Debugging output level (0=almost none)
//          gui             = false;
          sync = new SyncErrorDetector(10, 0.001, 0.01, 0.0, 0, false);
    }

    public void run()
    {
        TagDetector td = new TagDetector(new Tag36h11());
        isrc.start();

        double initTime = 0;
        double lastTimestamp = 0;
        int rollOverCounter = 0;
        
        while (run)
        {
            byte imageBuffer[] = null;
            BufferedImage image = null;

            imageBuffer = isrc.getFrame();
            if (imageBuffer != null)
            {
                sync.addTimePointGreyFrame(imageBuffer);
                double[] times = sync.getTimes();
                if(firstTime)
                {
                    firstTime = false;
                    initTime = times[times.length-1];
                }
                
                if(sync.verify() == SyncErrorDetector.RECOMMEND_ACTION)
                {
                    toggleImageSource(isrc);
                }
                
                if(sync.verify() == SyncErrorDetector.SYNC_GOOD)
                {
                    image = ImageConvert.convertToImage(ifmt.format,ifmt.width, ifmt.height, imageBuffer);
        
                    if (image != null)
                    {
                        double timestamp = times[times.length-1] - initTime;
                        if(lastTimestamp > timestamp)
                        {
                            rollOverCounter++;
                        }
                        lastTimestamp = timestamp;
                        timestamp += 128*rollOverCounter;
                        
                        for (Listener listener : Listeners)
                        {
                            listener.handleImage(image, timestamp);
                        }
                        
                        if(tagFlag)
                        {
                            synchronized(tags)
                            {
                                if (tags == null)
                                {
                                    tags = new ArrayList<TagDetection>();
                                }
                                
                                tags.addAll(td.process(image, new double[] {image.getWidth()/2.0, image.getHeight()/2.0}));
                                imagesToProcess--;
                                
                                if(imagesToProcess == 0)
                                {
                                    tags.notify();
                                }
                            }
                        } 
                    }
                    else
                    {
                        System.out.println("err converting to image");
                        toggleImageSource(isrc);
                    }
                }
            }
            else
            {
                System.out.println("err getting frame");
                toggleImageSource(isrc);
            }
        }
    }

    private void setIsrc(boolean loRes, boolean color16, int fps, String url) throws IOException
    {
        isrc = ImageSource.make(url);

        // 760x480 8 = 0, 760x480 16 = 1, 380x240 8 = 2, 380x240 16 = 3
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
        isrc.setFeatureValue(11, fps); // frame-rate, idx=11
        
        ifmt = isrc.getCurrentFormat();
    }
   
    private void setUrls()
    {
        urls.put("dc1394://b09d01008b51b8", 0);
        urls.put("dc1394://b09d01008b51ab", 1);
        urls.put("dc1394://b09d01008b51b9", 2);
        urls.put("dc1394://b09d01009a46a8", 3);
        urls.put("dc1394://b09d01009a46b6", 4);
        urls.put("dc1394://b09d01009a46bd", 5);
        urls.put("dc1394://b09d01008c3f62", 10);
        urls.put("dc1394://b09d01008c3f6a", 11); // has J on it
        urls.put("dc1394://b09d01008e366c", 12); // unmarked
    }
    
    private void toggleImageSource(ImageSource isrc)
    {
        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
        
        sync = new SyncErrorDetector(10, 0.001, 0.01, 0.0, 0, false);
        firstTime = true;
    }

    public String getUrl()
    {
        return url;
    }
    
    public ArrayList<TagDetection> getTagDetections(int images) throws InterruptedException
    {
        imagesToProcess = images;
        tagFlag = true;
        
        if(tags==null) tags = new ArrayList<TagDetection>();
        
        synchronized(tags)
        {
            while(imagesToProcess > 0)
            {
                tags.wait();
            }
        }
        
        Collections.sort(tags, new TagComparator());
        int lastId = -1;
        
        int x = 0;
        while(x < tags.size())
        {
            if(lastId == tags.get(x).id)
            {
                tags.remove(x);
            }
            else
            {
                lastId = tags.get(x).id;
                x++;
            }
        }
        
        return tags;
    }

    public void addListener(Listener listener)
    {
        Listeners.add(listener);
    }
    
    /**
     * Stops the imageReader thread in a safe way
     */
    public void kill()
    {
        run = false;
    }
}
