package edu.umich.mihai.camera;

import java.util.ArrayList;
import april.jmat.LinAlg;

public class Tag
{
    private double[] xyzrpy;
    private double[][] matrix;
    private int id;
    
    public ArrayList<Integer> spotters = new ArrayList<Integer>();
    
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
    
    public int getId()
    {
        return id;
    }
    
    public double[] getXyzrpy()
    {
        return xyzrpy;
    }
    
    public double[][] getTransformationMatrix()
    {
        return matrix;
    }
    
    public void addSpotter(int id)
    {
        spotters.add(id);
    }
}
