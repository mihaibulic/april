package mihai.util;

import april.config.Config;

public class Util
{
    /**
     * Verifies a given node of the config file is valid
     */
    public static void verifyConfig(Config config) throws ConfigException
    {
        if(config == null || !config.getBoolean("valid", false))
    	{
        	throw new ConfigException(ConfigException.NULL_CONFIG);
    	}
    }

    /**
     * Verifies if the given URL is valid
     */
    public static boolean isValidUrl(Config config, String url)
    {
        config = config.getChild(getSubUrl(config, url));
        return (config != null && config.getBoolean("valid", false));
    }
    
    /**
     * given the URL of a camera, it returns the suburl which corresponds to an entry in the config file
     *   It does this simply by removing the default prefix of the url
     */
    public static String getSubUrl(Config config, String url)
    {
        String prefix = config.requireString("default_url");
        
        if(url.contains(prefix))
        {
            url = url.substring(url.indexOf(prefix) + prefix.length());
        }

        return url;
    }
}
