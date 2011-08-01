package mihai.calibration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import mihai.camera.CameraDriver;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import mihai.vis.VisCamera;
import april.config.Config;
import april.config.ConfigFile;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisText;
import april.vis.VisWorld;

public class ExtrinsicsPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;
    
    private VisWorld vw;
    private VisCanvas vc;
    private VisWorld.Buffer vbDirections;
    private VisWorld.Buffer vbCameras;
    private VisWorld.Buffer vbTags;

    private Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.GRAY, Color.MAGENTA, Color.ORANGE, Color.PINK, Color.LIGHT_GRAY};
    private ArrayList <Camera> cameras;
    private double tagSize;
    private String configPath;
    private Config config;
    
    private Calibrate calibrate;
    private boolean run = true;
    
    public ExtrinsicsPanel(String id) throws ConfigException, CameraException, IOException, InterruptedException
    {
        super(id, new BorderLayout());
        
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.setBackground(Color.BLACK);
        vbTags = vw.getBuffer("tags");
        vbTags.setDrawOrder(1);
        vbCameras = vw.getBuffer("cameras");
        vbCameras.setDrawOrder(2);
        vbDirections = vw.getBuffer("directions");
        vbDirections.setDrawOrder(3);
        
        vc.getViewManager().viewGoal.fit2D(new double[] { -1, -1 }, new double[] { 1, 1});
        
        add(vc, BorderLayout.CENTER);
    }

    private void getAllCorrespondences() throws CameraException
    {
    	if(cameras.get(0).getTagCount() == 0)
        {
    	    throw new CameraException(CameraException.NO_TAGS);
	    }
    	
        for (int cam = 1; cam < cameras.size(); cam++)
        {
            boolean found = false;
            if (cameras.get(cam).getTagCount() == 0) 
            {
                throw new CameraException(CameraException.NO_TAGS);
            }

            for (int main = 0; main < cameras.size() && !found; main++)
            {
                if (main != cam)
                {
                    getCorrespondence(main, cameras.get(main), cameras.get(cam));
                    found = cameras.get(cam).isCertain();
                    
                    if(found) 
                    {
                        break;
                    }
                }
            }
            
            if (!found)
            {
                throw new CameraException(CameraException.UNCERTAIN);
            }
        }
    }

    private void getCorrespondence(int main, Camera mainCam, Camera auxCam)
    {
        int mainIndex = 0;
        int auxIndex = 0;
        double mainM[][];
        double auxM[][];
        ArrayList<Tag> mainTags = mainCam.getTagsList();
        ArrayList<Tag> auxTags = auxCam.getTagsList();
        
        auxCam.setMaster(main);
        auxCam.clearPotentialPositions();

        while (mainIndex < mainTags.size() && auxIndex < auxTags.size())
        {
            if (auxTags.get(auxIndex).id == mainTags.get(mainIndex).id)
            {
                mainM = mainTags.get(mainIndex).matrix;
                auxM = auxTags.get(auxIndex).matrix;
                
                auxCam.addCorrespondence(LinAlg.matrixAB(mainM, LinAlg.inverse(auxM)));
                mainIndex++;
                auxIndex++;
            }
            else if (auxTags.get(auxIndex).id > mainTags.get(mainIndex).id)
            {
                mainIndex++;
            }
            else
            {
                auxIndex++;
            }
        }
    }

    private void initializeExtrinsics() throws CameraException
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
    
    private void calculateItt()
    {
        double threshold = 0.00000001; // experimentally derived
        int ittLimit = 1000;           // experimentally derived
        int length = 6;                // xyzrpy
        int size = length * cameras.size();
        Distance distance = new Distance(cameras);

        double locations[] = new double[size];
        double eps[] = new double[size];
        for (int i = 0; i < cameras.size(); i++) 
        {
            double[] pos = cameras.get(i).getXyzrpy();
            for(int o = 0; o < 6; o++)
            {
                locations[i*length+o] = pos[o];
                eps[i*length+o] = 0.000001; // units: meters
            }
        }
        
        double[] r = LinAlg.scale(distance.evaluate(locations), -1);
        double[] oldR = null;
        
        int count = 0;
        while(!shouldStop(r, oldR, threshold) && ++count < ittLimit)
        {
            double[][] _J = NumericalJacobian.computeJacobian(distance, locations, eps);
            Matrix J = new Matrix(_J);
            Matrix JTtimesJplusI = (J.transpose().times(J)).plus(Matrix.identity(size, size));
            Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
            Matrix dx = JTtimesJplusI.solve(JTr);
            
            for (int i = 0; i < locations.length; i++)
            {
                locations[i] += 0.1*dx.get(i,0); // 0.1 helps stabilize results
            }
            
            oldR = r.clone();
            r = LinAlg.scale(distance.evaluate(locations), -1);
        }
        
        for(int x = 0; x < cameras.size(); x++)
        {
            double[] position = new double[length];
            for(int y = 0; y < length; y++)
            {
                position[y] = locations[x*length+y];
            }
            cameras.get(x).setPosition(position);
        }
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
        
        return stop || !run;
    }
    
    class Distance extends Function
    {
        ArrayList<Camera> cameras;;
        HashMap<Integer,double[]>[] tagsH;
        ArrayList<Tag>[] tagsL;
        
        @SuppressWarnings("unchecked") // can't infer generic when making arrays of HashMaps or ArrayLists
        public Distance(ArrayList<Camera> cameras)
        {
            this.cameras = cameras;
            tagsH = new HashMap[cameras.size()];
            tagsL = new ArrayList[cameras.size()];
            
            for(int c = 0; c < cameras.size(); c++)
            {
                tagsH[c] = cameras.get(c).getTagsHashMap();
                tagsL[c] = cameras.get(c).getTagsList();
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
            
            for(int c = 0; c < cameras.size(); c++)
            {
                int count = 0;
                double[] curCamera = new double[length];
                for(int x = 0; x < length; x++)
                {
                    curCamera[x] = location[c*length+x];
                }
                for(int t = 0; t < tagsL[c].size(); t++)
                {
                    double[] curTag = LinAlg.matrixToXyzrpy(LinAlg.matrixAB(LinAlg.xyzrpyToMatrix(curCamera), tagsL[c].get(t).matrix));
                    for(int o = 0; o < cameras.size(); o++)
                    {
                        if(o!=c)
                        {
                            double[] otherTag = tagsH[o].get(tagsL[c].get(t).id);
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
    
    /**
     * Once the constructor is run, call this method to retrieve an 
     * array of objects of type Camera
     * 
     * @return Camera[] - array of objects of type Camera 
     *      (contains useful information regarding each camera
     *      such as its relative position and url)
     */
    public ArrayList<Camera> getCameras()
    {
        return cameras;
    }

    class Calibrate extends Thread
    {
        public void run()
        {
            double text[] = {-2.875, 1.35}; // good text location found experimentally
            ArrayList<String> directions = new ArrayList<String>();
            directions.add("Please wait while calibrating:");
            directions.add("    Aggregating tags (~10 seconds/camera)...");
            
            for(int x = 0; x < directions.size(); x++)
            {
                vbDirections.addBuffered(new VisText(new double[]{text[0],text[1]-0.1*x}, VisText.ANCHOR.LEFT,directions.get(x)));
            }
            vbDirections.switchBuffer();
            
            for (Camera camera : cameras)
            {
                try
                {
                    camera.aggregateTags(15, tagSize);
                } catch (InterruptedException e)
                {
                    e.printStackTrace();
                    return;
                }
            }

            Collections.sort(cameras, new CameraComparator());
            
            try
            {
                directions.add("    Gathering tag correspondences...");
                for(int x = 0; x < directions.size(); x++)
                {
                    vbDirections.addBuffered(new VisText(new double[]{text[0],text[1]-0.1*x}, VisText.ANCHOR.LEFT,directions.get(x)));
                    
                }
                vbDirections.switchBuffer();
                getAllCorrespondences();
                
                directions.add("    Initializing extrinsics (coarse adjustments)...");
                for(int x = 0; x < directions.size(); x++)
                {
                    vbDirections.addBuffered(new VisText(new double[]{text[0],text[1]-0.1*x}, VisText.ANCHOR.LEFT,directions.get(x)));
                }
                vbDirections.switchBuffer();
                initializeExtrinsics();
            } catch (CameraException e)
            {
                e.printStackTrace();
                alertListener(false);
                return;
            }
            
            directions.add("    Itteratively resolving extrinsics (fine adjustments)...");
            for(int x = 0; x < directions.size(); x++)
            {
                vbDirections.addBuffered(new VisText(new double[]{text[0],text[1]-0.1*x}, VisText.ANCHOR.LEFT,directions.get(x)));
                
            }
            vbDirections.switchBuffer();
            calculateItt();

            String output = "";
            for(int x = 0; x < cameras.size(); x++)
            {
                Camera cam = cameras.get(x);
                
                double[] pos = cam.getXyzrpy();
                output += "camera: " + cam.getCameraId() + "\n"; 
                output += "    (x,y,z): " + ConfigUtil.round(pos[0],3) + ", " + ConfigUtil.round(pos[1],3) + ", " + ConfigUtil.round(pos[2],3) + "\n";
                output += "    (r,p,y): " + ConfigUtil.round(pos[3],3) + ", " + ConfigUtil.round(pos[4],3) + ", " + ConfigUtil.round(pos[5],3) + "\n\n";

                try
                {
                    ConfigUtil.setValues(configPath, new String[]{CameraDriver.getSubUrl(config, cam.getUrl())}, "xyzrpy", pos);
                } catch (ConfigException e)
                {
                    e.printStackTrace();
                } catch (IOException e)
                {
                    e.printStackTrace();
                }
                
                
                double[][] camM = cam.getTransformationMatrix();
                vbCameras.addBuffered(new VisChain(camM, new VisCamera(colors[x], 0.08)));
                
                for (Tag tag : cam.getTagsList())   
                {
                    double tagM[][] = tag.matrix;
                    vbTags.addBuffered(new VisChain(camM, tagM, new VisRectangle(tagSize, tagSize, 
                            new VisDataLineStyle(colors[x], 2))));
                }
            }
                
            vbDirections.addBuffered(new VisText(VisText.ANCHOR.TOP_LEFT,output));
            vbDirections.switchBuffer();
            vbTags.switchBuffer();
            vbCameras.switchBuffer();
            
            alertListener(true);
        }
    }
    
    @Override
    public void go(String configPath, String...urls)
    {
        try
        {
            this.configPath = configPath;
            config = new ConfigFile(configPath).getChild("extrinsics");
            ConfigUtil.verifyConfig(config);
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        }
        
        tagSize = config.requireDouble("tag_size");

        cameras = new ArrayList<Camera>();
        for(String url : urls)
        {
            try
            {
                Camera test = new Camera(config, url);
                if(test.isGood())
                {
                    cameras.add(test);
                }
            } catch (CameraException e)
            {
                e.printStackTrace();
            } catch (IOException e)
            {
                e.printStackTrace();
            } catch (ConfigException e)
            {
                e.printStackTrace();
            }
        }
    
        calibrate = new Calibrate();
        calibrate.start();
    }
    
    @Override
    public void stop()
    {
        run = false;
        for(Camera cam : cameras)
        {
            try
            {
                cam.kill();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void showDisplay(boolean show)
    {
        // no directions to show
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {}
}
