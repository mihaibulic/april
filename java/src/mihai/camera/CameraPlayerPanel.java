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
import mihai.calibration.gui.Broadcaster;
import mihai.lcmtypes.image_path_t;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
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

public class CameraPlayerPanel extends Broadcaster implements LCMSubscriber, ImageReader.Listener
{
    private static final long serialVersionUID = 1L;
    static LCM lcm = LCM.getSingleton();

    private int columns;
    private int maxWidth;
    private int maxHeight;
    private HashMap<Integer, Integer> widthHash = new HashMap<Integer, Integer>();
    private HashMap<Integer, Integer> heightHash = new HashMap<Integer, Integer>();
    private HashMap<Integer, String> formatHash = new HashMap<Integer, String>();
    
    private ArrayList<ImageReader> irs;
    private BufferedImage image;
    
    private VisWorld vw;
    private VisCanvas vc;
    private VisWorld.Buffer vbError;
    private ArrayList<Integer> cameraPosition = new ArrayList<Integer>();
    
    public CameraPlayerPanel(int id, int columns) throws CameraException, IOException, ConfigException
    {
        super(id, new BorderLayout());
        
        this.columns = columns;
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        vc.setBackground(Color.BLACK);
        vbError = vw.getBuffer("error");
        
        add(vc);
    }
    
    public CameraPlayerPanel(int columns)
    {
        super(0, new BorderLayout());
        
        this.columns = columns;
        vw = new VisWorld();
        vc = new VisCanvas(vw);
        add(vc);
        lcm.subscribeAll(this);
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
                
                int position = cameraPosition.indexOf(camera);
                if(position == -1)
                {
                    position = cameraPosition.size();
                    cameraPosition.add(camera);
                }
                
                VisWorld.Buffer vb = vw.getBuffer("cam" + camera);
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

	public void handleImage(byte[] imageBuffer, long timeStamp, int camera)
	{
	    int slot = cameraPosition.indexOf(camera);
	    double x = maxWidth*(slot%columns);
	    double y =-maxHeight*(slot/columns);
	 
	    BufferedImage image = ImageConvert.convertToImage(
	            formatHash.get(camera), widthHash.get(camera), heightHash.get(camera), imageBuffer);
	    VisWorld.Buffer vb = vw.getBuffer("cam" + camera);
        vb.addBuffered(new VisChain(LinAlg.translate(x, y, 0.0), new VisImage(image)));
        vb.addBuffered(new VisText(new double[]{x + widthHash.get(camera)/2, y + heightHash.get(camera),}, 
                VisText.ANCHOR.TOP, "Camera "+camera));
        vb.switchBuffer();
	}

    @Override
    public void go(String configPath, String... urls)
    {
        Config config = null;
        try
        {
            config = new ConfigFile(configPath);
            Util.verifyConfig(config);
            if(ImageSource.getCameraURLs().size() == 0) new CameraException(CameraException.NO_CAMERA).printStackTrace();
            irs = new ArrayList<ImageReader>();
            if(urls.length == 0)
            {
                ArrayList<String> u = ImageSource.getCameraURLs();
                urls = u.toArray(new String[u.size()]);
            }
            for (String url : urls)
            {
                ImageReader test = new ImageReader(config, url);
                if(test.isGood())
                {
                    test.addListener(this);
                    
                    if(test.getWidth() > maxWidth) maxWidth = test.getWidth();
                    if(test.getHeight() > maxHeight) maxHeight = test.getHeight();

                    cameraPosition.add(test.getCameraId());
                    widthHash.put(test.getCameraId(), test.getWidth());
                    heightHash.put(test.getCameraId(), test.getHeight());
                    formatHash.put(test.getCameraId(), test.getFormat());
                    test.start();
                    irs.add(test);
                }
            }
        }catch (CameraException e)
        {
            e.printStackTrace();
        } catch (IOException e)
        {
            e.printStackTrace();
        } catch (ConfigException e)
        {
            e.printStackTrace();
        }
        
        if(irs.size() == 0) new CameraException(CameraException.NO_CAMERA).printStackTrace();
        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { columns*maxWidth, maxHeight*Math.ceil((double)irs.size()/(columns*maxWidth))});
        
        VisWorld.Buffer vbDirections = vw.getBuffer("directions");
        double text = maxHeight+500;
        String directions[] = {"DIRECTIONS: place tags in the camera views following these guidelines, then hit next to being extrinsic calibration:",
                               "   Layman's guidelines:",
                               "            1. Each tag MUST be viewable by at least two cameras (the more the better).",
                               "            2. Each camera MUST see at least one tag (the more the better).",
                               "            3. One must be able to 'connect' all the cameras together by seeing common tags inbetween them.",
                               "            4. The tags should be placed as far apart from one another as possible.",
                               "            5. The more tags that are used the better.",
                               "   Computer scienctist guideline:",
                               "            A connected graph must be formable using cameras as nodes and common tag detections as edges.",
                               "            (the more connected the better, with a complete graph being ideal)"};
        for(int x = 0; x < directions.length; x++)
        {
            vbDirections.addBuffered(new VisText(new double[]{0,text-40*x}, VisText.ANCHOR.LEFT,directions[x]));
            
        }
        vbDirections.switchBuffer();
    }

    @Override
    public void stop()
    {      
        for(ImageReader ir : irs)
        {
            try
            {
                ir.kill();
            } catch (InterruptedException e)
            {
                e.printStackTrace();
            }
        }
    }
    
    @Override
    public void displayMsg(String msg, boolean error)
    {
        vbError.addBuffered(new VisText(VisText.ANCHOR.BOTTOM_LEFT, (error ? "<<red, big>>" : "") + msg));
        vbError.switchBuffer();
    }
}
