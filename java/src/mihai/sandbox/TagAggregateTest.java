package mihai.sandbox;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFrame;

import mihai.camera.ImageReader;
import mihai.camera.TagDetector2;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import mihai.vis.VisCamera;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

public class TagAggregateTest extends JFrame implements ImageReader.Listener
{
    private static final long serialVersionUID = 1L;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vb = vw.getBuffer("buff");
    private VisWorld.Buffer vbC = vw.getBuffer("cam");
    
    private double tagSize;
    private double fc[];
    private double cc[];
    private double kc[];
    private double alpha;
    
    private TagDetector2 td;
    
    private Object lock = new Object();
    private boolean ready = false;
    
    private byte[] im;
    private int w=752;
    private int h=480;
    private String f="GRAY8";
    
    public TagAggregateTest() throws IOException, CameraException, ConfigException, InterruptedException
    {
        super("TA test");
        add(vc);
        
        String url = "dc1394://b09d01009a46a8";
        Config config = new ConfigFile(System.getenv("CONFIG") + "/camera.config");
        tagSize = config.requireDouble("tagSize");
        ImageReader ir = new ImageReader(config, url);
        ir.addListener(this);
        ir.start();

        config = config.getChild(Util.getSubUrl(config, url));
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");
        
        td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
        
        run();
    }
    
    public void run() throws InterruptedException
    {
        setSize(500,500);
        setVisible(true);
        BufferedImage image;
        vbC.addBuffered(new VisCamera(Color.BLACK, 0.08));
        vbC.switchBuffer();
        
        while(true)
        {
            synchronized(lock)
            {
                while(!ready)
                {
                    lock.wait();
                }
                image = ImageConvert.convertToImage(f, w, h, im);
            }
            ready = false;
            ArrayList<TagDetection> tags = td.process(image, cc);
            
            double[][] M;
            double[] loc;
            for(TagDetection tag : tags)
            {
                M = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, tag.homography);
                loc = LinAlg.matrixToXyzrpy(M);
                
                System.out.println("found tag " + tag.id + " at (" + loc[0] + ", " + loc[1] + ", " + loc[2] + ")\t" + "(" + loc[3] + ", " + loc[4] + ", " + loc[5] + ")");
                vb.addBuffered(new VisChain(M, new VisRectangle(tagSize, tagSize, new VisDataFillStyle(Color.red))));
            }
            vb.switchBuffer();
        }
        
    }
    
    
    public static void main(String[] args) throws IOException, CameraException, ConfigException, InterruptedException
    {
        new TagAggregateTest();
    }


    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        synchronized(lock)
        {
            im = image;
            ready = true;
            lock.notify();
        }
        
    }

}