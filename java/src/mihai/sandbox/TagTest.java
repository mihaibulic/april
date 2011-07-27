package mihai.sandbox;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFrame;
import mihai.camera.CameraDriver;
import mihai.camera.Distortion;
import mihai.camera.ImageReader;
import mihai.camera.TagDetector2;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.vis.VisCamera;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.tag.CameraUtil;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.util.GetOpt;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataLineStyle;
import april.vis.VisRectangle;
import april.vis.VisWorld;

@SuppressWarnings("deprecation")
public class TagTest implements ImageReader.Listener, ParameterListener
{
    private TagDetector2 td;
    private TagDetector tdOld;
    double tagSize;
    double[] fc;
    double[] cc;
    double[] kc;
    double alpha;

    private ParameterGUI pg;
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    private VisWorld.Buffer vbTag = vw.getBuffer("tags");
    private VisWorld.Buffer vbTagOld = vw.getBuffer("tagsO");

    private Distortion df;
    
    private boolean ready = false;
    private Object lock = new Object();
    private byte[] buffer;
    private int width=752;
    private int height=480;
    private String format="GRAY8";
    
    public TagTest() throws IOException, CameraException, ConfigException
    {
        pg = new ParameterGUI();
        pg.addButtons("Reset", "toggle image source");
        pg.addListener(this);

        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(1000, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        String url = "dc1394://b09d01008b51b8";
        Config config = new ConfigFile(System.getenv("APRIL_CONFIG")
                + "/camera.config");
        tagSize = config.requireDouble("tagSize");
        ImageReader ir = new ImageReader(config, url);
        ir.addListener(this);

        config = config.getChild(CameraDriver.getSubUrl(config, url));
        fc = config.requireDoubles("fc");
        cc = config.requireDoubles("cc");
        kc = config.requireDoubles("kc");
        alpha = config.requireDouble("alpha");

        td = new TagDetector2(new Tag36h11(), fc, cc, kc, alpha);
        tdOld = new TagDetector(new Tag36h11());
        
        df = new Distortion(fc, cc, kc, alpha, 752, 480, 0.1);
        
        ir.start();
        jf.setVisible(true);

        run();
    }

    public static void main(String[] args) throws IOException, CameraException,
            ConfigException
    {
        GetOpt opts = new GetOpt();

        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('c', "colors", "gray8", "gray8 or gray16");
        opts.addInt('f', "fps", 15, "set the max fps to publish");
        opts.addString('r', "resolution", "hi", "lo=380x240, hi=760x480");
        opts.addString('u', "url", "dc1394", "camera url");

        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }

        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: record video from multiple cameras.");
            System.out.println("Cameras available:");

            ArrayList<String> cameras = ImageSource.getCameraURLs();

            for (String i : cameras)
            {
                System.out.println(i);
            }

            opts.doHelp();
            System.exit(1);
        }

        if (ImageSource.getCameraURLs().size() == 0)
        {
            System.out.println("No cameras found.  Are they plugged in?");
            System.exit(1);
        }

