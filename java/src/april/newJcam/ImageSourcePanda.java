package april.newJcam;

import java.io.IOException;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import lcm.spy.LCMTypeDatabase;
import april.lcmtypes.panda_frame_t;
import april.lcmtypes.panda_get_frame_t;
import april.lcmtypes.panda_get_info_t;
import april.lcmtypes.panda_info_t;
import april.lcmtypes.panda_start_t;
import april.lcmtypes.panda_stop_t;
import april.util.TimeUtil;

public class ImageSourcePanda extends ImageSource implements LCMSubscriber
{
    LCMTypeDatabase handlers = new LCMTypeDatabase();
    private LCM lcm = LCM.getSingleton();
    private String channel;

    private panda_info_t info;
    private Object infoLock = new Object();

    private byte[] imageBuffer;
    private boolean newImage = false;
    private Object imageLock = new Object();

    public ImageSourcePanda(String channel)
    {
        this.channel = channel;

        panda_get_info_t info = new panda_get_info_t();
        info.utime = TimeUtil.utime();
        lcm.publish(channel, info);
    }

    @Override
    public int close()
    {
        panda_stop_t close = new panda_stop_t();
        close.utime = TimeUtil.utime();

        lcm.publish(channel, close);
        return 0;
    }

    @Override
    public int getCurrentFormatIndex()
    {
        int index = 0;

        synchronized (infoLock)
        {
            index = info.format_index;
        }

        return index;
    }

    @Override
    public ImageSourceFormat getFormat(int idx)
    {
        ImageSourceFormat ifmt = new ImageSourceFormat();

        synchronized (infoLock)
        {
            ifmt.width = info.width[idx];
            ifmt.height = info.height[idx];
            ifmt.format = info.format[idx];
        }

        return ifmt;
    }

    @Override
    public byte[] getFrame()
    {
        byte[] buffer = null;
        panda_get_frame_t get = new panda_get_frame_t();
        get.utime = TimeUtil.utime();
        lcm.publish(channel, get);

        synchronized (imageLock)
        {
            while (!newImage)
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
            buffer = imageBuffer;
        }

        return buffer;
    }

    @Override
    public int getNumFormats()
    {
        int number = 0;

        synchronized (infoLock)
        {
            number = info.num_of_formats;
        }

        return number;
    }

    @Override
    public void printInfo()
    {}

    public int getNumFeatures()
    {
        int number = 0;

        synchronized (infoLock)
        {
            number = info.num_of_features;
        }

        return number;
    }

    public String getFeatureName(int idx)
    {
        String name = null;

        synchronized (infoLock)
        {
            name = info.feature_names[idx];
        }

        return name;
    }

    public double getFeatureMin(int idx)
    {
        double min = 0;

        synchronized (infoLock)
        {
            min = info.feature_min[idx];
        }

        return min;
    }

    public double getFeatureMax(int idx)
    {
        double max = 0;

        synchronized (infoLock)
        {
            max = info.feature_max[idx];
        }

        return max;
    }

    public double getFeatureValue(int idx)
    {
        double value = 0;

        synchronized (infoLock)
        {
            value = info.feature_values[idx];
        }

        return value;
    }

    @Override
    public void setFormat(int idx)
    {
        synchronized (infoLock)
        {
            info.format_index = idx;
            
            lcm.publish(channel, info);
        }
    }

    public int setFeatureValue(int idx, double v)
    {
        synchronized (infoLock)
        {
            info.feature_values[idx] = v;
            
            lcm.publish(channel, info);
        }
        return 0;
    }

    @Override
    public void start()
    {
        panda_start_t start = new panda_start_t();
        start.utime = TimeUtil.utime();

        lcm.publish(channel, start);
    }

    @Override
    public void stop()
    {
        panda_stop_t stop = new panda_stop_t();
        stop.utime = TimeUtil.utime();

        lcm.publish(channel, stop);
    }

    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        if (channel.equals(channel))
        {
            synchronized (imageLock)
            {
                String type = null;
                try
                {
                    int msg_size = ins.available();
                    long fingerprint = (msg_size >= 8) ? ins.readLong() : -1;
                    type = handlers.getClassByFingerprint(fingerprint).getName();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }

                if (type.equals("panda_frame_t"))
                {
                    try
                    {
                        imageBuffer = new panda_frame_t(ins).image;
                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                    newImage = true;
                    imageLock.notify();
                }
                else if (type.equals("panda_info_t"))
                {
                    try
                    {
                        panda_info_t info = new panda_info_t(ins);
                        synchronized (infoLock)
                        {
                            this.info = info;
                        }

                    } catch (IOException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
