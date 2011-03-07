/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package edu.umich.mihai.lcmtypes;
 
import java.io.*;
import java.nio.*;
import java.util.*;
import lcm.lcm.*;
 
public final class led_t implements lcm.lcm.LCMEncodable
{
    public long utime;
    public double xyz[];
    public int id;
 
    public led_t()
    {
        xyz = new double[3];
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0xb43d08931d45b962L;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class>());
    }
 
    public static long _hashRecursive(ArrayList<Class> classes)
    {
        if (classes.contains(edu.umich.mihai.lcmtypes.led_t.class))
            return 0L;
 
        classes.add(edu.umich.mihai.lcmtypes.led_t.class);
        long hash = LCM_FINGERPRINT_BASE
            ;
        classes.remove(classes.size() - 1);
        return (hash<<1) + ((hash>>63)&1);
    }
 
    public void encode(DataOutput outs) throws IOException
    {
        outs.writeLong(LCM_FINGERPRINT);
        _encodeRecursive(outs);
    }
 
    public void _encodeRecursive(DataOutput outs) throws IOException
    {
        outs.writeLong(this.utime); 
 
        for (int a = 0; a < 3; a++) {
            outs.writeDouble(this.xyz[a]); 
        }
 
        outs.writeInt(this.id); 
 
    }
 
    public led_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public led_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static edu.umich.mihai.lcmtypes.led_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        edu.umich.mihai.lcmtypes.led_t o = new edu.umich.mihai.lcmtypes.led_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        this.utime = ins.readLong();
 
        this.xyz = new double[(int) 3];
        for (int a = 0; a < 3; a++) {
            this.xyz[a] = ins.readDouble();
        }
 
        this.id = ins.readInt();
 
    }
 
    public edu.umich.mihai.lcmtypes.led_t copy()
    {
        edu.umich.mihai.lcmtypes.led_t outobj = new edu.umich.mihai.lcmtypes.led_t();
        outobj.utime = this.utime;
 
        outobj.xyz = new double[(int) 3];
        System.arraycopy(this.xyz, 0, outobj.xyz, 0, 3); 
        outobj.id = this.id;
 
        return outobj;
    }
 
}

