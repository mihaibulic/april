package april.sandbox;

import java.io.IOException;
import april.config.Config;
import april.util.ConfigException;
import april.util.ConfigUtil;

public class StringToke
{
    public StringToke()
    {
        String loc = "/home/april/mihai/config/camera.config";
    
        try
        {
            Config config = ConfigUtil.setValue(loc, new String[]{}, "fps", "250000");
            
            System.out.println(config.requireDouble("fps"));
        } catch (ConfigException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
    }
    public static void main(String[] args)
    {
        new StringToke();
    }
    
}
