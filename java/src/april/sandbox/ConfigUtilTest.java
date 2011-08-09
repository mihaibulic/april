package april.sandbox;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import april.config.Config;
import april.config.ConfigFile;
import april.util.ConfigException;

public class ConfigUtilTest
{
    /**
     * same as setValue except this is used when the variable to be set is an array
     */
    public static Config setValues(String configPath, String[] path, String variable, String[] value) throws ConfigException, IOException
    {
        int index = 0;
        boolean found = false;
        
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(configPath))));
            
            String strLine = null;
            ArrayList<String> file = new ArrayList<String>();
            while ((strLine = in.readLine()) != null)   
            {
                String copy = strLine;
                if(copy.contains("//"))
                {
                    copy = strLine.substring(0, strLine.indexOf("//"));
                }

                if(index < path.length)
                {
                    if(copy.contains(path[index]))
                    {
                        index++;
                    }
                }
                else if(copy.contains(variable))
                {
                    String[] split = strLine.split("[=,;, ,\t,\n]");
                    for(String token : split)
                    {
                        if(token.equals(variable))
                        {
                            found = true;
                            String front = strLine.substring(0, strLine.indexOf("[")+1);
                            String back  = strLine.substring(strLine.indexOf("]"), strLine.length());
                            strLine = front;
                            for(int x = 0; x < value.length; x++)
                            {
                                strLine += value[x] + ((x < value.length-1) ? ", " : ""); 
                            }
                            strLine += back;
                            break;
                        }
                    }
                }
                file.add(strLine);
            }
            in.close();
            File f = new File(System.getenv("CONFIG")+"/camera.config");
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
            throw new ConfigException(ConfigException.INVALID_VARIABLE);
        }
        
        return new ConfigFile(configPath);
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
        int index = 0;
        boolean found = false;
        
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(new DataInputStream(new FileInputStream(configPath))));
            
            String strLine = null;
            ArrayList<String> file = new ArrayList<String>();
            while ((strLine = in.readLine()) != null)   
            {
                String copy = strLine;
                if(copy.contains("//"))
                {
                    copy = strLine.substring(0, strLine.indexOf("//"));
                }

                if(index < path.length)
                {
                    if(copy.contains(path[index]))
                    {
                        index++;
                    }
                }
                else if(copy.contains(variable))
                {
                    String[] split = strLine.split("[=,;, ,\t,\n]");
                    for(String token : split)
                    {
                        if(token.equals(variable))
                        {
                            found = true;
                            String front = strLine.substring(0, strLine.indexOf("=")+1);
                            String back  = strLine.substring(strLine.indexOf(";"), strLine.length());
                            strLine = front + " " + value + back;
                            break;
                        }
                    }
                }
                file.add(strLine);
            }
            in.close();
            File f = new File(System.getenv("CONFIG")+"/camera.config");
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
            throw new ConfigException(ConfigException.INVALID_VARIABLE);
        }
        
        return new ConfigFile(configPath);
    }
}
