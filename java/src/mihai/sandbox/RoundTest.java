package mihai.sandbox;

import mihai.util.ConfigUtil;

public class RoundTest
{
    public static void main(String[] args)
    {
        int index = "4".indexOf('.');
        System.out.println(index);
        
        System.out.println(4.523132514351 + "\t" + ConfigUtil.round(4.523132514351, 4));
        System.out.println(4.52 + "\t" + ConfigUtil.round(4.52, 4));
        System.out.println(4 + "\t" + ConfigUtil.round(4, 4));
    }

}
