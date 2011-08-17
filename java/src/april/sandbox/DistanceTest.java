package april.sandbox;

import april.jmat.LinAlg;

public class DistanceTest
{
    public DistanceTest()
    {
        double[] camera = {0,0,0,0,0,0};
        double[] end = getVector(camera);
        double[] point = {0,0,1};
        
        double a = LinAlg.distance(point, end, 3), b = LinAlg.distance(point, camera, 3), c = 100;
        double s = (a+b+c)/2;
        double area = Math.sqrt(s*(s-a)*(s-b)*(s-c));
        
        double distance = 2*area/c;
        
        System.out.println(distance);
    }
    
    public double[] getVector(double[] start)
    {
        double[] fc = {477,477};
        double[] cc = {752/2,480/2};
        double[] point = {100,200};

        double[][] vector = LinAlg.matrixAB(
                                LinAlg.matrixAB(
                                        LinAlg.rotateY(-1*Math.atan((point[0]-cc[0])/fc[0])), 
                                        LinAlg.rotateX(-1*Math.atan((point[1]-cc[1])/fc[1]))), 
                                LinAlg.translate(new double[]{0,0,100}));
        
        for(int r= 0; r < 4; r++)
        {
            for(int c = 0; c < 4; c++)
            {
                System.out.print(vector[r][c] + "\t\t");
            }
            System.out.println("");
        }
        
        return LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(start), vector));
    }
    
    public static void main(String[] args)
    {
        new DistanceTest();
    }

}
