package mihai.camera;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JPanel;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import mihai.lcmtypes.image_path_t;
import mihai.util.CameraException;
import mihai.util.ConfigException;
import mihai.util.Util;
import april.config.Config;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jmat.LinAlg;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisWorld;

/**
 * Plays back the video from all cameras. Images are selected from the LCM message stream of image paths.
 *  The images themselves are saved onto the HDD at the location specified by the messages.
 * 
 * @author Mihai Bulic
 *
 */
public class CameraPlayer extends JPanel implements LCMSubscriber, ImageReader.Listener
{
    private static final long serialVersionUID = 1L;
    static LCM lcm = LCM.getSingleton();

    private int columns;
    private int maxWidth;
    private int maxHeight;
    private HashMap<Integer, Integer> widthHash = new HashMap<Integer, Integer>();
    private HashMap<Integer, Integer> heightHash = new HashMap<Integer, Integer>();
    private HashMap<Integer, String> formatHash = new HashMap<Integer, String>();
    
    private BufferedImage image;
    private VisWorld vw;
    private VisCanvas vc;
    private ArrayList<Integer> cameraPosition = new ArrayList<Integer>();
    
    public CameraPlayer(Config config, int columns) throws CameraException, IOException, ConfigException
    {
        super();
        setLayout(new BorderLayout());
        
        Util.verifyConfig(config);
    	if(ImageSource.getCameraURLs().size() == 0) throw new CameraException(CameraException.NO_CAMERA);
        ArrayList<ImageReader> irs = new ArrayList<ImageReader>();
        for (String url : ImageSource.getCameraURLs())
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
    	
        if(irs.size() > 0)
        {
            this.columns = columns;
            vw = new VisWorld();
            vc = new VisCanvas(vw);
            vc.setBackground(Color.BLACK);
            
            add(vc);
            vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { columns*maxWidth, maxHeight*Math.ceil((double)irs.size()/(columns*maxWidth))});
        }
        else
        {
            throw new CameraException(CameraException.NO_CAMERA);
        }
    }
    
    public CameraPlayer(int columns)
    {
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
	 
	    BufferedImage image = ImageConvert.convertToImage(formatHash.get(camera), widthHash.get(camera), heightHash.get(camera), imageBuffer);
	    VisWorld.Buffer vb = vw.getBuffer("cam" + camera);
        vb.addBuffered(new VisChain(LinAlg.translate(x, y, 0.0), 
                       new VisImage(image), 
                       new VisText(new double[]{x + widthHash.get(camera)/2, y + heightHash.get(camera),}, VisText.ANCHOR.TOP, "Camera "+camera)));
        vb.switchBuffer();
	}
}
