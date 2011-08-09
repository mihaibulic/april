package april.sandbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import april.camera.CameraDriver;
import april.camera.util.TagDetector2;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.util.PointUtil;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisImage;
import april.vis.VisRectangle;
import april.vis.VisWorld;

public class TagLocatorTest extends JFrame
{
    private static final long serialVersionUID = 1L;

    private JPanel j = new JPanel(new BorderLayout());
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    
    private VisWorld vw2 = new VisWorld();
    private VisCanvas vc2 = new VisCanvas(vw2);
    private VisWorld.Buffer vb2 = vw2.getBuffer("a2");
    
    private CameraDriver c;
    
    public TagLocatorTest(String url)
    {
        JSplitPane jsp = new JSplitPane(JSplitPane.VERTICAL_SPLIT, vc, vc2);
        jsp.setDividerLocation(0.5);
        jsp.setResizeWeight(0.5);
        
        vc2.getViewManager().viewGoal.fit2D(new double[]{0,0}, new double[]{752,480});
        
        j.add(jsp, BorderLayout.CENTER);
        add(j);
        setVisible(true);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
     
        c = new CameraDriver(url);
        
        run();
    }
    
    public void run()
    {
        double[] fc = {477,477};
        double[] cc = {376,240};
        double[] kc = {-0.25, 0.12, 0.0008, 0, 0};
        double alpha = 0;
        double tagSize = 0.02;
        
        HashMap<Integer, ArrayList<double[][]> > tags = new HashMap<Integer, ArrayList<double[][]> >();
        TagDetector2 td = new TagDetector2(new Tag36h11(), fc,cc,kc,alpha); 
        
        c.setFramerate(15);
        c.start();
        
        while(true)
        {
            BufferedImage im = c.getFrameImage();
            ArrayList<TagDetection> t = td.process(im, cc);
            
            System.out.println(t.size());
            vb2.addBuffered(new VisImage(im));
            
            for(TagDetection d : t)
            {
                if(!tags.containsKey(d.id)) 
                {
                    tags.put(d.id, new ArrayList<double[][]>());
                }
                
                double[][] M = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, d.homography);
                tags.get(d.id).add(M);
                
                VisWorld.Buffer vb = vw.getBuffer("blue" + d.id);
                for(double[][] loc : tags.get(d.id))
                {
                    vb.addBuffered(new VisChain(loc, new VisRectangle(tagSize/2,tagSize/2, 
                            new VisDataFillStyle(Color.blue))));
                }
                
                if(tags.get(d.id).size() > 100)
                {
                    double[] locI = PointUtil.getLocation(tags.get(d.id).toArray(new double[tags.get(d.id).size()][4][4]));
                    
                    VisWorld.Buffer vbred = vw.getBuffer("red" + d.id);
                    vbred.addBuffered(new VisChain(LinAlg.xyzrpyToMatrix(locI), new VisRectangle(tagSize,tagSize, 
                            new VisDataFillStyle(Color.red))));
                    
                    vbred.switchBuffer();
                    tags.get(d.id).clear();
                }
                
                vb.switchBuffer();
            }
            vb2.switchBuffer();
        }
    }
    
    public static void main(String[] args)
    {
        new TagLocatorTest(args[0]);
    }

}
