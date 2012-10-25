package april.extrinsics;

import april.graph.GNode;

public class Node extends GNode
{
    boolean isCamera;
    String id;
    
    public Node(boolean isCamera, String id)
    {
        this(isCamera, id, null);
    }
    
    public Node(boolean isCamera, String id, double[] location)
    {
        super();
        
        this.isCamera = isCamera;
        this.id=id;
        
        if(location == null)
        {
            location = new double[]{0,0,0,0,0,0};
        }
        
        state = location;
        init = location;
    }
    
    @Override
    public GNode copy()
    {
        return null;
    }

    @Override
    public int getDOF()
    {
        return 0;
    }

    @Override 
    public boolean equals(Object a)
    {
        return id.equals(((Node)a).id);
    }

    @Override
    public double[] toXyzRpy(double[] s)
    {
        //TODO
        return null;
    }
}
