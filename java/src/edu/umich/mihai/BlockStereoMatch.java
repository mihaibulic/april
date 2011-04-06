package edu.umich.mihai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import april.jcam.ImageConvert;
import april.jmat.LinAlg;
import april.jmat.geom.GRay3D;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.util.TimeUtil;
import april.vis.HelpOutput;
import april.vis.VisCanvas;
import april.vis.VisCanvasEventHandler;
import april.vis.VisChain;
import april.vis.VisCircle;
import april.vis.VisDataFillStyle;
import april.vis.VisImage;
import april.vis.VisText;
import april.vis.VisTexture;
import april.vis.VisWorld;
import april.vis.VisText.ANCHOR;

public class BlockStereoMatch implements ParameterListener,
        VisCanvasEventHandler
{
    ParameterGUI pg;
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);
    VisWorld.Buffer vbClick = vw.getBuffer("click");
    VisWorld.Buffer vbImage = vw.getBuffer("images");
    BufferedImage imageLeft;
    BufferedImage imageRight;

    int widthLeft;
    int widthRight;
    byte[] left;
    byte[] right;
    int offset = 500;
    int slide = 16;
    int window = 4;

    public BlockStereoMatch()
    {
        pg = new ParameterGUI();
        pg.addIntSlider("slide", "distance to slide window", 1, 768, slide);
        pg.addIntSlider("window", "1/2 window width", 1, 1000, window);
        pg.addButtons("process", "process image");

        jf = new JFrame("Correlation test v0.0");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);
        jf.setSize(1000, 500);

        try
        {
            imageLeft = ImageIO.read(new File("/home/april/left"));
            imageRight = ImageIO.read(new File("/home/april/right"));
            left = new byte[imageLeft.getWidth()*imageLeft.getHeight()];
            right = new byte[imageRight.getWidth()*imageRight.getHeight()];
            widthLeft = imageLeft.getWidth();    
            widthRight = imageRight.getWidth();
            
            for(int y = 0; y< imageLeft.getHeight(); y++)
            {
                for(int x = 0; x< imageLeft.getWidth(); x++)
                {
                    left[widthLeft*y+x] = (byte)(imageLeft.getRGB(x, y)&0x0000FF);
                }
            }
            for(int y = 0; y< imageRight.getHeight(); y++)
            {
                for(int x = 0; x< imageRight.getWidth(); x++)
                {
                    right[widthRight*y+x] = (byte)(imageRight.getRGB(x, y)&0x0000FF);
                }
            }
            
            vbImage.addBuffered(new VisChain(new VisImage(new VisTexture(
                    imageLeft), new double[] { 0., 0, }, new double[] {
                    imageLeft.getWidth(), imageLeft.getHeight() }, true),
                    new VisImage(new VisTexture(imageRight), new double[] {
                            offset, 0, }, new double[] {
                            imageRight.getWidth() + offset,
                            imageRight.getHeight() }, true)));
            vbImage.switchBuffer();
        } catch (Exception e)
        {
            e.printStackTrace();
        }

        vc.getViewManager().viewGoal.fit2D(new double[] { 100, 100 },
                new double[] { 2000, 800 });
        vc.addEventHandler(this, 1);

        jf.setVisible(true);
        pg.addListener(this);

        while (true)
        {
            TimeUtil.sleep(100);
        }

    }

    /**
     * @param args
     */
    public static void main(String[] args)
    {
        new BlockStereoMatch();
    }

    private int getDisparity(int pointX, int pointY)
    {
        int disparity = 0;
        double min = Integer.MAX_VALUE;

        // window in right image
        for (int w = 0; w < slide; w++)
        {
            double differences = 0;
            if (pointX - w > window && pointY > window
                    && pointX + window < imageLeft.getWidth()
                    && pointY + window < imageLeft.getHeight())
            {
                // slide in columns of L/R image
                for (int y = pointY - window; y < pointY + window; y++)
                {
                    // slide in 1 row of L/R image
                    for (int x = pointX - window; x < pointX + window; x++)
                    {
                        byte bitxor = (byte)(left[widthLeft*y+x]^right[widthLeft*y+x-w]); // sum of hamming dist
                        int mask = 0x01;

                        for(int a = 0; a<8; a++)
                        {
                            differences += ((bitxor&mask)>0 ? 1 : 0 );
                            mask <<= 1;
                        }
                        
//                        differences += Math.abs(left[widthLeft*y+x]-right[widthLeft*y+x-w]); // sum of absolute diff
//                        differences += Math.pow(left[widthLeft*y+x]-right[widthLeft*y+x-w],2); // sum of square diff
                    }
                }

                if (differences < min)
                {
                    min = differences;
                    disparity = w;
                }
            }
        }

        return disparity;
    }

    private int getDisparity(GRay3D ray)
    {
        long time = System.currentTimeMillis();
        int disparity = getDisparity((int) ray.getSource()[0], (int) ray
                .getSource()[1]);
        System.out.println("disp of: " + disparity + " after time: "+ ((System.currentTimeMillis() - time) / 1000.));

        return disparity;
    }

    @Override
    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("slide"))
        {
            slide = pg.gi("slide");
            System.out.println("slide changed to " + slide);
        }
        else if (name.equals("window"))
        {
            window = pg.gi("window");
            System.out.println("window changed to " + window);
        }
        else if (name.equals("process"))
        {
            long time = System.currentTimeMillis();
            byte[] disparityImage = new byte[left.length];
            
            for (int x = 0; x < imageLeft.getWidth(); x++)
            {
                for (int y = 0; y < imageLeft.getHeight(); y++)
                {
                    int disperity = getDisparity(x, y);

                    disparityImage[widthLeft*y + x] = (byte)(255.*disperity/slide);
                }
            }

            vbImage.addBuffered(new VisChain(
                    new VisImage(new VisTexture(imageLeft), new double[] { 0., 0., 0.}, new double[] {imageLeft.getWidth(), imageLeft.getHeight(), 0. }, true),
                    new VisImage(new VisTexture(imageRight), new double[] {offset, 0., 0.}, 
                            new double[] {imageRight.getWidth() + offset,imageRight.getHeight(), 0. }, true),
                    new VisImage(new VisTexture(ImageConvert.convertToImage("GRAY8", widthRight, right.length/widthRight, disparityImage)), 
                            new double[] {offset, -1*offset, 0.}, new double[] {widthRight + offset, right.length/widthRight-offset, 0.}, true)));
            vbImage.switchBuffer();

            System.out.println("total time for whole image: "+ ((System.currentTimeMillis() - time) / 1000.));
        }
    }

    @Override
    public void doHelp(HelpOutput houts)
    {
    // TODO Auto-generated method stub

    }

    @Override
    public String getName()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void hoverNotify(boolean winner)
    {
    // TODO Auto-generated method stub

    }

    @Override
    public double hoverQuery(VisCanvas vc, GRay3D ray)
    {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public boolean keyPressed(VisCanvas vc, KeyEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean keyReleased(VisCanvas vc, KeyEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean keyTyped(VisCanvas vc, KeyEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseClicked(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        int x = (int) (ray.getSource()[0]);
        int y = (int) (ray.getSource()[1]);
        boolean ret = false;

        if (x > 0 && x < imageLeft.getWidth() - 1 && y > 0
                && y < imageLeft.getHeight() - 1)
        {
            vbClick.addBuffered(new VisChain(new VisText(new double[] { 0.,
                    -100. }, ANCHOR.CENTER, x + ", " + y), LinAlg.translate(ray
                    .getSource()), new VisCircle(10., new VisDataFillStyle(
                    Color.green)), LinAlg
                    .translate(new double[] { offset, 0, 0 }), new VisCircle(
                    10., new VisDataFillStyle(Color.blue)), LinAlg
                    .translate(new double[] { -1 * getDisparity(ray), 0, 0 }),
                    new VisCircle(10., new VisDataFillStyle(Color.red))));

            vbClick.switchBuffer();
            ret = true;
        }

        return ret;
    }

    @Override
    public boolean mouseDragged(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseMoved(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mousePressed(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseReleased(VisCanvas vc, GRay3D ray, MouseEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean mouseWheelMoved(VisCanvas vc, GRay3D ray, MouseWheelEvent e)
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public void pickNotify(boolean winner)
    {
    // TODO Auto-generated method stub

    }

    @Override
    public double pickQuery(VisCanvas vc, GRay3D ray)
    {
        // TODO Auto-generated method stub
        return 0;
    }

}