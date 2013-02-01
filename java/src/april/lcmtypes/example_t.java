/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package april.lcmtypes;
 
import java.io.*;
import java.util.*;
import lcm.lcm.*;
 
public final class example_t implements lcm.lcm.LCMEncodable
{
    public long timestamp;
    public double position[];
    public double orientation[];
    public int num_ranges;
    public short ranges[];
    public String name;
    public boolean enabled;
 
    public example_t()
    {
        position = new double[3];
        orientation = new double[4];
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0x1baa9e29b0fbaa8bL;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class<?>>());
    }
 
    public static long _hashRecursive(ArrayList<Class<?>> classes)
    {
        if (classes.contains(april.lcmtypes.example_t.class))
            return 0L;
 
        classes.add(april.lcmtypes.example_t.class);
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
        char[] __strbuf = null;
        outs.writeLong(this.timestamp); 
 
        for (int a = 0; a < 3; a++) {
            outs.writeDouble(this.position[a]); 
        }
 
        for (int a = 0; a < 4; a++) {
            outs.writeDouble(this.orientation[a]); 
        }
 
        outs.writeInt(this.num_ranges); 
 
        for (int a = 0; a < this.num_ranges; a++) {
            outs.writeShort(this.ranges[a]); 
        }
 
        __strbuf = new char[this.name.length()]; this.name.getChars(0, this.name.length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
 
        outs.writeByte( this.enabled ? 1 : 0); 
 
    }
 
    public example_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public example_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static april.lcmtypes.example_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        april.lcmtypes.example_t o = new april.lcmtypes.example_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        char[] __strbuf = null;
        this.timestamp = ins.readLong();
 
        this.position = new double[(int) 3];
        for (int a = 0; a < 3; a++) {
            this.position[a] = ins.readDouble();
        }
 
        this.orientation = new double[(int) 4];
        for (int a = 0; a < 4; a++) {
            this.orientation[a] = ins.readDouble();
        }
 
        this.num_ranges = ins.readInt();
 
        this.ranges = new short[(int) num_ranges];
        for (int a = 0; a < this.num_ranges; a++) {
            this.ranges[a] = ins.readShort();
        }
 
        __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.name = new String(__strbuf);
 
        this.enabled = ins.readByte()!=0;
 
    }
 
    public april.lcmtypes.example_t copy()
    {
        april.lcmtypes.example_t outobj = new april.lcmtypes.example_t();
        outobj.timestamp = this.timestamp;
 
        outobj.position = new double[(int) 3];
        System.arraycopy(this.position, 0, outobj.position, 0, 3); 
        outobj.orientation = new double[(int) 4];
        System.arraycopy(this.orientation, 0, outobj.orientation, 0, 4); 
        outobj.num_ranges = this.num_ranges;
 
        outobj.ranges = new short[(int) num_ranges];
        if (this.num_ranges > 0)
            System.arraycopy(this.ranges, 0, outobj.ranges, 0, this.num_ranges); 
        outobj.name = this.name;
 
        outobj.enabled = this.enabled;
 
        return outobj;
    }
 
}

