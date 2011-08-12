package april.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.calibration.Broadcaster;
import april.camera.util.CameraException;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.lcmtypes.image_path_t;
import april.util.ConfigException;
import april.util.ConfigUtil2;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisDataFillStyle;
import april.vis.VisImage;
import april.vis.VisRectangle;
import april.vis.VisText;
import april.vis.VisWorld;

public class CameraPlayerPanel extends Broadcaster implements LCMSubscriber
{
    private static final long serialVersionUID = 1L;

    static LCM lcm = LCM.getSingleton();
    private String dir;

    private boolean show;
    private int columns;
    private int maxWidth, maxHeight;

    private Discover discoveryThread;
    private ArrayList<Camera> cameras = new ArrayList<Camera>();
    
    private VisWorld vw;
    private VisCanvas vc;
    private boolean change = true;
    
    public CameraPlayerPanel(int columns, boolean wizard) throws CameraException, IOException, ConfigException
    {
        this(columns, wizard, null);
    }

    public CameraPlayerPanel(int columns, boolean wizard, String dir) throws CameraException, IOException, ConfigException
    {
        super(new BorderLayout());

        this.columns = columns;
        this.dir = dir;
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.getViewManager().interfaceMode = 1.0;
        vc.setBackground(Color.BLACK);

        if (!wizard)
        {
            lcm.subscribeAll(this);
        }

        add(vc);
    }
    
    class Discover extends Thread
    {
        private Config config;
        private boolean run = true;
        
        public Discover(Config config)
        {
            this.config = config;
        }
        
