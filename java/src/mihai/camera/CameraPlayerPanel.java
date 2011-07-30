package mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import mihai.calibration.Broadcaster;
import mihai.lcmtypes.image_path_t;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.ConfigUtil;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisWorld;

public class CameraPlayerPanel extends Broadcaster implements LCMSubscriber
{
    private static final long serialVersionUID = 1L;
    static LCM lcm = LCM.getSingleton();

    private int columns;
    private int maxWidth, maxHeight;
    private HashMap<String, Integer> widthHash = new HashMap<String, Integer>();
    private HashMap<String, Integer> heightHash = new HashMap<String, Integer>();
    private HashMap<String, String> formatHash = new HashMap<String, String>();
    
    private ArrayList<Capture> captures;
    
    private VisWorld vw;
    private VisCanvas vc;
    private ArrayList<String> cameraPosition = new ArrayList<String>();
    
    public CameraPlayerPanel(int columns, boolean wizard) throws CameraException, IOException, ConfigException
    {
        super(new BorderLayout());
        
        this.columns = columns;
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.getViewManager().interfaceMode = 1.0;
        vc.setBackground(Color.BLACK);
        
        lcm.subscribeAll(this);
        add(vc);
    }
    
    class Capture extends Thread
    {
        private String id;
        private CameraDriver driver;
        private double x, y;
        private VisWorld.Buffer vb;

        private boolean run = true, done = false;
        private Object lock = new Object();
        
        String format;
        int width, height;
        public Capture(CameraDriver driver)
        {
            this.driver = driver;
            this.id = driver.getCameraId();
            format=driver.getFormat();
            width=driver.getWidth();
            height=driver.getHeight();

            vb = vw.getBuffer(id);
            int slot = cameraPosition.indexOf(id);
            x = maxWidth*(slot%columns);
            y =-maxHeight*(slot/columns);
        }
        
        public void run()
        {
            driver.start();
            while(run)
            {
                vb.addBuffered(new VisChain(LinAlg.translate(x, y, 0.0), new VisImage(driver.getFrameImage())));
                vb.addBuffered(new VisText(new double[]{x + widthHash.get(id)/2, y + heightHash.get(id),}, 
                        VisText.ANCHOR.TOP, id));
                vb.switchBuffer();
            }
            
            synchronized(lock)
            {
                driver.kill();
                done = true;
                lock.notify();
            }
        }
        
        public void kill()
        {
            run = false;
            
            synchronized(lock)
            {
                while(!done)
                {
                    try
                    {
                        lock.wait();
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            if (channel.contains("camera "))
            {
                image_path_t imagePath = new image_path_t(ins);
                String camera = imagePath.id;
                byte[] buffer = new byte[imagePath.width * imagePath.height];
                new FileInputStream(new File(imagePath.img_path)).read(buffer);
                BufferedImage image = ImageConvert.convertToImage(imagePath.format,imagePath.width, imagePath.height, buffer);
                
                int position = cameraPosition.indexOf(camera);
                if(position == -1)
                {
                    position = cameraPosition.size();
                    cameraPosition.add(camera);
                }
                
                VisWorld.Buffer vb = vw.getBuffer(camera);
                vb.addBuffered(new VisChain(LinAlg.translate(new double[] {maxWidth*(position%columns),-maxHeight*(position/columns),0}), new VisImage(image)));
                
                if(image.getWidth() > maxWidth) maxWidth = image.getWidth();
                if(image.getHeight() > maxHeight) maxHeight = image.getHeight();
                vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { columns*maxWidth, maxHeight*Math.ceil((double)cameraPosition.size()/(columns*maxWidth))});
                vb.switchBuffer();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void go(String configPath, String... urls)
    {
        Config config = null;
        try
        {
            config = new ConfigFile(configPath);
            ConfigUtil.verifyConfig(config);
            if(ImageSource.getCameraURLs().size() == 0) new CameraException(CameraException.NO_CAMERA).printStackTrace();
            captures = new ArrayList<Capture>();
            if(urls.length == 0)
            {
                ArrayList<String> u = ImageSource.getCameraURLs();
                urls = u.toArray(new String[u.size()]);
            }
            for (String url : urls)
            {
                CameraDriver test = new CameraDriver(url, config);
                if(test.isGood())
                {
                    if(test.getWidth() > maxWidth) maxWidth = test.getWidth();
                    if(test.getHeight() > maxHeight) maxHeight = test.getHeight();

                    cameraPosition.add(test.getCameraId());
                    widthHash.put(test.getCameraId(), test.getWidth());
                    heightHash.put(test.getCameraId(), test.getHeight());
                    formatHash.put(test.getCameraId(), test.getFormat());
                    captures.add(new Capture(test));
                }
            }
            for(Capture c : captures)
            {
                c.start();
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        }
        
        if(captures.size() == 0) new CameraException(CameraException.NO_CAMERA).printStackTrace();
        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { columns*maxWidth, maxHeight*Math.ceil((double)captures.size()/(columns*maxWidth))});
    }

    @Override
    public void stop()
    {      
        for(Capture c : captures)
        {
            c.kill();
        }
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {
        VisWorld.Buffer vbError = vw.getBuffer("error");
        vbError.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, (error ? "<<red, big>>" : "") + msg));
        vbError.switchBuffer();
    }
    
    @Override
    public void showDirections(boolean show)
    {
        VisWorld.Buffer vbDirections = vw.getBuffer("directions");
        vbDirections.setDrawOrder(10);
        
        if(show)
        {
            String directions = "<<left>><<mono-small>> \n \n \n " +
                                "DIRECTIONS: \n \n "+ 
                                "<<left>> Place tags in the view of the cameras, follow these guidelines, and hit\n"+"" +
                                "<<left>>               next to begin extrinsic calibration: \n \n" +
                                "<<left>>   Layman's guidelines:\n" +
                                "<<left>>       1. Each tag MUST be viewable by at least two cameras \n"+
                                "<<left>>               (the more the better).\n \n" +
                                "<<left>>       2. Each camera MUST see at least one tag (the more the better).\n \n" +
                                "<<left>>       3. One must be able to 'connect' all the cameras together by seeing\n"+
                                "<<left>>               common tags inbetween them.\n \n" +
                                "<<left>>       4. The tags should be placed as far apart from one another as possible.\n \n" +
                                "<<left>>       5. The more tags that are used the better.\n \n \n" +
                                "<<left>>   Computer scienctist guideline:\n" +
                                "<<left>>       A connected graph must be formable using cameras as nodes and \n"+
                                "<<left>>               common tag detections as edges.\n \n" +
                                "<<left>>   HINT: the more connections the better, with a complete graph being ideal";
            
            vbDirections.addBuffered(new VisChain(new VisText(VisText.ANCHOR.CENTER, directions)));
        }
        
        vbDirections.switchBuffer();
    }
}
