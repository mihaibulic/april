package april.sandbox;

import java.io.File;
import java.io.IOException;
import april.camera.CameraDriver;
import april.camera.util.CameraException;
import april.config.Config;
import april.config.ConfigFile;
import april.util.ConfigException;

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
