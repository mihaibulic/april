package april.camera;

import java.awt.image.BufferedImage;
import java.io.IOException;
import magic.camera.util.SyncErrorDetector;
import april.camera.util.CameraException;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;

public class CameraDriver extends Thread
{
    private int samples = 10;
    private double chi2Tolerance = 0.001;
    private double minimumSlope = 0.01;
    private double timeThresh = 0.0;
    private int verbosity = -1;
    private boolean gui = false;
    private SyncErrorDetector sync = null;

    private String id = "";
    private String url = "";
    private ImageSource isrc;
    private ImageSourceFormat ifmt;
    
    private boolean newImage = false;
    private Object imageLock = new Object();
    private byte[] imageBuffer;

    private boolean run = true;

    public CameraDriver(String url)
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
    }

    public CameraDriver(String url, Config config) throws ConfigException
    {
        run = isValidUrl(config, url);

        if(run)
        {
            config = config.getRoot().getChild(getSubUrl(url));
            this.url = url;
            
            id = config.requireString("id");
            String format = config.getString("format", "GRAY8");
            int width = config.getInt("width", 752);
            int height = config.getInt("height", 480);
            int fps = config.getInt("fps", 60);

            try
            {
                isrc = ImageSource.make(url);
                setImageSource(format, width, height, fps);
            } catch (IOException e)
            {
                e.printStackTrace();
            } catch (CameraException e)
            {
                e.printStackTrace();
            }
            
            ifmt = isrc.getCurrentFormat();
            
            config = config.getRoot().getChild("sync");
            ConfigUtil2.verifyConfig(config);
            samples = config.requireInt("samples");
            chi2Tolerance = config.requireDouble("chi2Tolerance");
            timeThresh = config.requireDouble("timeThresh");
            minimumSlope = config.requireDouble("minimumSlope");
            verbosity = config.requireInt("verbosity");
            gui = config.requireBoolean("gui");
            sync = new SyncErrorDetector(samples, chi2Tolerance, minimumSlope, timeThresh, verbosity, gui);
        }
    }

    public CameraDriver(String url, String format, int width, int height, int fps,
            int samples, double chi, double minimumSlope, double timeThresh, int verbosity, boolean gui)
    {
        this.url = url;

        try
        {
            isrc = ImageSource.make(url);
            setImageSource(format, width, height, fps);
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (CameraException e)
        {
            e.printStackTrace();
        }

        ifmt = isrc.getCurrentFormat();

        sync = new SyncErrorDetector(samples, chi, minimumSlope, timeThresh, verbosity, gui);
    }
    
    public void run()
    {
        isrc.start();
        byte[] imageBuffer;
        
        while (run)
        {
            imageBuffer = isrc.getFrame();

            if(imageBuffer != null)
            {
                int status = SyncErrorDetector.SYNC_GOOD;
                if(sync != null)
                {
                    sync.addTimePointGreyFrame(imageBuffer);
                    status = sync.verify();
                }
                
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
                    toggleImageSource();
                }
            }
            else
            {
                toggleImageSource();
            }
        }
        isrc.stop();
    }
    
    private void toggleImageSource()
    {
        int f = isrc.getCurrentFormatIndex();
        isrc.stop();
        isrc.setFormat(f);
        isrc.start();
        
        sync = new SyncErrorDetector(samples, chi2Tolerance, minimumSlope, timeThresh, verbosity, gui);
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
    
    public static String getSubUrl(String url)
    {
        url = url.replaceAll(":///", "-");
        url = url.replaceAll("://", "-");
        url = url.replaceAll("[^a-zA-Z 0-9]", "-");
        
        return url;
    }
    
    public void kill()
    {
        run = false;

        try
        {
            this.join();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    
    public boolean isGood()
    {
        return run;
    }
    
    public static boolean isValidUrl(Config config, String url)
    {
        Config c = config.getRoot().getChild(getSubUrl(url));
        
        return (c != null && c.getBoolean("valid", false) && ImageSource.getCameraURLs().contains(url));
    }
    
    private void setImageSource(String format, int width, int height, int fps) throws CameraException
    {
        if(isrc == null) throw new CameraException(CameraException.NULL_IMAGESOURCE);
        
        setFormat(format, width, height);

        for(int x = 0; x < isrc.getNumFeatures(); x++)
        {
            String name = isrc.getFeatureName(x);
            
            if(name.equals("frame-rate-manual"))
            {
                isrc.setFeatureValue(x, 1);
            }
            else if(name.equals("frame-rate"))
            {
                isrc.setFeatureValue(x, fps);
            }
            else if(name.equals("timestamps-enable"))
            {
                isrc.setFeatureValue(x, 1);
            }
        }
    }

    public void setFormat(String format, int width, int height) throws CameraException
    {
        if(isrc == null) throw new CameraException(CameraException.NULL_IMAGESOURCE);
        
        isrc.stop();
        
        int formats = isrc.getNumFormats();
        for(int x = 0; x < formats; x++)
        {
            ImageSourceFormat f = isrc.getFormat(x);
            if(f.format.contains(format) && f.width == width && f.height == height)
            {
                isrc.setFormat(x);
                break;
            }
        }
        
        ifmt = isrc.getCurrentFormat();
    }

    public void setFramerate(int fps)
    {
        for(int x = 0; x < isrc.getNumFeatures(); x++)
        {
            String name = isrc.getFeatureName(x);
            
            if(name.equals("frame-rate-manual"))
            {
                isrc.setFeatureValue(x, 1);
            }
            else if(name.equals("frame-rate"))
            {
                isrc.setFeatureValue(x, fps);
            }
        }
    }
    
    public void setFeature(String feature, int value)
    {
        for(int x = 0; x < isrc.getNumFeatures(); x++)
        {
            String name = isrc.getFeatureName(x);
            
            if(name.equals(feature))
            {
                isrc.setFeatureValue(x, value);
            }
        }

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
