package mihai.sandbox;

import april.jmat.LinAlg;

public class Linalgtest
{
    private static double[] matrixab(double[] a, double[] b)
    {
        double[] c = new double[a.length];
        
        for(int x = 0; x < a.length; x++)
        {
            c[x] = b[x] - a[x];
        }
        
        return c;
    }
    
    public static void main(String[] args)
    {
        double[] a = {0,1,2,3,4,5};
        double[] b = {-1,3,5,8,2,-3};
        
        double[] c = matrixab(a,b);
        double[] cc = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(a), LinAlg.xyzrpyToMatrix(b)));
        
        for(int x = 0; x < 6; x++)
        {
            if(c[x] != cc[x])
            {
                System.out.println(x + "\t" + c[x] + "\t" + cc[x]);
            }
        }
    }
    
    
}
