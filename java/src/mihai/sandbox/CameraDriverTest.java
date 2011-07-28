package mihai.sandbox;

import java.io.File;
import java.io.IOException;
import mihai.camera.CameraDriver;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.config.Config;
import april.config.ConfigFile;

public class CameraDriverTest
{
    public static void main(String[] args)
    {
        try
        {
            Config c = new ConfigFile(System.getenv("APRIL_CONFIG")+ File.separator + "camera.config");
            
            CameraDriver cd = new CameraDriver("dc1394://b09d01008b51b8",c);
            cd.start();
            Thread.sleep(1000);
            cd.setFormat("GRAY16", 752, 480);
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        } catch (InterruptedException e)
        {
            e.printStackTrace();
        } catch (CameraException e)
        {
            e.printStackTrace();
        }
    }
}
