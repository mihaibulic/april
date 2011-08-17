package april.sandbox;

import java.util.Random;

public class SqrtTest
{

    public static void main(String[] args)
    {
        Random r = new Random();
        int size = 1000000;
        double[] input = new double[size];
        double[] output = new double[size];
        
        for(int x = 0; x < input.length; x++)
        {
            input[x] = r.nextDouble()*10000;
            output[x] = 0;
        }
        
        long time = System.currentTimeMillis();
        for(int x = 0; x < output.length; x++)
        {
            output[x] = Math.sqrt(input[x]);
        }
        long time2 = System.currentTimeMillis();
        
        System.out.println("*" + (time2-time));
    }

}
