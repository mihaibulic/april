package mihai.calibration;

import april.jmat.LinAlg;

public class Tag
{
    public double[] xyzrpy;
    public double[][] matrix;
    public int id;
    
    public Tag(double[] xyzrpy, double[][] matrix, int id)
    {
        this.matrix = matrix;
        this.xyzrpy = xyzrpy;
        this.id = id;
    }
    
    public Tag(double[][] matrix, int id)
    {
        this.matrix = matrix;
        this.xyzrpy = LinAlg.matrixToXyzrpy(matrix);
        this.id = id;
    }
    
    public Tag(double[] xyzrpy, int id)
    {
        this.xyzrpy = xyzrpy;
        this.matrix = LinAlg.xyzrpyToMatrix(xyzrpy);
        this.id = id;
    }
    
    @Override
    public boolean equals(Object a)
    {
        return (id == ((Tag)a).id);
    }
}
