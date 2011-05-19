package mihai.util;

import april.config.Config;

public class Util
{
    public static void verifyConfig(Config config) throws ConfigException
    {
        if(config == null || !config.getBoolean("valid", false))
    	{
        	throw new ConfigException(ConfigException.NULL_CONFIG);
    	}
    }
}
