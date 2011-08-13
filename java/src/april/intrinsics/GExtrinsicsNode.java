package april.intrinsics;

import aprilO.jmat.LinAlg;
import aprilO.graph.GNode;

public class GExtrinsicsNode extends GNode
{
    // state:
    // 0-2: xyz
    // 3-5: rpy

    public int getDOF()
    {
        assert (state.length == 6);
        return state.length; // should be 6
    }

    public static double[] project(double state[], double p[])
    {
        double M[][] = LinAlg.xyzrpyToMatrix(state);

        return LinAlg.matrixAB(M, new double[] { p[0], p[1], 0, 1 });
    }

    public GExtrinsicsNode copy()
    {
        GExtrinsicsNode gn = new GExtrinsicsNode();
        gn.state = LinAlg.copy(state); 
        gn.init = LinAlg.copy(init);
        
        if (truth != null)
        {
            gn.truth = LinAlg.copy(truth);
        }
        
        gn.attributes = attributes.copy();
        return gn;
    }
}
