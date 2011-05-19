package mihai.util;

public class ConfigException extends Exception
{
	private static final long serialVersionUID = 1L;
	
	public static final int INDICES_URL_LENGTH = 1;
	public static final int NULL_CONFIG = 2;
	
	public ConfigException(int error)
	{
		String msg = "Config Exception: ";
		
		switch(error)
		{
			case INDICES_URL_LENGTH:
				msg += "Number of camera URLs and indices do not match";
				break;
			case NULL_CONFIG:
				msg += "Config file is null";
				break;	
        	default:
        		msg += "Unknown error";
		}
    
		printStackTrace();
	    System.err.println(msg);
	    System.exit(1);
	}
}
