package mihai.calibrate.intrinsics;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import april.jmat.LinAlg;
import april.vis.VisChain;
import april.vis.VisData;
import april.vis.VisDataLineStyle;
import april.vis.VisDataPointStyle;
import april.vis.VisImage;
import april.vis.VisWorld;
import aprilO.graph.GEdge;
import aprilO.graph.GNode;
import aprilO.graph.Graph;
import aprilO.graph.Linearization;
import aprilO.util.StructureReader;
import aprilO.util.StructureWriter;

public class GCalibrateEdge extends GEdge
{
    // each correspondence is: worldx, worldy, imagex, imagey
    ArrayList<double[]> correspondences;
    BufferedImage im;

    public GCalibrateEdge(ArrayList<double[]> correspondences, BufferedImage im)
    {
        this.nodes = new int[] { -1, -1 }; // make sure someone sets us
                                           // later.
        this.correspondences = correspondences;
        this.im = im;
    }

    public int getDOF()
    {
        return correspondences.size();
    }

    public void draw(Graph g, VisWorld.Buffer vb, double xoff, double yoff, double xsize)
    {
        ArrayList<double[]> projected = new ArrayList<double[]>();
        VisChain errs = new VisChain();

        for (double corr[] : correspondences)
        {
            double pp[] = project(g, new double[] { corr[0], corr[1] });
            projected.add(new double[] { pp[0], pp[1] });
            ArrayList<double[]> line = new ArrayList<double[]>();
            line.add(new double[] { corr[2], corr[3] });
            line.add(new double[] { pp[0], pp[1] });
            errs.add(new VisData(line, new VisDataLineStyle(Color.orange, 1)));
        }

        vb.addBuffered(new VisChain(LinAlg.translate(xoff, yoff, 0), LinAlg.scale(xsize / im.getWidth(), xsize / im.getWidth(), 1), new VisImage(im), LinAlg.translate(0, im.getHeight(), 0), LinAlg.scale(1, -1, 1), errs, new VisData(projected, new VisDataPointStyle(Color.cyan, 3))));
    }

    double[] project(Graph g, double worldxy[])
    {
        GIntrinsicsNode gin = (GIntrinsicsNode) g.nodes.get(nodes[0]);
        GExtrinsicsNode gex = (GExtrinsicsNode) g.nodes.get(nodes[1]);

        return GIntrinsicsNode.project(gin.state, GExtrinsicsNode.project(gex.state, worldxy), im.getWidth(), im.getHeight());
    }

    public double getChi2(Graph g)
    {
        double err2 = 0;
        for (double corr[] : correspondences)
        {
            err2 += LinAlg.sq(getResidual(g, corr));
        }

        return err2;
    }

    public double getResidual(Graph g, double corr[])
    {
        double p[] = project(g, new double[] { corr[0], corr[1] });
        return LinAlg.distance(p, new double[] { corr[2], corr[3] });
    }

    public Linearization linearize(Graph g, Linearization lin)
    {
        if (lin == null)
        {
            lin = new Linearization();

            for (int nidx = 0; nidx < nodes.length; nidx++)
            {
                lin.J.add(new double[correspondences.size()][g.nodes.get(nodes[nidx]).state.length]);
            }

            lin.R = new double[correspondences.size()];
            lin.W = LinAlg.identity(correspondences.size());

            // chi2 is sum of error of each correspondence, so W
            // should just be 1.
        }

        for (int cidx = 0; cidx < correspondences.size(); cidx++)
        {
            lin.R[cidx] = getResidual(g, correspondences.get(cidx));

            for (int nidx = 0; nidx < nodes.length; nidx++)
            {
                GNode gn = g.nodes.get(nodes[nidx]);

                double s[] = LinAlg.copy(gn.state);
                for (int i = 0; i < gn.state.length; i++)
                {
                    double eps = Math.max(0.001, Math.abs(gn.state[i]) / 1000);

                    gn.state[i] = s[i] + eps;
                    double chiplus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                    gn.state[i] = s[i] - eps;
                    double chiminus = LinAlg.sq(getResidual(g, correspondences.get(cidx)));

                    lin.J.get(nidx)[cidx][i] = (chiplus - chiminus) / (2 * eps);

                    gn.state[i] = s[i];
                }
            }
        }

        return lin;
    }

    public GCalibrateEdge copy()
    {
        assert (false);
        return null;
    }

    public void write(StructureWriter outs) throws IOException
    {
        assert (false);
    }

    public void read(StructureReader ins) throws IOException
    {
        assert (false);
    }
}