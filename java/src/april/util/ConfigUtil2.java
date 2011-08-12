package april.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.StringTokenizer;
import april.config.Config;
import april.config.ConfigFile;

public class ConfigUtil2
{
    
    /**
     * WARNING: if value is < 0.001 it will be written as 0 to avoid scientific notation
     * 
     *ex: lets say we want to set root->child2->kid3->v1 to {1, 2, 3} in the following config 
     *      structure which is in a config file located under /home/april/test.config
     *                  root
     *                  /   \
     *            child1    child2
     *            /    \    /    \
     *       kid1    kid2  kid3   kid4
     *       /  \    / \   / \    / \
     *     v1   v2  v1 v2 v1 v2  v1 v2       
     * 
     * @param configPath - The filepath to the config file to be modified
     *         ex: "/home/april/test.config"
     * @param path - the path internal to the config file to find the variable to be changed
     *         make array of size 0 if it is in root
     *         ex: {"child2", "kid3"}
     * @param variable - the variable name that will be changed
     *         ex: "v1"
     * @param value - the new value for the variable to be changed
     *         ex: {1, 2, 3}
     * @return - the new Config which contains the new variable value
     * @throws ConfigException
     * @throws IOException
     */
    public static Config setValues(String configPath, String[] path, String variable, double[] value) throws ConfigException, IOException
    {
        String[] stringValue = new String[value.length];
        for(int x = 0; x < stringValue.length; x++)
        {
            if(Math.abs(value[x]) < 0.001)
                value[x] = 0;
            
            stringValue[x] = round(value[x],4);
        }
        
        return setValues(configPath, path, variable, stringValue);
    }
    
    /**
     * WARNING: if value is < 0.001 it will be written as 0 to avoid scientific notation
     * 
     *ex: lets say we want to set root->child2->kid3->v1 to 1 in the following config 
     *      structure which is in a config file located under /home/april/test.config
     *                  root
     *                  /   \
     *            child1    child2
     *            /    \    /    \
     *       kid1    kid2  kid3   kid4
     *       /  \    / \   / \    / \
     *     v1   v2  v1 v2 v1 v2  v1 v2       
     * 
     * @param configPath - The filepath to the config file to be modified
     *         ex: "/home/april/test.config"
     * @param path - the path internal to the config file to find the variable to be changed
     *         make array of size 0 if it is in root
     *         ex: {"child2", "kid3"}
     * @param variable - the variable name that will be changed
     *         ex: "v1"
     * @param value - the new value for the variable to be changed
     *         ex: 1
     * @return - the new Config which contains the new variable value
     * @throws ConfigException
     * @throws IOException
     */
    public static Config setValue(String configPath, String[] path, String variable, double value) throws ConfigException, IOException
    {
        if(Math.abs(value) < 0.001)
            value = 0;
        
        return setValue(configPath, path, variable, round(value,4));
    }
    
    /**
     *ex: lets say we want to set root->child2->kid3->v1 to ":)" in the following config 
     *      structure which is in a config file located under /home/april/test.config
     *                  root
     *                  /   \
     *            child1    child2
     *            /    \    /    \
     *       kid1    kid2  kid3   kid4
     *       /  \    / \   / \    / \
     *     v1   v2  v1 v2 v1 v2  v1 v2       
     * 
     * @param configPath - The filepath to the config file to be modified
     *         ex: "/home/april/test.config"
     * @param path - the path internal to the config file to find the variable to be changed
     *         make array of size 0 if it is in root
     *         ex: {"child2", "kid3"}
     * @param variable - the variable name that will be changed
     *         ex: "v1"
     * @param value - the new value for the variable to be changed
     *         ex: ":)"
     * @return - the new Config which contains the new variable value
     * @throws ConfigException
     * @throws IOException
     */
    public static Config setValue(String configPath, String[] path, String variable, String value) throws ConfigException, IOException
    {
        return setValues(configPath, path, variable, new String[]{value});
    }
    
