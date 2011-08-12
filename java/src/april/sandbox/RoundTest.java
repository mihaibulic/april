package april.sandbox;

import april.util.ConfigUtil2;

public class RoundTest
{
    public static void main(String[] args)
    {
        int index = "4".indexOf('.');
        System.out.println(index);
        
        System.out.println(4.523132514351 + "\t" + ConfigUtil2.round(4.523132514351, 4));
        System.out.println(4.52 + "\t" + ConfigUtil2.round(4.52, 4));
        System.out.println(4 + "\t" + ConfigUtil2.round(4, 4));
    }

}
