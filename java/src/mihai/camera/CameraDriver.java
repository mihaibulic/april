package mihai.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import magic.camera.util.SyncErrorDetector;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;

public class CameraDriver extends Thread
{
    private int samples = 10;
    private double chi = 0.01;
    private double minimumSlope = 0.01;
    private double timeThresh = 0.0;
    private int verbosity = -1;
    private boolean gui = false;
    private SyncErrorDetector sync;

    private boolean hiRes = true;
    private boolean color8 = true;
    private int fps = 60;
    private String id;
    private String url;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    
    private boolean newImage = false;
    private Object imageLock = new Object();
    private byte[] imageBuffer;

    private boolean run = true;
    private boolean done = false;
    private Object driverLock = new Object();

    public CameraDriver(String url) throws CameraException, IOException, ConfigException
    {
        this.url = url;
        
        try
        {
            isrc = ImageSource.make(url);
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        ifmt = isrc.getCurrentFormat();
        
        sync = new SyncErrorDetector(samples, chi, minimumSlope, timeThresh, verbosity, gui);
    }

    public CameraDriver(String url, Config config) throws ConfigException
    {
        config = config.getRoot().getChild(getSubUrl(config, url));
        ConfigUtil.verifyConfig(config);
        
        run = isValidUrl(config, url);
        if(run)
        {
            this.url = url;
            
            id = config.requireString("id");
            hiRes = config.requireBoolean("hiRes");
            color8 = config.requireBoolean("color8");
            fps = config.requireInt("fps");
            
            try
            {
                isrc = ImageSource.make(url);
            } catch (IOException e)
            {
                e.printStackTrace();
            }

            // 760x480 8 = 0, 760x480 16 = 1, 380x240 8 = 2, 380x240 16 = 3
            // converts booleans to 1/0 and combines them into an int
            isrc.setFormat(Integer.parseInt("" + (hiRes ? 0 : 1) + (color8 ? 0 : 1), 2));
            isrc.setFeatureValue(15, fps); // frame-rate, idx=11

            ifmt = isrc.getCurrentFormat();
            
            config = config.getRoot().getChild("sync");
            ConfigUtil.verifyConfig(config);
            sync = new SyncErrorDetector(config);
        }
    }

    public CameraDriver(String url, boolean hiRes, boolean color8, int fps,
            int samples, double chi, double minimumSlope, double timeThresh, int verbosity, boolean gui)
    {
        this.url = url;

        try
        {
            isrc = ImageSource.make(url);
        } catch (IOException e)
        {
            e.printStackTrace();
        }

        // 760x480 8 = 0, 760x480 16 = 1, 380x240 8 = 2, 380x240 16 = 3
        // converts booleans to 1/0 and combines them into an int
        isrc.setFormat(Integer.parseInt("" + (hiRes ? 0 : 1) + (color8 ? 0 : 1), 2));
        isrc.setFeatureValue(15, fps); // frame-rate, idx=11
        ifmt = isrc.getCurrentFormat();

        sync = new SyncErrorDetector(samples, chi, minimumSlope, timeThresh, verbosity, gui);
    }
    
    public void run()
    {
        isrc.start();
        
        while (run)
        {
            byte imageBuffer[] = isrc.getFrame();
            
            if(imageBuffer != null)
            {
                sync.addTimePointGreyFrame(imageBuffer);

                int status = sync.verify();
                if(status == SyncErrorDetector.SYNC_GOOD)
                {
                    synchronized(imageLock)
                    {
                        newImage = true;
                        this.imageBuffer = imageBuffer;
                        imageLock.notify();
                    }
                } 
                else if (status == SyncErrorDetector.RECOMMEND_ACTION)
                {
                    try
                    {
                        toggleImageSource(isrc);
                    } catch (ConfigException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        isrc.stop();
        synchronized(driverLock)
        {
            done = true;
            driverLock.notify();
        }
    }
    
    private void toggleImageSource(ImageSource isrc) throws ConfigException
    {
        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
        sync = new SyncErrorDetector(samples, chi, minimumSlope, timeThresh, verbosity, gui);
    }

    public String getCameraId()
    {
        return id;
    }

    public String getUrl()
    {
        return url;
    }
    
    public int getWidth()
    {
        return ifmt.width;
    }
    
    public int getHeight()
    {
        return ifmt.height;
    }
    
    public String getFormat()
    {
        return ifmt.format;
    }
    
    public byte[] getFrameBuffer()
    {
        synchronized(imageLock)
        {
            while(!newImage)
            {
                try
                {
                    imageLock.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            newImage = false;
            return imageBuffer;
        }
    }
    
    public BufferedImage getFrameImage()
    {
        synchronized(imageLock)
        {
            while(!newImage)
            {
                try
                {
                    imageLock.wait();
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
            newImage = false;
            return ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imageBuffer);
        }
    }
    
    public static String getSubUrl(Config config, String url)
    {
        String prefix = config.requireString("default_url");
        
        if(url.contains(prefix))
        {
            url = url.substring(url.indexOf(prefix) + prefix.length());
        }
        
        return url;
    }
    
    public void kill() throws InterruptedException
    {
        run = false;
        synchronized(driverLock)
        {
            while(!done)
            {
                driverLock.wait();
            }
        }
    }
    
    public boolean isGood()
    {
        return run;
    }
    
    public static boolean isValidUrl(Config config, String url)
    {
        config = config.getChild(getSubUrl(config, url));
        return (config != null && config.getBoolean("valid", false));
    }

    public void setFormat(boolean hiRes, boolean color8)
    {
        isrc.stop();
        isrc.setFormat(Integer.parseInt("" + (hiRes ? 0 : 1) + (color8 ? 0 : 1), 2));
        ifmt = isrc.getCurrentFormat();
        isrc.start();
    }

    public void setFramerate(int fps)
    {
        isrc.setFeatureValue(15, fps); // frame-rate, idx=11
    }
    
    public void setSync(SyncErrorDetector newSync)
    {
        sync = newSync;
    }
    
    public void setSync(int samples, double chi, double minimumSlope, double timeThresh, int verbosity, boolean gui)
    {
        sync = new SyncErrorDetector(samples, chi, minimumSlope, timeThresh, verbosity, gui);
    }
}
