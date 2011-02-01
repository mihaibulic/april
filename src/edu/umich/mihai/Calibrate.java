package edu.umich.mihai;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import javax.swing.JFrame;
import april.jcam.ImageConvert;
import april.jcam.ImageSource;
import april.jcam.ImageSourceFormat;
import april.jmat.Function;
import april.jmat.LinAlg;
import april.jmat.Matrix;
import april.jmat.NumericalJacobian;
import april.tag.Tag36h11;
import april.tag.TagDetection;
import april.tag.TagDetector;
import april.tag.TagFamily;
import april.util.ParameterGUI;
import april.util.ParameterListener;
import april.vis.ColorUtil;
import april.vis.VisCanvas;
import april.vis.VisChain;
import april.vis.VisData;
import april.vis.VisDataFillStyle;
import april.vis.VisDataLineStyle;
import april.vis.VisDepthTest;
import april.vis.VisImage;
import april.vis.VisLighting;
import april.vis.VisRectangle;
import april.vis.VisText;
import april.vis.VisWorld;

public class Calibrate implements ParameterListener
{
    JFrame jf;
    VisWorld vw = new VisWorld();
    VisCanvas vc = new VisCanvas(vw);
    ImageSource is;
    ParameterGUI pg;
    TagFamily tagFamily = new Tag36h11();

    static int THETA_SCALE = 10000;
    static int WIDTH = 480;
    static int HEIGHT = 768;
    static double MINIMUM_LINE_LENGTH = 8; // in pixels

    boolean addArmed = false;
    ArrayList<double[][]> detections = new ArrayList<double[][]>();

    public static void main(String args[])
    {
        try
        {
            ArrayList<String> urls = ImageSource.getCameraURLs();

            String url = null;
            if (urls.size() == 1)
                url = urls.get(0);

            if (args.length > 0)
                url = args[0];

            ImageSource is = ImageSource.make(url);

            new Calibrate(is);

        } catch (IOException ex)
        {
            System.out.println("Ex: " + ex);
        }
    }

    public Calibrate(ImageSource is)
    {
        this.is = is;

        pg = new ParameterGUI();
        pg.addIntSlider("size", "size", 1, 240, 110);
        pg.addButtons("clear", "Clear detections", "add", "Add detections",
                "save", "Save detections", "load", "Load detections",
                "optimize", "Optimize", "reset", "Reset opt.", "toggle",
                "Toggle imagesource");

        jf = new JFrame("Calibrate");
        jf.setLayout(new BorderLayout());
        jf.add(vc, BorderLayout.CENTER);
        jf.add(pg, BorderLayout.SOUTH);

        jf.setSize(800, 600);
        jf.setVisible(true);
        jf.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        vc.getViewManager().viewGoal.fit2D(new double[] { 0, 0 }, new double[] { WIDTH, HEIGHT });
        new RunThread().start();

        pg.addListener(this);
    }

    public void parameterChanged(ParameterGUI pg, String name)
    {
        if (name.equals("clear"))
        {
            detections.clear();
        }
        else if (name.equals("add"))
        {
            addArmed = true;
        }
        else if (name.equals("save"))
        {
            save();
        }
        else if (name.equals("load"))
        {
            load();
        }
        else if (name.equals("optimize"))
        {
            for (int i = 0; i < 10; i++)
            {
                optimize();
            }
        }
        else if (name.equals("reset"))
        {
            x = null;
        }
        else if (name.equals("toggle"))
        {
            toggleImageSourceFormat(is);
        }
        else if (name.equals("size"))
        {
            double s = pg.gi("size");
            VisWorld.Buffer vbSize = vw.getBuffer("box");
            vbSize.addBuffered(new VisRectangle(new double[] {376-s,240-s,0}, new double[] {376+s,240+s,0}, new VisDataLineStyle(Color.red, 4)));
            vbSize.switchBuffer();
        }
        else
        {
            System.out.println("Parameter not found");
        }
    }

    void load()
    {
        detections.clear();

        try
        {
            BufferedReader ins = new BufferedReader(new FileReader(
                    "/tmp/calib.txt"));
            String line = null;

            while ((line = ins.readLine()) != null)
            {
                String toks[] = line.split("\\s+");
                double p[][] = new double[4][2];

                for (int i = 0; i < 4; i++)
                {
                    for (int j = 0; j < 2; j++)
                    {
                        p[i][j] = Double.parseDouble(toks[i * 2 + j]);
                    }
                }

                detections.add(p);
            }

        } catch (IOException ex)
        {
            System.out.println("ex: " + ex);
        }
    }

    void save()
    {
        try
        {
            BufferedWriter outs = new BufferedWriter(new FileWriter(
                    "/tmp/calib.txt"));
            for (double p[][] : detections)
            {
                for (int i = 0; i < 4; i++)
                    outs.write(String.format("%f %f ", p[i][0], p[i][1]));
                outs.write("\n");
            }

            outs.close();
        } catch (IOException ex)
        {
            System.out.println("ex: " + ex);
        }
    }

