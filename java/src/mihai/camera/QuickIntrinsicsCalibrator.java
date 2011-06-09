package mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JFrame;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.util.GetOpt;
import april.util.ParameterGUI;
import april.vis.VisCanvas;
import april.vis.VisGrid;
import april.vis.VisImage;
import april.vis.VisWorld;

public class QuickIntrinsicsCalibrator extends JFrame implements ImageReader.Listener
{
    private static final long serialVersionUID = 1L;

    private boolean run = true;
    
    private ImageReader ir;
    private Object lock = new Object();
    private boolean imageReady = false;
    private byte[] imageBuffer;
    
    private VisWorld vw;
    private VisCanvas vc;
    private ParameterGUI pg;

    public QuickIntrinsicsCalibrator(Config config, String url) throws IOException, ConfigException, CameraException
    {
        super("RadialDistortionTest");
        setLayout(new BorderLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(Toolkit.getDefaultToolkit().getScreenSize());
        
        Util.verifyConfig(config);
        
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        add(vc, BorderLayout.CENTER);

        pg = new ParameterGUI();
        add(pg, BorderLayout.SOUTH);
        pg.addDoubleSlider("r", "r", 0.5, 2, 2.0);
        pg.addDoubleSlider("r2", "r2", -.005, .005, 0);

        ir = new ImageReader(config, url);
        ir.addListener(this);
        
        setVisible(true);
        new RunThread().start();
    }
    
    class RunThread extends Thread
    {
        public void run()
        {
            if(ir.isGood())
            {
                int width = ir.getWidth();
                int height = ir.getHeight();
                String format = ir.getFormat();
                VisWorld.Buffer vbImage = vw.getBuffer("image");
                VisWorld.Buffer vbGrid = vw.getBuffer("grid");
                vc.getViewManager().viewGoal.fit2D(new double[] { 250, 250 }, new double[] { width-250, height-250});
                
                vbImage.setDrawOrder(1);
                vbGrid.setDrawOrder(100);
                
                vc.setBackground(Color.BLACK);
                VisGrid vg = new VisGrid(10);
                vg.gridColor = Color.RED;
                vg.autoColor = false;
                vbGrid.addBuffered(vg);
                vbGrid.switchBuffer();
                
                ir.start();
                while (run)
                {
                    synchronized(lock)
                    {
                        while(!imageReady)
                        {
                            try
                            {
                                lock.wait();
                            } catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        imageReady = false;
                        BufferedImage originalImage = ImageConvert.convertToImage(format, width, height, imageBuffer);
                        BufferedImage rectifiedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

                        double cx = width / 2.0;
                        double cy = height / 2.0;
    
                        double A = pg.gd("r");
                        double B = pg.gd("r2");
    
                        for (int y = 0; y < height; y++)
                        {
                            for (int x = 0; x < width; x++)
                            {
                                double dy = y - cy;
                                double dx = x - cx;
    
                                double theta = Math.atan2(dy, dx);
                                double r = Math.sqrt(dy * dy + dx * dx);
    
                                double rp = A * r + B * r * r;
    
                                int nx = (int) Math.round(cx + rp * Math.cos(theta));
                                int ny = (int) Math.round(cy + rp * Math.sin(theta));
    
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height)
                                {
                                    rectifiedImage.setRGB(x, y, originalImage.getRGB((int) nx, (int) ny));
                                }
                            }
                        }
                        vbImage.addBuffered(new VisImage(rectifiedImage));
                        vbImage.switchBuffer();
//                        image.setImage(rectifiedImage);
                    }
                }
            }
            ir.kill();
        }
    }

    public static void main(String args[]) throws IOException, ConfigException, CameraException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file");
        opts.addString('u', "url", "dc1394://", "url of camera to use (only need to set if multiple dc1394 cameras are connected)");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate)");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: allows user to discover intrinsic parameters of camera experimentally.");  
            opts.doHelp();
            System.exit(1);
        }
        
        Config config = new ConfigFile(opts.getString("config"));
        if(config == null) throw new ConfigException(ConfigException.NULL_CONFIG);

        if(!opts.getString("resolution").isEmpty())
        {
            config.setBoolean("loRes", opts.getString("resolution").contains("lo"));
        }
        if(!opts.getString("colors").isEmpty())
        {
            config.setBoolean("color16", opts.getString("colors").contains("16"));
        }
        if(!opts.getString("fps").isEmpty())
        {
            config.setInt("fps", Integer.parseInt(opts.getString("fps")));
        }
        
        String url = opts.getString("url");
        for(String u : ImageSource.getCameraURLs())
        {
            if(u.contains(url))
            {
                url = u;
                break;
            }
        }
        
        new QuickIntrinsicsCalibrator(config, url);
    }

    public void kill()
    {
        run = false;
    }
    
    public void handleImage(byte[] image, long timeStamp, int camera)
    {
        synchronized(lock)
        {
            imageBuffer = image;
            imageReady = true;
            lock.notify();
        }
    }
}
