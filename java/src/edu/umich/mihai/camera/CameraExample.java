package edu.umich.mihai.camera;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import javax.swing.JFrame;
import lcm.lcm.LCM;
import lcm.lcm.LCMDataInputStream;
import lcm.lcm.LCMSubscriber;
import april.jcam.ImageConvert;
import april.jcam.ImageSourceFormat;
import april.vis.VisCanvas;
import april.vis.VisImage;
import april.vis.VisTexture;
import april.vis.VisWorld;
import edu.umich.mihai.lcmtypes.image_path_t;

public class CameraExample implements LCMSubscriber, ImageReader.Listener
{
    static LCM lcm = LCM.getSingleton();

    private Object lock = new Object();
    private boolean bufferReady = false;
    private byte[] imageBuffer;
    private int width;
    private int height;
    private String format;
    private double timeStamp;
    
    private boolean run = true;
    
    private JFrame jf;
    private VisWorld vw = new VisWorld();
    private VisCanvas vc = new VisCanvas(vw);
    private VisWorld.Buffer vbImage = vw.getBuffer("images");
    
    public CameraExample()
    {
        jf = new JFrame("Basic Image GUI");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.setSize(1000, 500);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setVisible(true);
        
//        lcm.subscribeAll(this);
        lcm.subscribe("cam1", this);
        
        this.run();
    }
    
    public void run()
    {
        while(run)
        {
            synchronized(lock)
            {
                while(!bufferReady)
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
            
            // DO STUFF WITH IMAGE
            System.out.println(timeStamp);
            
            BufferedImage image = ImageConvert.convertToImage(format, width, height, imageBuffer);
            vbImage.addBuffered(new VisImage(new VisTexture(image),new double[] { 0., 0, }, 
                    new double[] {image.getWidth(), image.getHeight() }, true));
            vbImage.switchBuffer();
        }
    }
    
    public static void main(String[] args)
    {
        new CameraExample();
    }

    public void kill()
    {
        run = false;
    }
    
    @Override
    public void handleImage(byte[] im, ImageSourceFormat ifmt, double time)
    {
        synchronized(lock)
        {
            if(imageBuffer == null) 
            {
                imageBuffer = new byte[height*width];
            }
            
            imageBuffer = im;
            width = ifmt.width;
            height = ifmt.height;
            format = ifmt.format;
            timeStamp = time;
            bufferReady = true;
            lock.notify();
        }
    }
    

    @Override
    public void messageReceived(LCM lcm, String channel, LCMDataInputStream ins)
    {
        try
        {
            if (channel.contains("cam1"))
            {
                synchronized(lock)
                {
                    image_path_t path = new image_path_t(ins);
                    if(imageBuffer == null) 
                    {
                        imageBuffer = new byte[path.height*path.width];
                    }
                    
                    new FileInputStream(new File(path.img_path)).read(imageBuffer);
                    width = path.width;
                    height = path.height;
                    format = path.format;
                    timeStamp = path.utime;
                    bufferReady = true;
                    lock.notify();
                }
            }
        } catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
