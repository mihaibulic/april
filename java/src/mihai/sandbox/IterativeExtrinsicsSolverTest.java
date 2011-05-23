package mihai.sandbox;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JFrame;

import mihai.camera.Camera;
import mihai.camera.Tag;
import mihai.vis.VisCamera;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

public class IterativeExtrinsicsSolverTest extends JFrame
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("a");
    
    private Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
    
    public IterativeExtrinsicsSolverTest(Camera[] cameras)
    {
        super("EST");
        
        int length = 6;
        double locations[] = calculateItt(cameras);
        
        for(int i = 0; i < cameras.length; i++)
        {
            double[] pos = new double[length];
            for(int j = 0; j < pos.length; j++)
            {
                pos[j] = locations[i*length+j];
                System.out.print(pos[j] + "\t");
            }
            cameras[i].setPosition(pos, 0);
            System.out.println();
            
            vb.addBuffered(new VisChain(cameras[i].getTransformationMatrix(), new VisCamera(colors[i], 0.08)));
            
            for(Tag tag : cameras[i].getTags())
            {
                vb.addBuffered(new VisChain(cameras[i].getTransformationMatrix(), tag.getTransformationMatrix(), new VisRectangle(0.15, 0.15, new VisDataFillStyle(colors[i]))));
            }
        }
        
        showGui();
    }
    
    /* very big improvement to current extrinsics initial solution
     * for(int c = 0; c < cameras.length; c++)
            {
                tagsH[c] = cameras[c].getTagsH();
                tagsL[c] = cameras[c].getTags();
            }
            
            for(int c = 0; c < cameras.length; c++)
            {
                for(int t= 0; t < tagsL[c].size(); t++)
                {
                    for(int o = 0; o < cameras.length; o++)
                    {
                        if(o!=c)
                        {
                            double[] loc = tagsH[o].get(tagsL[c].get(t).getId());
                            if(loc != null)
                            {
                                cameras[c].addPotentialPosition(matrixab(tagsL[c].get(t).getXyzrpy(),LinAlg.scale(loc,-1)));
                            }
                        }
                    }
                }
            }
     */
    
    private void showGui()
    {
        add(vc);
        vb.switchBuffer();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setVisible(true);
    }
    
    public double[] calculateItt(Camera[] cameras)
    {
        double threshold = 0.000001; // experimentally derived
        int ittLimit = 1000;         // experimentally derived
        int length = 6;              // xyzrpy
        int size = length * cameras.length;
        Distance distance = new Distance(cameras);

        double locations[] = new double[size];
        double eps[] = new double[size];
        for (int i = 0; i < cameras.length; i++) 
        {
            double[] pos = cameras[i].getXyzrpy();
            for(int o = 0; o < 6; o++)
            {
                locations[i*length+o] = pos[o];
                eps[i*length+o] = 0.0001; // units: meters
            }
        }
        
        double[] r = LinAlg.scale(distance.evaluate(locations), -1);
        double[] oldR = null;
        
        int count = 0;
        while(!shouldStop(r, oldR, threshold) && ++count < ittLimit)
        {
            double[][] _J = NumericalJacobian.computeJacobian(distance, locations, eps);
            Matrix J = new Matrix(_J);
            
            Matrix JTtimesJplusI = J.transpose().times(J).plus(Matrix.identity(size, size));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTtimesJplusI.solve(JTr);
            
            for (int i = 0; i < locations.length; i++)
            {
                locations[i] += 0.1*dx.get(i,0); // 0.1 helps stabilize results
            }
            
            oldR = r.clone();
            r = LinAlg.scale(distance.evaluate(locations), -1);
        }
        
        return locations;
    }
    
    private boolean shouldStop(double[] r, double[] oldR, double threshold)
    {
        boolean stop = true;
        int length = 2;
        
        if(oldR != null)
        {
            for(int x = 0; x < r.length/length; x++)
            {
                double[] cur = new double[length];
                double[] old = new double[length];
                for(int y = 0; y < length; y++)
                {
                    cur[y] = r[x*length+y];
                    old[y] = oldR[x*length+y];
                }
                
                if(LinAlg.distance(cur, old) > threshold)
                {
                    stop = false;
                    break;
                }
            }
        }
        else
        {
            stop = false;
        }
        
        return stop;
    }
    
    class Distance extends Function
    {
        Camera[] cameras;;
        HashMap<Integer,double[]>[] tagsH;
        ArrayList<Tag>[] tagsL;
        
        @SuppressWarnings("unchecked")
        public Distance(Camera[] cameras)
        {
            this.cameras = cameras;
            tagsH = new HashMap[cameras.length];
            tagsL = new ArrayList[cameras.length];
            
            for(int c = 0; c < cameras.length; c++)
            {
                tagsH[c] = cameras[c].getTagsH();
                tagsL[c] = cameras[c].getTags();
            }
        }
        
        @Override
        public double[] evaluate(double[] location)
        {
            return evaluate(location, null);
        }
        
        @Override
        public double[] evaluate(double[] location, double[] distance)
        {
            int length = 6;
            
            if(distance == null)
            {
                distance = new double[location.length/3];
                for(int a = 0; a < distance.length; a++)
                {
                    distance[a] = 0;
                }
            }
            
            for(int c = 0; c < cameras.length; c++)
            {
                int count = 0;
                double[] curCamera = new double[length];
                for(int x = 0; x < length; x++)
                {
                    curCamera[x] = location[c*length+x];
                }
                for(int t = 0; t < tagsL[c].size(); t++)
                {
                    double[] curTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(curCamera), tagsL[c].get(t).getTransformationMatrix()));
                    for(int o = 0; o < cameras.length; o++)
                    {
                        if(o!=c)
                        {
                            double[] otherTag = tagsH[o].get(tagsL[c].get(t).getId());
                            if(otherTag != null)
                            {
                                count++;
                                
                                double[] otherCamera = new double[length];
                                for(int x = 0; x < length; x++)
                                {
                                    otherCamera[x] = location[o*length+x];
                                }
                                otherTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(otherCamera), LinAlg.xyzrpyToMatrix(otherTag)));
                                
                                distance[2*c] += Math.sqrt(sq(curTag[0]-otherTag[0])+sq(curTag[1]-otherTag[1])+sq(curTag[2]-otherTag[2]));
                                distance[2*c+1] += Math.sqrt(sq(curTag[3]-otherTag[3])+sq(curTag[4]-otherTag[4])+sq(curTag[5]-otherTag[5]));
                            }
                        }
                    }
                }
                if(count != 0)
                {
                    distance[c*2] /= count;
                    distance[c*2+1] /= count;
                }
            }
            
            return distance;
        }
        
        public double sq(double a)
        {
            return a*a;
        }
    }  
    
    @SuppressWarnings("unchecked")
    public static void main(String[] args)
    {
        
        double[][] camAct = {{0,0,0,0,0,0}, {0,1,0,0,0,0}, {1,0,0,0,0,0}};
        double[][] tagsAct = {{0,0,-2,0,0,0}, {0,1,-2,0,0,0}, {1,0,-2,0,0,0}, {1,1,-2,0,0,0}, {-1,0,-2,0,0,0}, 
                              {0,-1,-2,0,0,0}, {-1,-1,-2,0,0,0}, {0.5,0.5,-2,0,0,0}, {-0.5,-0.5,-2,0,0,0}};
        double[][] camInit = {{.1,3,-.2,0,.05,0}, {0.2,1.05,-0.2,.05,0,.05}, {0.8,.1,-.05,.1,0.1,-0.1}};
//        double[][] camInit = camAct;
        
        int numOfCams = camAct.length;
        int numOfTags = tagsAct.length;
        
        Camera[] cameras = new Camera[numOfCams];
        ArrayList<Tag>[] tags = new ArrayList[numOfCams];
        
        for(int x = 0; x < numOfCams; x++)
        {
            tags[x] = new ArrayList<Tag>();
            HashMap<Integer, double[]> tagsH = new HashMap<Integer,double[]>();
            
            for(int y = 0; y < numOfTags; y++)
            {
                double[] tagPos = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.inverse(LinAlg.xyzrpyToMatrix(camAct[x])), LinAlg.xyzrpyToMatrix(tagsAct[y])));
                
                Tag tag = new Tag(tagPos, y);
                tagsH.put(y, tagPos);
                tags[x].add(tag);
                
            }
            cameras[x] = new Camera(tags[x], tagsH, camInit[x]);
        }
        
        new IterativeExtrinsicsSolverTest(cameras);
    }

}
