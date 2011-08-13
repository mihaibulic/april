package april.extrinsics;

import java.io.IOException;
import aprilO.graph.GEdge;
import aprilO.graph.GNode;
import aprilO.graph.Graph;
import aprilO.graph.Linearization;
import aprilO.jmat.LinAlg;
import aprilO.util.StructureReader;
import aprilO.util.StructureWriter;

public class Edge extends GEdge
{
    double[] xyzrpy;
    
    public Edge(int[] nodes, double[] xyzrpy)
    {
        this.nodes = nodes; 
        this.xyzrpy = xyzrpy;
    }
    
    public double getResidual(Graph g)
    {
        Node n1 = (Node)g.nodes.get(nodes[0]);
        Node n2 = (Node)g.nodes.get(nodes[1]);
        double[] curXyzrpy = LinAlg.xyzrpyMultiply(n1.state, n2.state);
        
        return LinAlg.distance(curXyzrpy, xyzrpy);
    }
    
    @Override
    public double getChi2(Graph g)
    {
        return LinAlg.sq(getResidual(g));
    }

    @Override
    public int getDOF()
    {
        return xyzrpy.length;
    }

    @Override
    public Linearization linearize(Graph g, Linearization lin)
    {
        if (lin == null)
        {
            lin = new Linearization();

            lin.J.add(new double[1][g.nodes.get(nodes[0]).state.length]);
            lin.J.add(new double[1][g.nodes.get(nodes[1]).state.length]);
            
            lin.R = new double[]{getResidual(g)};
            lin.W = LinAlg.identity(1);
        }

        for (int nidx = 0; nidx < nodes.length; nidx++)
        {
            GNode gn = g.nodes.get(nodes[nidx]);

            double s[] = LinAlg.copy(gn.state);
            for (int i = 0; i < gn.state.length; i++)
            {
                double eps = Math.max(0.001, Math.abs(gn.state[i]) / 1000);

                gn.state[i] = s[i] + eps;
                double chiplus = LinAlg.sq(getResidual(g));

                gn.state[i] = s[i] - eps;
                double chiminus = LinAlg.sq(getResidual(g));

                lin.J.get(nidx)[0][i] = (chiplus - chiminus) / (2 * eps);

                gn.state[i] = s[i];
            }
        }

        return lin;
    }

    @Override
    public GEdge copy()
    {
        assert(false);
        return null;
    }

    @Override
    public void read(StructureReader ins) throws IOException
    {
        assert(false);
    }

    @Override
    public void write(StructureWriter outs) throws IOException
    {
        assert(false);
    }
}
