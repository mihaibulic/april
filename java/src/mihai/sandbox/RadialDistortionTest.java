package mihai.sandbox;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import javax.swing.JFrame;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.util.JImage;
import april.util.ParameterGUI;

public class RadialDistortionTest
{
    JFrame jf;
    JImage jim;

    ImageSource isrc;

    ParameterGUI pg;

    public RadialDistortionTest(String url) throws IOException
    {
        jf = new JFrame("RadialDistortionTest");
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        jf.setLayout(new BorderLayout());

        jim = new JImage();
        jf.add(jim, BorderLayout.CENTER);

        pg = new ParameterGUI();
        jf.add(pg, BorderLayout.SOUTH);
        pg.addDoubleSlider("r", "r", 0.5, 2, 2.0);
        pg.addDoubleSlider("r2", "r2", -.005, .005, 0);
        isrc = ImageSource.make(url);

        jf.setSize(600,400);
        jf.setVisible(true);

        new RunThread().start();
    }

    class RunThread extends Thread
    {
        public void run()
        {
            ImageSourceFormat ifmt = isrc.getCurrentFormat();

            isrc.start();

            while (true) {
                byte imbuf[] = isrc.getFrame();

                BufferedImage im = ImageConvert.convertToImage(ifmt.format, ifmt.width, ifmt.height, imbuf);

                BufferedImage im2 = new BufferedImage(ifmt.width, ifmt.height, BufferedImage.TYPE_INT_RGB);

                double cx = ifmt.width / 2.0;
                double cy = ifmt.height / 2.0;

                double A = pg.gd("r");
                double B = pg.gd("r2");

                for (int y = 0; y < ifmt.height; y++) {
                    for (int x = 0; x < ifmt.width; x++) {

                        double dy = y - cy;
                        double dx = x - cx;

                        double theta = Math.atan2(dy, dx);
                        double r = Math.sqrt(dy*dy+dx*dx);

                        double rp = A*r + B*r*r;

                        int nx = (int) Math.round(cx + rp*Math.cos(theta));
                        int ny = (int) Math.round(cy + rp*Math.sin(theta));

                        if (nx >= 0 && nx < ifmt.width && ny >= 0 && ny < ifmt.height) {
                            im2.setRGB(x, y, im.getRGB((int) nx, (int) ny));
                        }
/*
                        nx = Math.max(0, nx);
                        ny = Math.max(0, ny);
                        nx = Math.min(ifmt.width - 1, nx);
                        ny = Math.min(ifmt.height - 1, ny);
*/

                    }
                }

                jim.setImage(im2);
            }
        }
    }

    public static void main(String args[])
    {
        try {
            new RadialDistortionTest(args[0]);
        } catch (IOException ex) {
            System.out.println("ex: "+ex);
        }
    }
}