        new TagTest();
    }
    
    public void run()
    {
    	BufferedImage im;
    	
    	while(true)
    	{
	    	synchronized(lock)
	    	{
	    		while(!ready)
	    		{
	    			try 
	    			{
						lock.wait();
					} catch (InterruptedException e) 
					{
						e.printStackTrace();
					}
	    		}
	    		im = ImageConvert.convertToImage(format, width,height, buffer);
	    	}
	    	ready = false;
	    	
	    	if(time == 0)
	        {
	            time = System.currentTimeMillis();
	        }
	        if(System.currentTimeMillis() - time >= 1000)
	        {
	            System.out.println(1000*count/(System.currentTimeMillis() - time) + " fps");
	            time = System.currentTimeMillis();
	            count = 0;
	        }
	        count++;
	    	
	//      BufferedImage imNew = ImageConvert.convertToImage(ifmt.format, ifmt.width,ifmt.height, df.undistortBuffer(buffer));
	      ArrayList<TagDetection> tags = td.process(im, cc);
	      ArrayList<TagDetection> tagsOld = tdOld.process(im, new double[] {376,240});
	
	//      vbImage.addBuffered(new VisChain(LinAlg.translate(new double[] {0,-1*ifmt.height}), new VisImage(new VisTexture(imNew), new double[] { 0., 0, }, 
	//              new double[] {im.getWidth(), im.getHeight() }, true),LinAlg.translate(im.getWidth() / 2, 
	//                      im.getHeight() / 2), new VisRectangle(110, 110,new VisDataLineStyle(Color.RED, 2))));
	//
	//      vbImage.addBuffered(new VisChain(new VisImage(new VisTexture(im), new double[] { 0., 0, }, 
	//              new double[] {im.getWidth(), im.getHeight() }, true),LinAlg.translate(im.getWidth() / 2, 
	//                      im.getHeight() / 2), new VisRectangle(110, 110,new VisDataLineStyle(Color.RED, 2))));
	      
	      vbImage.addBuffered(new VisCamera(Color.BLACK, 0.08));
	      
	      for (int x = 0; x < tags.size(); x++)
	      {
//	          double[] d1 = fix(tags.get(x).interpolate(-1, -1));
//	          double[] d2 = fix(tags.get(x).interpolate(-1, 1));
//	          double[] d3 = fix(tags.get(x).interpolate(1, -1));
//	          double[] d4 = fix(tags.get(x).interpolate(1, 1));
	          
	          double[][] M = CameraUtil.homographyToPose(fc[0], fc[1], tagSize, tags.get(x).homography);
	          double[] xyz = LinAlg.matrixToXyzrpy(M);
	          
	          for(double q : xyz)
	              System.out.print(q + "\t");
	          for(double[] a : tags.get(x).p)
	          	System.out.print(a[0] + "\t" + a[1] + "\t");
	          System.out.println("new " + tags.get(x).id);
	          
//	          if(tagsOld.size() == tags.size())
//	          {
	              vbTag.addBuffered(new VisChain(M, new VisRectangle(tagSize, tagSize, 
	                      new VisDataLineStyle(Color.RED, 2))));
//	          }
	          
//	          vbImage.addBuffered(new VisChain(LinAlg.translate(new double[] {0,-1*ifmt.height}), LinAlg.translate(d1), new VisCircle(2, new VisDataFillStyle(Color.RED)), LinAlg.translate(inv(d1)),
//	                  LinAlg.translate(d2), new VisCircle(2, new VisDataFillStyle(Color.RED)), LinAlg.translate(inv(d2)),
//	                  LinAlg.translate(d3), new VisCircle(2, new VisDataFillStyle(Color.RED)), LinAlg.translate(inv(d3)),
//	                  LinAlg.translate(d4), new VisCircle(2, new VisDataFillStyle(Color.RED)), LinAlg.translate(inv(d4))));
	      }
	      
//	      for (int x = 0; x < tagsOld.size(); x++)
//	      {
//	//          double[] d1o = fix(tagsOld.get(x).interpolate(-1, -1));
//	//          double[] d2o = fix(tagsOld.get(x).interpolate(-1, 1));
//	//          double[] d3o = fix(tagsOld.get(x).interpolate(1, -1));
//	//          double[] d4o = fix(tagsOld.get(x).interpolate(1, 1));
//	
//	          double[][] MOld = CameraUtil.homographyToPose(fc[0], fc[1], tagSize,tagsOld.get(x).homography);
//	          double[] xyzOld = LinAlg.matrixToXyzrpy(MOld);
//	          
//	          {
//	              vbTagOld.addBuffered(new VisChain(MOld, new VisRectangle(tagSize, tagSize, 
//	                      new VisDataLineStyle(Color.BLUE, 2))));
//	          }
//	          
//	          for(double q : xyzOld)
//	              System.out.print(q + "\t");
//	          for(double[] a : tagsOld.get(x).p)
//	          	System.out.print(a[0] + "\t" + a[1] + "\t");
//	          System.out.println("old " + tagsOld.get(x).id);
//	          
//	//          vbImage.addBuffered(new VisChain(LinAlg.translate(d1o), new VisCircle(1, new VisDataFillStyle(Color.BLUE)), LinAlg.translate(inv(d1o)),
//	//                  LinAlg.translate(d2o), new VisCircle(1, new VisDataFillStyle(Color.BLUE)), LinAlg.translate(inv(d2o)),
//	//                  LinAlg.translate(d3o), new VisCircle(1, new VisDataFillStyle(Color.BLUE)), LinAlg.translate(inv(d3o)),
//	//                  LinAlg.translate(d4o), new VisCircle(1, new VisDataFillStyle(Color.BLUE)), LinAlg.translate(inv(d4o))));
//	      }
	      
	      vbTag.switchBuffer();
//	      vbTagOld.switchBuffer();
	      vbImage.switchBuffer();
	  }
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {}

    public double[] inv(double[] a)
    {
        for(int x = 0; x < a.length; x++)
            a[x] *= -1;
        
        return a;
    }
    
    public double[] fix(double[] a)
    {
        a[1] *= -1;
        a[1] += 480;
        
        return a;
    }
    
    private double count = 0;
    private long time = 0;
    
    public void handleImage(byte[] buffer, long timeStamp, int camera)
    {
        synchronized(lock)
        {
	        this.buffer = buffer;
	        ready = true;
	        lock.notify();
        }
    }
}

