package edu.umich.mihai.camera;

import java.io.IOException;
import java.util.ArrayList;

import magic.camera.util.SyncErrorDetector;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;

/**
 * Sets camera to a certain resolution, color format, and framerate and reads off images (byte[]) and handles this for all subscribed listeners
 * 
 * @author Mihai Bulic
 *
 */
public class ImageReader extends Thread
{
	private Config config;
	
    private ArrayList<Listener> Listeners = new ArrayList<Listener>();

    private boolean run = true;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private String url;
    private int index;
    
    private SyncErrorDetector sync;
    
    public interface Listener
    {
        public void handleImage(byte[] image, ImageSourceFormat ifmt, double timeStamp, int camera);
    }
    
    public ImageReader(String url) throws CameraException, IOException, ConfigException
    {
    	this(new ConfigFile("$CONFIG/camera.config") , url);
    }
    
    public ImageReader(boolean loRes, boolean color16, int maxfps, String url) throws CameraException, IOException, ConfigException
    {
    	this(setConfig(loRes, color16, maxfps), url);
    }
    
    public ImageReader(Config config, String url) throws CameraException, IOException, ConfigException
    {
    	if(config == null) throw new ConfigException(ConfigException.NULL_CONFIG);
    	this.config = config;
    	boolean loRes = config.requireBoolean("loRes");
    	boolean color16 = config.requireBoolean("color16");
    	int maxfps = config.requireInt("fps");
    	
    	this.url = url;
    	int indices[] = config.requireInts("indices");
    	String urls[] = config.requireStrings("urls");
    	boolean found = false;
    	
    	if(indices.length != urls.length) throw new ConfigException(ConfigException.INDICES_URL_LENGTH);
    	
    	for(int x = 0; x < indices.length; x++)
    	{
    		if(urls[x].equals(url)) 
			{
    			index = indices[x];
    			found = true;
    			break;
			}
    	}
    	if(!found)
    	{
    		this.kill();
    	}
    	else
    	{
	        if (maxfps > (loRes ? CameraException.MAX_LO_RES : CameraException.MAX_HI_RES)) throw new CameraException(CameraException.FPS);
	        setIsrc(loRes, color16, maxfps, this.url);
	        sync = new SyncErrorDetector(config.getChild("sync"));
    	}
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
                        listener.handleImage(imageBuffer, ifmt, timestamp, index);
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

    private static Config setConfig(boolean loRes, boolean color16, int maxfps) throws IOException
    {
    	Config config = new ConfigFile("$CONFIG/camera.config");

    	config.setBoolean("loRes", loRes);
    	config.setBoolean("color16", color16);
    	config.setInt("maxfps", maxfps);
    	
    	return config;
    }
    
    public int getIndex()
    {
    	return index;
    }
    
    private void setIsrc(boolean loRes, boolean color16, int fps, String urls) throws IOException
    {
        isrc = ImageSource.make(urls);

        // 760x480 8 = 0, 760x480 16 = 1, 380x240 8 = 2, 380x240 16 = 3
        // converts booleans to 1/0 and combines them into an int
        isrc.setFormat(Integer.parseInt("" + (loRes ? 1 : 0) + (color16 ? 1 : 0), 2));
        isrc.setFeatureValue(11, fps); // frame-rate, idx=11

        int features[] = config.getInts("isrc_feature");
        for(int x = 0; x < features.length; x++)
        {
        	isrc.setFeatureValue(x, features[x]);
        }

    	ifmt = isrc.getCurrentFormat();
    }
    
    private void toggleImageSource(ImageSource isrc)
    {
        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
        sync = new SyncErrorDetector(config.getChild("sync"));
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
    
    public boolean isGood()
    {
    	return run;
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
