package edu.umich.mihai.sandbox;

import java.awt.Color;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JFrame;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;
import edu.umich.mihai.camera.Camera;
import edu.umich.mihai.camera.Tag;
import edu.umich.mihai.vis.VisCamera;

public class ExtrinsicsSolverTest extends JFrame
{
    private static final long serialVersionUID = 1L;

    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("a");
    
    private Color[] colors = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
    
    public ExtrinsicsSolverTest(Camera[] cameras)
    {
        super("EST");

        int length = 6;
        int size = length * cameras.length;
        Distance func = new Distance(cameras);

        // ideal value of our function. (distances between points in observation)
        double locations[] = new double[size];
        boolean skip[] = new boolean[size];
        double eps[] = new double[size];
        for (int i = 0; i < cameras.length; i++) 
        {
            double[] pos = cameras[i].getXyzrpy();
            for(int o = 0; o < 6; o++)
            {
                locations[i*length+o] = pos[o];
                skip[i*length+o] = false;
                eps[i*length+o] = 0.0001; // units: meters
            }
        }
        
        double[][] J = NumericalJacobian.computeJacobian(func, locations, eps);
        double[][] oldJ = J.clone();
        double[][] oldoldJ = oldJ.clone();
        
        int count = 0;
        do
        {
            count++;
            J = NumericalJacobian.computeJacobian(func, locations, eps);
            
            for (int i = 0; i < locations.length; i++)
            {
                if(!skip[i])
                {
                    int a = i/3;
                    
                    if(Math.abs(J[a][i]) < 0.01)
                    {
                        System.out.println("************"+J[a][i]);
                        skip[i] = true;
                    }
                    if(J[a][i]*oldJ[a][i] < 0 && oldJ[a][i]*oldoldJ[a][i] < 0)
                    {
                        System.out.println("^^^^^^^^^^^");
                        skip[i] = true;
                    }
                    else
                    {
                        locations[i] -= 0.04*J[a][i];
                    }
                }
            }

            oldoldJ = oldJ.clone();
            oldJ = J.clone();
        } while(!shouldStop(skip));
        System.out.println(count);
        
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
    
    private boolean shouldStop(boolean[] skip)
    {
        boolean stop = true;
        
        for(int a = 0; a < skip.length; a++)
        {
            if(!skip[a])
            {
                stop = false;
                break;
            }
        }
            
        return stop;
    }
    
    private void showGui()
    {
        add(vc);
        vb.switchBuffer();
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        setVisible(true);
    }
    
    private double[] matrixab(double[] a, double[] b)
    {
        return LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(a), LinAlg.xyzrpyToMatrix(b)));
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
    
    class Distance extends Function
    {
        Camera[] cameras;;
        HashMap<Integer,double[]>[] tagsH;
        ArrayList<Tag>[] tagsL;
        
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
                for(int t= 0; t < tagsL[c].size(); t++)
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
                                otherTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(cameras[o].getTransformationMatrix(), LinAlg.xyzrpyToMatrix(otherTag)));
                                
                                distance[2*c] += (sq(curTag[0]-otherTag[0])+sq(curTag[1]-otherTag[1])+sq(curTag[2]-otherTag[2]));
                                distance[2*c+1] += (sq(curTag[3]-otherTag[3])+sq(curTag[4]-otherTag[4])+sq(curTag[5]-otherTag[5]));
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
    
    public static void main(String[] args)
    {
        
        double[][] camAct = {{0,0,0,0,0,0}, {0,1,0,0,0,0}};//, {1,0,0,0,0,0}};
        double[][] tagsAct = {{0,0,-2,0,0,0}, {0,1,-2,0,0,0}, {1,0,-2,0,0,0}, {1,1,-2,0,0,0}, {-1,0,-2,0,0,0}};//, 
//                              {0,-1,-2,0,0,0}, {-1,-1,-2,0,0,0}, {0.5,0.5,-2,0,0,0}, {-0.5,-0.5,-2,0,0,0}};
        double[][] camInit = {{.1,0,-.2,0,.05,0}, {0.2,1.05,-0.2,.05,0,.05}};//, {0.8,.1,-.05,.1,0.1,-0.1}};
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
                Tag tag = new Tag(LinAlg.subtract(tagsAct[y], camAct[x]), y);
                tagsH.put(y, LinAlg.subtract(tagsAct[y], camAct[x]));
                tags[x].add(tag);
                
            }
            cameras[x] = new Camera(tags[x], tagsH, camInit[x]);
        }
        
        new ExtrinsicsSolverTest(cameras);
        
    }

}