    class RunThread extends Thread
    {
        public void run()
        {
            is.start();
            ImageSourceFormat fmt = is.getCurrentFormat();

            TagDetector detector = new TagDetector(tagFamily);

            VisWorld.Buffer vbInput = vw.getBuffer("input");
            VisWorld.Buffer vbDetections = vw.getBuffer("detections");

            while (true)
            {
                byte buf[] = is.getFrame();
                if (buf == null)
                {
                    System.out.println("err getting frame");
                    toggleImageSourceFormat(is);
                    continue;
                }

                BufferedImage im = ImageConvert.convertToImage(fmt.format, fmt.width, fmt.height, buf);

                if (im == null)
                {
                    System.out.println("err converting to image");
                    toggleImageSourceFormat(is);
                    continue;
                }

                ArrayList<TagDetection> thedetections = detector.process(im, new double[] { im.getWidth() / 2.0, im.getHeight() / 2.0 });

                if (addArmed && thedetections.size() > 0)
                {
                    addArmed = false;
                    
                    for(TagDetection d : thedetections)
                    {
                        detections.add(d.p);                        
                    }
                }

                vbInput.addBuffered(new VisDepthTest(false, new VisLighting(false, new VisImage(im))));
                vbInput.switchBuffer();

                for (TagDetection d : thedetections)
                {
                    double p0[] = d.interpolate(-1, -1);
                    double p1[] = d.interpolate(1, -1);
                    double p2[] = d.interpolate(1, 1);
                    double p3[] = d.interpolate(-1, 1);

                    vbDetections.addBuffered(new VisChain(LinAlg.translate(0, im.getHeight(), 0),LinAlg.scale(1, -1, 1),
                                    new VisText(d.cxy,VisText.ANCHOR.CENTER,String.format("<<center,blue>>id %3d\n(err=%d)\n",d.id,d.hammingDistance)),
                                    new VisData(new VisDataLineStyle(Color.blue, 4), p0, p1, p2, p3, p0),
                                    new VisData(new VisDataLineStyle(Color.green, 4), p0, p1), // x axis
                                    new VisData(new VisDataLineStyle(Color.red,4), p0, p3))); // y axis
                }
                vbDetections.switchBuffer();
            }
        }

    }

    double x[] = null;
    
    void optDraw()
    {
        if (x == null)
        {
            return;
        }

        double f = x[0]; // focal length
        double cx = x[1]; // optical center
        double cy = x[2];

        VisWorld.Buffer vb = vw.getBuffer("opt");

        for (int didx = 0; didx < detections.size(); didx++)
        {
            double p[][] = detections.get(didx);
            double a0 = x[NCAM_PARAMS + didx * 4 + 0];
            double a1 = x[NCAM_PARAMS + didx * 4 + 1];
            double a2 = x[NCAM_PARAMS + didx * 4 + 2];
            double a3 = x[NCAM_PARAMS + didx * 4 + 3];

            double cxyz[] = new double[] { cx, cy, -f };

            double xhat[][] = new double[4][3];
            for (int i = 0; i < 4; i++)
            {
                xhat[i][0] = p[i][0] - cx;
                xhat[i][1] = p[i][1] - cy;
                xhat[i][2] = f;
                xhat[i] = LinAlg.normalize(xhat[i]);
            }

            ArrayList<double[]> fit = new ArrayList<double[]>();

            fit.add(LinAlg.add(cxyz, LinAlg.scale(xhat[0], f * a0)));
            fit.add(LinAlg.add(cxyz, LinAlg.scale(xhat[1], f * a1)));
            fit.add(LinAlg.add(cxyz, LinAlg.scale(xhat[2], f * a2)));
            fit.add(LinAlg.add(cxyz, LinAlg.scale(xhat[3], f * a3)));

            for (int i = 0; i < 4; i++)
            {
                vb.addBuffered(new VisChain(new VisData(new VisDataLineStyle(
                        Color.blue, 1), cxyz, LinAlg.add(cxyz, LinAlg.scale(
                        xhat[i], 10000)))));
            }

            vb.addBuffered(new VisData(new VisDataFillStyle(ColorUtil.seededColor(didx)), fit));

        }
        vb.switchBuffer();
    }

    // double f = 300;
    // double cx = 752/2;
    // double cy = 480/2;

    static final int NCAM_PARAMS = 3;

