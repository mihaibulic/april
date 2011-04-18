package edu.umich.mihai.camera;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import magic.camera.util.SyncErrorDetector;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.util.TimeUtil;

/**
 * Sets camera to a certain resolution, color format, and framerate and reads off images (byte[]) and handles this for all subscribed listeners
 * 
 * @author Mihai Bulic
 *
 */
public class ImageReader extends Thread
{
    ArrayList<Listener> Listeners = new ArrayList<Listener>();

    private boolean run = true;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private String url;
    private HashMap<String, Integer> urlsAvailable = new HashMap<String, Integer>();
    private int camera;
    
    private SyncErrorDetector sync;
    
    public interface Listener
    {
        public void handleImage(byte[] image, ImageSourceFormat ifmt, double timeStamp, int camera);
    }
    
    public ImageReader(String urls) throws Exception
    {
        this(false, false, 15, urls);
    }
    
    public ImageReader(boolean loRes, boolean color16, int maxfps, String url) throws CameraException, IOException
    {
        setUrls();
        this.url = url;
        if(urlsAvailable.get(url) == null) return;
        camera = urlsAvailable.get(url);
        
        if (maxfps > (loRes ? 120 : 60)) throw new CameraException(CameraException.FPS);

        setIsrc(loRes, color16, maxfps, this.url);
        
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
		isrc.start();
    	
        boolean firstTime = true;
        double initTime = 0;
        double lastTimestamp = 0;
        int rollOverCounter = 0;
        double[] times;
        
        while (run)
        {
        	TimeUtil.sleep(30);
            byte imageBuffer[] = isrc.getFrame();
            
            if (imageBuffer != null)
            {
                sync.addTimePointGreyFrame(imageBuffer);
                times = sync.getTimes();

                if(firstTime)
                {
                    firstTime = false;
                    initTime = times[times.length-1];
                }
                
                if(sync.verify() == SyncErrorDetector.SYNC_GOOD)
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
                        listener.handleImage(imageBuffer, ifmt, timestamp, camera);
                    }
                }
                else if(sync.verify() == SyncErrorDetector.RECOMMEND_ACTION)
                {
                	toggleImageSource(isrc);
                	firstTime = true;
                }
	        }
    	}
        
        isrc.stop();
    }

    private void setIsrc(boolean loRes, boolean color16, int fps, String urls) throws IOException
    {
        isrc = ImageSource.make(urls);

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
        urlsAvailable.put("dc1394://b09d01008b51b8", 0);
        urlsAvailable.put("dc1394://b09d01008b51ab", 1);
        urlsAvailable.put("dc1394://b09d01008b51b9", 2);
        urlsAvailable.put("dc1394://b09d01009a46a8", 3);
        urlsAvailable.put("dc1394://b09d01009a46b6", 4);
        urlsAvailable.put("dc1394://b09d01009a46bd", 5);
        urlsAvailable.put("dc1394://b09d01008c3f62", 10);
        urlsAvailable.put("dc1394://b09d01008c3f6a", 11); // has J on it
        urlsAvailable.put("dc1394://b09d01008e366c", 12); // unmarked
    }
    
    private void toggleImageSource(ImageSource isrc)
    {
        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
        sync = new SyncErrorDetector(10, 0.001, 0.01, 0.0, 0, false);
    }

    public String getUrl()
    {
        return url;
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
    
    public void setFramerate(int fps)
    {
		isrc.setFeatureValue(11, fps); // frame-rate, idx=11
    }
    
    public void setFormat(boolean loRes, boolean color16)
    {
        isrc.stop();
        isrc.setFormat(Integer.parseInt("" + (loRes ? 1 : 0) + (color16 ? 1 : 0), 2));
        ifmt = isrc.getCurrentFormat();
        isrc.start();
    }
}
