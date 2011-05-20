package mihai.camera;

import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.swing.JFrame;

import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import mihai.lcmtypes.image_path_t;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.config.ConfigFile;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.jmat.LinAlg;
import april.util.GetOpt;
import april.util.TimeUtil;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisImage;
import april.vis.VisWorld;
import april.vis.VisWorld.Buffer;

/**
 * Plays back the video from all cameras. Images are selected from the LCM message stream of image paths.
 *  The images themselves are saved onto the HDD at the location specified by the messages.
 * 
 * @author Mihai Bulic
 *
 */
public class CameraPlayer extends JFrame implements LCMSubscriber, ImageReader.Listener
{
    private static final long serialVersionUID = 1L;
    static LCM lcm = LCM.getSingleton();
    private int width = 0;
    private int height = 0;
    private int columns;
    
    private BufferedImage image;
    
    private VisWorld vw;
    private VisCanvas vc;
    private HashMap<Integer, VisWorld.Buffer> buffers = new HashMap<Integer, VisWorld.Buffer>();

    public CameraPlayer(Config config, int columns) throws CameraException, IOException, ConfigException
    {
        super("Camera Player");
        
        Util.verifyConfig(config);
        
    	if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);
    	
        this.columns = columns;
        ArrayList<String> urls = ImageSource.getCameraURLs();
        ImageReader irs[] = new ImageReader[urls.size()];

        setGUI();
        
        for (int x = 0; x < irs.length; x++)
        {
        	irs[x] = new ImageReader(config, urls.get(x));
        	irs[x].addListener(this);
        	irs[x].start();
        }
    	
    	while(true)
        {
            TimeUtil.sleep(100);
        }
    }
    
    public CameraPlayer(int columns)
    {
        this.columns = columns;
        
        setGUI();
        lcm.subscribeAll(this);
        
        while(true)
        {
            TimeUtil.sleep(100);
        }
    }

    private void setGUI() 
    {
    	vw = new VisWorld();
    	vc = new VisCanvas(vw);
    	setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    	setSize(Toolkit.getDefaultToolkit().getScreenSize());
    	
    	add(vc);
    	setVisible(true);
    }
    

    public static void main(String[] args) throws CameraException, IOException, ConfigException
    {
        GetOpt opts = new GetOpt();
        
        opts.addBoolean('h', "help", false, "See this help screen");
        opts.addBoolean('a', "all", true, "LCM mode: display images from all cameras published via lcm");
        opts.addBoolean('s', "standAlone", false, "Standalone mode: will capture images from all cameras");
        opts.addString('n', "config", System.getenv("CONFIG")+"/camera.config", "location of config file (standalone mode only)");
        opts.addString('r', "resolution", "", "lo=380x240, hi=760x480 (overrides config resolution) (standalone mode only)");
        opts.addString('c', "colors", "", "gray8 or gray16 (overrides config color setting) (standalone mode only)");
        opts.addString('f', "fps", "", "framerate to use if player (overrides config framerate) (standalone mode only)");
        opts.addInt('x', "columns", 2, "number of columns in which to display camera images (standalone and lcm mode)");
        
        if (!opts.parse(args))
        {
            System.out.println("option error: " + opts.getReason());
        }
        
        if (opts.getBoolean("help"))
        {
            System.out.println("Usage: displays images from camera specified");  
            opts.doHelp();
            System.exit(1);
        }
        
        if(opts.getBoolean("standAlone"))
        {
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
        	
        	new CameraPlayer(config, opts.getInt("columns"));
        }
        
        if(opts.getBoolean("all"))
        {
            new CameraPlayer(opts.getInt("columns"));
        }
    }
    
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            if (channel.contains("cam"))
            {
                image_path_t imagePath = new image_path_t(ins);
                int camera = imagePath.id;
                byte[] buffer = new byte[imagePath.width * imagePath.height];
                new FileInputStream(new File(imagePath.img_path)).read(buffer);
                image = ImageConvert.convertToImage(imagePath.format,imagePath.width, imagePath.height, buffer);
                
                VisWorld.Buffer vb = (Buffer) (buffers.containsKey(camera) ? buffers.get(camera) : vw.getBuffer("cam"+camera));
                vb.addBuffered(new VisChain(LinAlg.translate(new double[] {width*(camera%columns),-height*(camera/columns),0}), new VisImage(image)));
                if(image.getWidth() != width || image.getHeight() != height)
                {
                    width = image.getWidth();
                    height = image.getHeight();
                    vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { width, height });
                }
                vb.switchBuffer();
                
                if(!buffers.containsKey(camera))
                {
                    buffers.put(camera, vb);
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }

	public void handleImage(byte[] imageBuffer, ImageSourceFormat ifmt, long timeStamp, int camera) 
	{
        BufferedImage image = ImageConvert.convertToImage(ifmt.format,ifmt.width, ifmt.height, imageBuffer);

        VisWorld.Buffer vb = (Buffer) (buffers.containsKey(camera) ? buffers.get(camera) : vw.getBuffer("cam"+camera));
        vb.addBuffered(new VisChain(LinAlg.translate(new double[] {width*(camera%columns),-height*(camera/columns),0}), new VisImage(image)));

        if(image.getWidth() != width || image.getHeight() != height)
        {
            width = image.getWidth();
            height = image.getHeight();
            vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { width, height });
        }
        vb.switchBuffer();
        
        if(!buffers.containsKey(camera))
        {
            buffers.put(camera, vb);
        }
	}
}
