package mihai.tracker;

import java.util.ArrayList;
import java.util.HashMap;

public class ObjectManager 
{
	private ArrayList<Integer> objectIds;
	private HashMap<Integer, ArrayList<ImageObjectDetection> > hashedObjects;
	
	public ObjectManager()
	{
		objectIds = new ArrayList<Integer>();
		hashedObjects = new HashMap<Integer, ArrayList<ImageObjectDetection> >();
	}
	
	public void addObjects(ArrayList<ImageObjectDetection> objects)
	{
		for(ImageObjectDetection object : objects)
		{
			if(!objectIds.contains(object.id))
			{
				objectIds.add(object.id);
			}
			
			if(hashedObjects.get(object.id) == null)
			{
				hashedObjects.put(object.id, new ArrayList<ImageObjectDetection>());
			}
			
			if(!hashedObjects.get(object.id).contains(object))
			{
				hashedObjects.get(object.id).add(object);
			}
		}
	}

	@SuppressWarnings("unchecked")
	public ArrayList<Integer> getIds()
	{
		return (ArrayList<Integer>) objectIds.clone();
	}
	
	public ArrayList<ImageObjectDetection> getObjects(int id)
	{
		return hashedObjects.get(id);
	}
	
	public void clearObjects(int id)
	{
		hashedObjects.get(id).clear();
		objectIds.remove((Integer)id);
	}
}
