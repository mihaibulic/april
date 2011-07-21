package mihai.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import magic.camera.util.SyncErrorDetector;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;

public class CameraDriver extends Thread
{
    private static final int SAMPLES = 10;
    private static final double CHI = 0.01;
    private static final double MINIMUM_SLOPE = 0.01;
    private static final double TIME_THRESH = 0.0;
    private static final int VERBOSITY = -1;
    private static final boolean GUI = false;

    private boolean newImage = false;
    private Object imageLock = new Object();
    
    private boolean run = true;
    private boolean done = false;
    private Object driverLock = new Object();
    
    private int id;
    private String url;
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    private SyncErrorDetector sync;
    
    private byte[] imageBuffer;

    public CameraDriver(String url, Config config) throws ConfigException
    {
        Util.verifyConfig(config);

        run = Util.isValidUrl(config, url);
        if(run)
        {
            this.url = url;
            
            boolean hiRes = config.requireBoolean("hiRes");
            boolean color8 = config.requireBoolean("color8");
            int fps = config.requireInt("fps");
            id = config.getChild(Util.getSubUrl(config, url)).requireInt("id");
            
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
            Util.verifyConfig(config);
            sync = new SyncErrorDetector(config);
        }
    }
    
    public CameraDriver(String url) throws CameraException, IOException, ConfigException
    {
        this(url, true, true, 60);
    }

    public CameraDriver(String url, boolean hiRes, boolean color8, int fps)
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
        sync = new SyncErrorDetector(SAMPLES, CHI, MINIMUM_SLOPE, TIME_THRESH, VERBOSITY, GUI);
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

    public int getCameraId()
    {
        return id;
    }
    
    private void toggleImageSource(ImageSource isrc) throws ConfigException
    {
        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
        sync = new SyncErrorDetector(SAMPLES, CHI, MINIMUM_SLOPE, TIME_THRESH, VERBOSITY, GUI);
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
    
    public void setFramerate(int fps)
    {
        isrc.setFeatureValue(15, fps); // frame-rate, idx=11
    }
    
    public void setFormat(boolean hiRes, boolean color8)
    {
        isrc.stop();
        isrc.setFormat(Integer.parseInt("" + (hiRes ? 0 : 1) + (color8 ? 0 : 1), 2));
        ifmt = isrc.getCurrentFormat();
        isrc.start();
    }
}
