package mihai.util;

public class ConfigException extends Exception
{
	private static final long serialVersionUID = 1L;
	
	public static final int INDICES_URL_LENGTH = 1;
	public static final int NULL_CONFIG = 2;
	public static final int INVALID_VARIABLE = 3;
	
	private String msg = "Config Exception: ";
	
	public ConfigException(int error)
	{
		
		switch(error)
		{
			case INDICES_URL_LENGTH:
				msg += "Number of camera URLs and indices do not match";
				break;
			case NULL_CONFIG:
				msg += "Config file is null";
				break;
			case INVALID_VARIABLE:
                msg += "Variable not found";
                break;  
        	default:
        		msg += "Unknown error";
		}
    
	    System.err.println(msg);
	}
	
	public String getErrorMessage()
	{
	    return msg;
	}
}
