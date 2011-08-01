package mihai.calibrate.intrinsics;

import april.jmat.LinAlg;
import aprilO.graph.GNode;

public class GIntrinsicsNode extends GNode
{
    public GIntrinsicsNode(double fx, double fy, double cx, double cy)
    {
        state = new double[] { fx, fy, cx, cy, 0, 0, 0, 0, 0, 0 };
    }

    private GIntrinsicsNode()
    {}

    // state:
    // 0: fx
    // 1: fy
    // 2: cx
    // 3: cy
    // 4: distortion r coefficient
    // 5: distortion r^2 coefficient
    // 6: tangential coefficient
    // 7: tangential coefficient
    // 8: distortion r^4 coefficient
    // 9: alpha (skew)
    public int getDOF()
    {
        return state.length;
    }

    public static double[] project(double state[], double p[], int width, int height)
    {
        assert (p.length == 4); // 3D homogeneous coordinates in, please.

        double M[][] = new double[][] { { -state[0], 0, state[2], 0 }, { 0, state[1], state[3], 0 }, { 0, 0, 1, 0 } };
        double q[] = LinAlg.matrixAB(M, p);
        q[0] /= q[2];
        q[1] /= q[2];
        q[2] = 1;

        double centered[] = {(q[0]-state[2])/state[0],(q[1]-state[3])/state[1]};
        double r2 = centered[0]*centered[0] + centered[1]*centered[1];
        double scale = 1 + state[4]*r2 + state[5]*r2*r2;
        double scaled[] = {centered[0]*scale, centered[1]*scale};
        
        double tangential[] =
            {2 * state[6] * centered[0] * centered[1] +
             state[7] * (r2 + 2 * centered[0] * centered[0]),
             state[6] * (r2 + 2 * centered[1] * centered[1]) +
             2 * state[7] * centered[0] * centered[1]};

        scaled[0] = scaled[0]+tangential[0];
        scaled[1] = scaled[1]+tangential[1];

        return new double[] {state[0] * (scaled[0] + state[9] * scaled[1]) + state[2], scaled[1] * state[1] + state[3]};
    }

    public GIntrinsicsNode copy()
    {
        GIntrinsicsNode gn = new GIntrinsicsNode();
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