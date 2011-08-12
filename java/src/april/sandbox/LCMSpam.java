package april.sandbox;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import exlcm.example_t;
import lcm.lcm.LCM;
import april.lcmtypes.lcm_spam_t;
import april.util.TimeUtil;

public class LCMSpam
{
    private LCM lcm = LCM.getSingleton();
    
    public LCMSpam(String channel, String output1, String output, int freq )
    {
        int pause = 1000/freq;
        lcm_spam_t spam = new lcm_spam_t();
//        example_t a = new example_t();
        
//        while(true)
//        {
//            a.ranges = new short[] {1,2};
//            a.name = "EXAMPLE";
//            a.enabled = true;
//            a.num_ranges = 2;
//            a.orientation = new double[] {0,0,0,0};
//            a.position = new double[] {0,0,0};
//            a.timestamp = TimeUtil.utime();
//            lcm.publish("EXAMPLE", a);

            spam.data = output1;
            lcm.publish(channel, spam);
            System.out.println("*" + output1);
            TimeUtil.sleep(2000);
            
            spam = new lcm_spam_t();
            spam.data = output;
            lcm.publish(channel, spam);
            System.out.println("*" + output);
            TimeUtil.sleep(1000);
//        }
    }
    
    public static void main(String[] args)
    {
        if(args.length == 4)
        {
            new LCMSpam(args[0], args[1], args[2], Integer.parseInt(args[3]));
        }
        else if(args.length == 0)
        {
            new LCMSpam("pandaIn", "panda001://open?", "panda001://get?", 15);
        }
        else
        {
            throw new RuntimeException
            (
                "need 0 or 4 (channel name, 1 time message string, repeating message string, and frequency) args"
            );
        }
    }

}
