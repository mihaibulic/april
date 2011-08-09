package april.sandbox;

import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.SingularValueDecomposition;

public class MatrixText
{
    public MatrixText()
    {
        double[][] aa = {{4000,3,2},
                         {2,3,5},
                         {0,0,0}};
        
        SingularValueDecomposition svd = new SingularValueDecomposition(new Matrix(aa));
        double[][] bb = LinAlg.add(aa, scaledIdentityMatrix(3, max(svd.getSingularValues())));
        
        
        int rank = svd.rank();
        double[][] S = svd.getS().copyArray();
        double[][] U = svd.getU().copyArray();
        double[][] V = svd.getV().copyArray();
        
        for(int x = 0; x < aa.length; x++)
        {
            for(int y = 0; y < aa[0].length; y++)
            {
                System.out.print(aa[x][y] + "\t");
            }
            System.out.println();
        }
        System.out.println("\n");
        
        for(int x = 0; x < bb.length; x++)
        {
            for(int y = 0; y < bb[0].length; y++)
            {
                System.out.print(bb[x][y] + "\t");
            }
            System.out.println();
        }
        
        System.out.println("rank: " + rank + "\n");
        
        System.out.println("S:");
        for(int x = 0; x < S.length; x++)
        {
            System.out.println(S[x][x]);
        }
        System.out.println();
        
        System.out.println("U:");
        for(int x = 0; x < U.length; x++)
        {
            for(int y = 0; y < U[0].length; y++)
            {
                System.out.print(U[x][y] + "\t");
            }
            System.out.println();
        }
        System.out.println();
        
        System.out.println("V:");
        for(int x = 0; x < V.length; x++)
        {
            for(int y = 0; y < V[0].length; y++)
            {
                System.out.print(V[x][y] + "\t");
            }
            System.out.println();
        }
        
    }
    
    private double[][] scaledIdentityMatrix(int size, double scale)
    {
        return (LinAlg.scale(Matrix.identity(size, size).copyArray(), scale));
    }
    
    private double max(double[] values)
    {
        double max = Double.MIN_VALUE;
        
        for(double v : values)
        {
            if(v > max)
            {
                max = v;
            }
        }
        
        return max;
    }
    
    
    public static void main(String[] args)
    {
        new MatrixText();
    }

}