        public void run()
        {
            try
            {
                while(run)
                {
                    for (String url : ImageSource.getCameraURLs())
                    {
                        if (CameraDriver.isValidUrl(config, url))
                        {
                            change = true;

                            Camera newCamera = new Camera(url, config);
                            newCamera.start();
                            cameras.add(newCamera);
                        }
                    }
                    
                    if(change)
                    {
                        showDisplay(show);
                        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, getDimension());
                        change = false;
                    }
                    Thread.sleep(1000);
                }
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
        
        public void kill()
        {
            run = false;

            try
            {
                this.join();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }

    class Camera extends Thread
    {
        private CameraDriver driver;
        private VisWorld.Buffer vb;
        private boolean run = true;

        String id;
        double x, y;
        String format;
        int width, height;

        public Camera(String id)
        {
            this.id = id;
        }
        
        public Camera(String id, String format, int width, int height, double x, double y)
        {
            this.id = id;
            this.format = format;
            this.width = width;
            this.height = height;
            this.x = x;
            this.y = y;
        }
        
        public Camera(String url, Config config)
        {
            try
            {
                driver = new CameraDriver(url, config);
            } catch (ConfigException e)
            {
                e.printStackTrace();
            }

            id = driver.getCameraId();
            format = driver.getFormat();
            width = driver.getWidth();
            height = driver.getHeight();
            
            if (width > maxWidth)
            {
                maxWidth = width;
            }
            if (height > maxHeight)
            {
                maxHeight = height;
            }

            vb = vw.getBuffer(id);
            int slot = cameras.size();
            x = maxWidth * (slot % columns);
            y = -maxHeight * (slot / columns);
        }

        public void run()
        {
            driver.start();
            
            while (run)
            {
                vb.addBuffered(new VisChain(LinAlg.translate(x, y, 0.0), new VisImage(driver.getFrameImage())));
                vb.addBuffered(new VisText(new double[] { x+width/2.0, y+height, }, VisText.ANCHOR.TOP, id));
                vb.switchBuffer();
            }
            driver.kill();
        }

        public void kill()
        {
            run = false;
            try
            {
                this.join();
            } catch (InterruptedException e1)
            {
                e1.printStackTrace();
            }
        }
        
        @Override
        public boolean equals(Object a)
        {
            return id.equals(((Camera)a).id);
        }
    }

    private double[] getDimension()
    {
        double x = (cameras.size() >= columns ? columns : cameras.size()) * maxWidth;
        double y = maxHeight * Math.ceil((double) cameras.size() / columns);
        
        return new double[]{x,y};
    }
    
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        if (channel.startsWith("rec "))
        {
            try
            {
                image_path_t imagePath = new image_path_t(ins);
                String id = imagePath.id;
    
                String finalDir = (dir != null ? dir : imagePath.dir);
                finalDir += !finalDir.endsWith(File.separator) ? File.separator : "";
                File f = new File(finalDir + imagePath.img_path);
                long length = f.length();
                if (length > Integer.MAX_VALUE)
                {
                    throw new CameraException(CameraException.FILE_TOO_LARGE);
                }
    
                byte[] buffer = new byte[(int) length];
                new FileInputStream(f).read(buffer);
    
                BufferedImage image = ImageConvert.convertToImage(imagePath.format, imagePath.width, imagePath.height, buffer);
    
                double x, y; 
                if(cameras.contains(new Camera(id)))
                {
                    x = cameras.get(cameras.indexOf(new Camera(id))).x;
                    y = cameras.get(cameras.indexOf(new Camera(id))).y;
                }
                else
                {
                    int slot = cameras.size();
                    x = maxWidth * (slot % columns);
                    y = -maxHeight * (slot / columns);
                    cameras.add(new Camera(id, imagePath.format, imagePath.width, imagePath.height,x,y));
                    change = true;
                }
    
                VisWorld.Buffer vb = vw.getBuffer(id);
                Camera c = cameras.get(cameras.indexOf(new Camera(id)));
                vb.addBuffered(new VisChain(LinAlg.translate(new double[] { maxWidth * x, -maxHeight * y, 0 }), new VisImage(image)));
                vb.addBuffered(new VisText(new double[] { x + c.width / 2, y + c.height, }, VisText.ANCHOR.TOP, id));
                vb.switchBuffer();
    
                if (imagePath.width > maxWidth)
                {
                    maxWidth = imagePath.width;
                }
                if (imagePath.height > maxHeight)
                {
                    maxHeight = imagePath.height;
                }
                
                if(change)
                {
                    vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, getDimension());
                    change = false;
                }
            } catch (IOException e)
            {
                e.printStackTrace();
            } catch (CameraException e)
            {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void go(String configPath)
    {
        try
        {
            Config config = new ConfigFile(configPath);
            ConfigUtil2.verifyConfig(config);
            
            discoveryThread = new Discover(config);
            discoveryThread.start();
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void kill()
    {
        discoveryThread.kill();
        
        for (Camera c : cameras)
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
    public void showDisplay(boolean show)
    {
        VisWorld.Buffer vbDisplay = vw.getBuffer("display");
        vbDisplay.setDrawOrder(10);

        this.show = show;
        if (show)
        {
            String directions = "<<left>><<mono-small>> \n \n \n " + 
            "DIRECTIONS: \n \n " + 
            "<<left>> Place tags in the view of the cameras, follow these guidelines, and hit\n" + "" + 
            "<<left>>               next to begin extrinsic calibration: \n \n" + 
            "<<left>>   Layman's guidelines:\n" + 
            "<<left>>       1. Each tag MUST be viewable by at least two cameras \n" + 
            "<<left>>               (the more the better).\n \n" + 
            "<<left>>       2. Each camera MUST see at least one tag (the more the better).\n \n" + 
            "<<left>>       3. One must be able to 'connect' all the cameras together by seeing\n" + 
            "<<left>>               common tags inbetween them.\n \n" + 
            "<<left>>       4. The tags should be placed as far apart from one another as possible.\n \n" + 
            "<<left>>       5. The more tags that are used the better.\n \n \n" + 
            "<<left>>   Computer scienctist guideline:\n" + 
            "<<left>>       A connected graph must be formable using cameras as nodes and \n" + 
            "<<left>>               common tag detections as edges.\n \n" + 
            "<<left>>   HINT: the more connections the better, with a complete graph being ideal";

            VisText v = new VisText(VisText.ANCHOR.CENTER, directions);
            v.dropShadowColor = new Color(0,0,0,0);
            
            for(Camera c : cameras)
            {
                vbDisplay.addBuffered(new VisRectangle(new double[]{c.x,c.y}, 
                        new double[]{c.x+c.width,c.y+c.height}, 
                        new VisDataFillStyle(new Color(0,0,0,200))));
            }
            vbDisplay.addBuffered(v);
        }

        vbDisplay.switchBuffer();
    }
}
