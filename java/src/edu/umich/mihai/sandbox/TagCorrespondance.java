package edu.umich.mihai.sandbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Random;
import javax.swing.JFrame;
import april.config.Config;
import april.config.ConfigFile;
import april.jmat.LinAlg;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;
import edu.umich.mihai.camera.Camera;
import edu.umich.mihai.util.CameraException;
import edu.umich.mihai.vis.VisCamera;

public class TagCorrespondance
{
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbCameras = vw.getBuffer("cameras");
    private VisWorld.Buffer vbTags = vw.getBuffer("tags");

    private ArrayList<Camera> cameras;
    
    double auxTags[][] = {{1.031,0,-3,0,0,0}, // a
            {1,1.08,-3,0,0,0}, // b
            {1,-1,-3,0.55,0,0}, // c
            {2,0,-3,0,0,0}, // d
            {0,0,-3,.05,0,0.1}}; // e
    double mainTags[][] = {{-1,0,-3,0,0,0}, // a
             {-1,1,-3,0,0,0.01}, // b
             {-1,-1,-3.01,0.5,0,0}, // c
             {0,0,-3,0,0,0}, // d
             {-1.95,0,-3,0,0,0.18}}; // e
    
    public TagCorrespondance()
    {
        Config config;
        cameras = new ArrayList<Camera>();
        try
        {
            config = new ConfigFile(System.getenv("CONFIG")+"/camera.config");
            
            config = config.getChild("b09d01008b51b8");
            cameras.add(new Camera(config, "b09d01008b51b8"));
            config = config.getRoot().getChild("b09d01008b51ab");
            cameras.add(new Camera(config, "b09d01008b51ab"));
            config = config.getRoot();
            
            getAllCorrespondences();
            resolveExtrinsics();
            
            String output = "";
            Random rand = new Random();
            for(int y = 0; y < 2; y++)
            {
                Color color = new Color(rand.nextInt(256), 127, 127);
                double[] pos = cameras.get(y).getXyzrpy();
                
                output += "camera: " + cameras.get(y).getCameraId() + "\n";
                output += "(x,y,z): " + pos[0] + ", " + pos[1] + ", " + pos[2] + "\n";
                output += "(r,p,y): " + pos[3] + ", " + pos[4] + ", " + pos[5] + "\n\n";
                
                double[][] camM = cameras.get(y).getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(color, 0.08)));
                
                for(int x = 0; x < 5; x++)
                {
                    double M[][];
                    if(y == 0)  M = LinAlg.xyzrpyToMatrix(mainTags[x]);
                    else M = LinAlg.xyzrpyToMatrix(auxTags[x]);
                    vbTags.addBuffered(new VisChain(camM, M, new VisRectangle(0.15, 0.15, new VisDataLineStyle(color, 2))));
                }
            }

            // TODO write to config file
            System.out.println(output);
            showGui(output);
            
            
        } catch (Exception e)
        {
            e.printStackTrace();
        }
    }
    
    private void showGui(String output)
    {
        jf = new JFrame("Inter Camera Calibrater");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1000, 500);
        
        vbTags.switchBuffer();
        vbCameras.switchBuffer();
        jf.setVisible(true);
    }
    
    private void getAllCorrespondences() throws CameraException
    {
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            boolean found = false;

            for (int main = 0; main < cameras.size() && !found; main++)
            {
                if (main != cam)
                {
                    getCorrespondence(main, cameras.get(main), cameras.get(cam));
                    found = cameras.get(cam).isCertain();
                }
            }
            
            if (!found) throw new CameraException(CameraException.UNCERTAIN);
        }
    }
    
    private void getCorrespondence(int main, Camera mainCam, Camera auxCam)
    {
        auxCam.setMain(main);
        auxCam.clearPotentialPositions();

        for(int x = 0; x < 5; x++)
        {
            double mainM[][] = LinAlg.xyzrpyToMatrix(mainTags[x]);
            double auxM[][] = LinAlg.xyzrpyToMatrix(auxTags[x]);
            
            auxCam.addCorrespondence(LinAlg.matrixAB(mainM, LinAlg.inverse(auxM)));
        }
        
    }
    

    private void resolveExtrinsics() throws CameraException
    {
        cameras.get(0).setPosition(new double[] { 0, 0, 0, 0, 0, 0 }, 0);
        
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            cameras.get(cam).setPosition();
            while (cameras.get(cam).getMain() != 0)
            {
                if (cameras.get(cam).getMain() == cam) throw new CameraException(CameraException.CYCLE);

                double[][] pos = cameras.get(cam).getTransformationMatrix();
                double[][] posToOldMain = cameras.get(cameras.get(cam).getMain()).getTransformationMatrix();
                double[][] posToNewMain = LinAlg.matrixAB(pos, posToOldMain);

                cameras.get(cam).setPosition(posToNewMain, cameras.get(cameras.get(cam).getMain()).getMain());
            }
        }
    }
        
    /**
     * @param args
     */
    public static void main(String[] args)
    {
        new TagCorrespondance();
    }

}
