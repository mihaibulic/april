package mihai.calibration;

import java.awt.BorderLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import mihai.camera.CameraDriver;
import mihai.camera.TagDetector2;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import april.graph.Graph;
import april.graph.GraphSolver;
import april.tag.Tag36h11;
import april.tag.TagDetection;

/*
 * finish capture 
 *  -take urls, get config info, and pass info to capture thread
 * 
 * make eval method for newton's method solver
 *  -instantiate w/ all tag detections
 *  -eval to find distance of proposed tags corners from rays, and distance btwn proposed tag corners
 *  -desired return value of eval are 0s for distance from rays, and the length of the tag for distance btwn tag corners
 *  -potentially add a measurement for planar-ness of a tag?
 *  
 * make newtons method step
 *  -mostly cpy/paste
 *  
 * get init solution
 *  -could i just start it all at 0,0,0,0,0,0?
 * 
 * cpy/paste gui code from old extrin
 * 
 */


public class NewEPanel extends Broadcaster
{
    private static final long serialVersionUID = 1L;
    
    Graph g;
    GraphSolver gs;
    
    public NewEPanel(String id, String...urls)
    {
        super(id, new BorderLayout());
        
        
    }
    
    class Tag
    {
        int id;
        int spotter; //id of camera that saw this tag
        double[][] uv = new double[4][2];
    }
    
    class Capture extends Thread
    {
        private CameraDriver driver;
        private TagDetector2 td;
        
        private double[] cc;
        
        private ArrayList<TagDetection> detections = new ArrayList<TagDetection>();
        HashMap<Integer, Tag> tagsH = new HashMap<Integer, Tag>(); 
        ArrayList<Tag> tagsL = new ArrayList< Tag >();
        
        public Capture(String url, double[] fc, double[] cc, double[] kc, double alpha)
        {
            this.cc = cc;
            td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
            
            try
            {
                driver = new CameraDriver(url);
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
        
        public void run()
        {
            driver.start();
            
            for(int x = 0; x < 15; x++)
            {
                detections.addAll(td.process(driver.getFrameImage(),cc));
            }

            Collections.sort(detections, new TagDetectionComparator());
            
            int end = 0;
            for(int start = 0; start < detections.size(); start=end)
            {
                int last_id = detections.get(start).id;
                int id = last_id;
                
                while( last_id==id && ++end < detections.size())
                {
                    id = detections.get(end).id;
                }
                
                double[][] points = new double[end-start][6];
                for(int b = 0; b < points.length; b++)
                {
                    TagDetection t = detections.get(start+b);
                    Tag tag = new Tag();
                    
                    tag.id = last_id;
                    tag.spotter = driver.getCameraId();
                    tag.uv = t.p;

                    tagsL.add(tag);
                    tagsH.put(last_id, tag);
                }
            }
        }
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {}

    @Override
    public void go(String configPath, String...urls)
    {
    }

    @Override
    public void showDirections(boolean show)
    {}

    @Override
    public void stop()
    {
    }
}