    void optimize()
    {
        double d = 0.028; // size of target along edge (in meters)

        // initial state.
        if (x == null || x.length != NCAM_PARAMS + detections.size() * 4)
        {
            x = new double[NCAM_PARAMS + detections.size() * 4];
            x[0] = 550;
            x[1] = 752/2;
            x[2] = 480/2;

            for (int i = NCAM_PARAMS; i < x.length; i++)
            {
                x[i] = 1;
            }
        }

        optDraw();

        // epsilon used to numerically compute jacobian
        double eps[] = new double[NCAM_PARAMS + detections.size() * 4];
        for (int i = 0; i < eps.length; i++)
        {
            eps[i] = 0.0001; // units: meters
        }

        // ideal value of our function. (distances between points in
        // observation)
        double b[] = new double[6 * detections.size()];
        for (int i = 0; i < detections.size(); i++)
        {
            b[6 * i + 0] = d * d;
            b[6 * i + 1] = d * d;
            b[6 * i + 2] = d * d;
            b[6 * i + 3] = d * d;
            b[6 * i + 4] = 2 * d * d; // diagonals
            b[6 * i + 5] = 2 * d * d;
        }

        MyFunction func = new MyFunction();

        // residual
        double r[] = LinAlg.subtract(b, func.evaluate(x, null));
        
        // compute jacobian
        double _J[][] = NumericalJacobian.computeJacobian(func, x, eps);
        Matrix J = new Matrix(_J);

        System.out.printf("\n\nResidual:\n");
        for (int i = 0; i < r.length; i++)
        {
            System.out.printf("%8f ", r[i]);
        }
        System.out.printf("\n");

        // LinAlg.print(func.evaluate(x, null));
        // LinAlg.print(r);
        // J.print();

        // do a least squares step
        Matrix JTJ = J.transpose().times(J);
        Matrix JTr = J.transpose().times(Matrix.columnMatrix(r));
        Matrix dx = JTJ.solve(JTr);

        System.out.printf("f " + x[0] + "\tdx " + dx.get(0,0));  
        System.out.printf("\n\nState: \n");
        
        for (int i = 0; i < dx.getRowDimension(); i++)
        {
//            if( i!=1 && i !=2)
//            if(i != 0 && i!=1 && i !=2)
//            {
                x[i] += 0.1*dx.get(i, 0);
//            }
            
            System.out.printf("%8f ", x[i]);
        }
        
        System.out.printf("\n");
    }

    class MyFunction extends Function
    {
        public double[] evaluate(double x[], double y[])
        {
            double f = x[0]; // focal length
            double cx = x[1]; // optical center
            double cy = x[2];

            if (y == null)
            {
                y = new double[detections.size() * 6];
            }

            for (int didx = 0; didx < detections.size(); didx++)
            {
                double p[][] = detections.get(didx);
                double a0 = x[NCAM_PARAMS + didx * 4 + 0];
                double a1 = x[NCAM_PARAMS + didx * 4 + 1];
                double a2 = x[NCAM_PARAMS + didx * 4 + 2];
                double a3 = x[NCAM_PARAMS + didx * 4 + 3];

                // Determine pixel locations for each observation
                double xhat[][] = new double[4][3];
                for (int i = 0; i < 4; i++)
                {
                    xhat[i][0] = p[i][0] - cx;
                    xhat[i][1] = p[i][1] - cy;
                    xhat[i][2] = f;
                    xhat[i] = LinAlg.normalize(xhat[i]);
                }                 
                
                // Compute squared distance between each point in this
                // observation
                double dot01 = LinAlg.dotProduct(xhat[0], xhat[1]);
                double dot12 = LinAlg.dotProduct(xhat[1], xhat[2]);
                double dot23 = LinAlg.dotProduct(xhat[2], xhat[3]);
                double dot03 = LinAlg.dotProduct(xhat[3], xhat[0]);
                double dot02 = LinAlg.dotProduct(xhat[0], xhat[2]);
                double dot13 = LinAlg.dotProduct(xhat[1], xhat[3]);

                // System.out.printf("%15f %15f %15f %15f : %15f %15f %15f %15f\n",
                // a0, a1, a2, a3, dot01, dot12, dot23, dot03);
                
                // our function equations which compute the distance between the
                // points
                y[6 * didx + 0] = a0 * a0 - 2 * a0 * a1 * dot01 + a1 * a1;
                y[6 * didx + 1] = a1 * a1 - 2 * a1 * a2 * dot12 + a2 * a2;
                y[6 * didx + 2] = a2 * a2 - 2 * a2 * a3 * dot23 + a3 * a3;
                y[6 * didx + 3] = a3 * a3 - 2 * a3 * a0 * dot03 + a0 * a0;
                y[6 * didx + 4] = a0 * a0 - 2 * a0 * a2 * dot02 + a2 * a2;
                y[6 * didx + 5] = a1 * a1 - 2 * a1 * a3 * dot13 + a3 * a3;
            }

            return y;
        }
    }

    double sq(double a)
    {
        return a*a;
    }
    
    double squaredDistance(double[] p1, double[] p2) throws Exception
    {
        if(p1.length < 2 || p2.length < 2)
        {
            throw new Exception("Points must contains u and v");
        }
        
        double u = (p1[0]-p2[0])*(p1[0]-p2[0]);
        double v = (p1[1]-p2[1])*(p1[1]-p2[1]);
        
        return (u+v);
    }
    
    void toggleImageSourceFormat(ImageSource isrc)
    {
        System.out.println("Toggling imagesource");

        isrc.stop();
        int currentFormat = isrc.getCurrentFormatIndex();
        isrc.setFormat(currentFormat);

        isrc.start();
    }
}