    /**
     *ex: lets say we want to set root->child2->kid3->v1 to "{ ":)", ":(", ":*", ";)" }" 
     *      in the following config structure which is in a config file located 
     *      under /home/april/test.config
     *                  root
     *                  /   \
     *            child1    child2
     *            /    \    /    \
     *       kid1    kid2  kid3   kid4
     *       /  \    / \   / \    / \
     *     v1   v2  v1 v2 v1 v2  v1 v2       
     * 
     * @param configPath - The filepath to the config file to be modified
     *         ex: "/home/april/test.config"
     * @param path - the path internal to the config file to find the variable to be changed
     *         make array of size 0 if it is in root
     *         ex: {"child2", "kid3"}
     * @param variable - the variable name that will be changed
     *         ex: "v1"
     * @param value - the new value for the variable to be changed
     *         ex: "{ ":)", ":(", ":*", ";)" }"
     * @return - the new Config which contains the new variable value
     * @throws ConfigException
     * @throws IOException
     */
    public static Config setValues(String configPath, String[] path, String variable, String[] value) throws ConfigException, IOException
    {
        int index = 0;
        int depth = 0;
        boolean found = false;
        
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(configPath))));
            
            String strLine = null;
            ArrayList<String> file = new ArrayList<String>();
            while ((strLine = in.readLine()) != null)
            {
                if(!found)
                {
                    String copy = strLine;
                    if(copy.contains("//"))
                    {
                        copy = strLine.substring(0, strLine.indexOf("//"));
                    }

                    if(copy.contains("{"))
                    {
                        depth++;
                        StringTokenizer st = new StringTokenizer(copy, "[=;\t\n{} "); 
                        while(st.hasMoreTokens())
                        { 
                            if(index < path.length && st.nextToken().equals(path[index]))
                            {
                                index++;
                            }
                        }
                    }
                    else if(copy.contains("}"))
                    {
                        depth--;
                    }
                    else if(copy.contains(variable) && index == path.length && depth == path.length)
                    {
                        StringTokenizer st = new StringTokenizer(copy, "[=;\t\n{} "); 
                        while(st.hasMoreTokens())
                        { 
                            String key = st.nextToken(); 
                            if(key.equals(variable))
                            {
                                found = true;
                                strLine = setLine(value, strLine);
                                break;
                            }
                        }
                    }
                }
                file.add(strLine);
            }
            in.close();
            File f = new File(configPath);
            f.delete();
            
            BufferedWriter out = new BufferedWriter(new FileWriter(configPath));
            for(String o : file)
            {
                out.write(o + "\n");
            }
            out.close();
        } catch (IOException e)
        {
            e.printStackTrace();
        }
        
        if(!found)
        {
            System.out.println(index + "\t" + depth);
            throw new ConfigException(ConfigException.INVALID_VARIABLE);
        }
        
        return new ConfigFile(configPath);
    }

    private static String setLine(String[] value, String strLine)
    {
        if(value.length > 1)
        {
            String front = strLine.substring(0, strLine.indexOf("[")+1);
            String back  = strLine.substring(strLine.indexOf("]"), strLine.length());
            strLine = front;
            for(int x = 0; x < value.length; x++)
            {
                strLine += value[x] + ((x < value.length-1) ? ", " : ""); 
            }
            strLine += back;
        }
        else
        {
            String front = strLine.substring(0, strLine.indexOf("=")+1);
            String back  = strLine.substring(strLine.indexOf(";"), strLine.length());
            strLine = front + " " + value[0] + " " + back;
        }
        
        return strLine;
    }
    
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
     * Used to round numbers to be written to a config file
     */
    // XXX - move to a separate config file when more general util methods are available
    public static String round(double number, int decimals) 
    {
        String format = "#.";
        for(int x = 0; x < decimals; x++)
        {
            format += "#";
        }
        
        DecimalFormat twoDForm = new DecimalFormat(format);
        String newNumber = twoDForm.format(number);

        int index = newNumber.indexOf('.');
        if(index == -1)
        {
            index = newNumber.length();
            newNumber += ".";
        }
        
        int itt = decimals - (newNumber.length()-(index+1));
        for(int x = 0; x < itt; x++)
        {
            newNumber += "0";
        }
        
        return newNumber;
    }
}
