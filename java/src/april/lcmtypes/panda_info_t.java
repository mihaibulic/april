/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package april.lcmtypes;
 
import java.io.*;
import java.util.*;
import lcm.lcm.*;
 
public final class panda_info_t implements lcm.lcm.LCMEncodable
{
    public long utime;
    public int num_of_formats;
    public int width[];
    public int height[];
    public String format[];
    public int format_index;
    public int num_of_features;
    public String feature_names[];
    public double feature_min[];
    public double feature_max[];
    public double feature_values[];
 
    public panda_info_t()
    {
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0x28045d77c6270bdaL;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class<?>>());
    }
 
    public static long _hashRecursive(ArrayList<Class<?>> classes)
    {
        if (classes.contains(april.lcmtypes.panda_info_t.class))
            return 0L;
 
        classes.add(april.lcmtypes.panda_info_t.class);
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
        outs.writeLong(this.utime); 
 
        outs.writeInt(this.num_of_formats); 
 
        for (int a = 0; a < this.num_of_formats; a++) {
            outs.writeInt(this.width[a]); 
        }
 
        for (int a = 0; a < this.num_of_formats; a++) {
            outs.writeInt(this.height[a]); 
        }
 
        for (int a = 0; a < this.num_of_formats; a++) {
            __strbuf = new char[this.format[a].length()]; this.format[a].getChars(0, this.format[a].length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
        }
 
        outs.writeInt(this.format_index); 
 
        outs.writeInt(this.num_of_features); 
 
        for (int a = 0; a < this.num_of_features; a++) {
            __strbuf = new char[this.feature_names[a].length()]; this.feature_names[a].getChars(0, this.feature_names[a].length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
        }
 
        for (int a = 0; a < this.num_of_features; a++) {
            outs.writeDouble(this.feature_min[a]); 
        }
 
        for (int a = 0; a < this.num_of_features; a++) {
            outs.writeDouble(this.feature_max[a]); 
        }
 
        for (int a = 0; a < this.num_of_features; a++) {
            outs.writeDouble(this.feature_values[a]); 
        }
 
    }
 
    public panda_info_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public panda_info_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static april.lcmtypes.panda_info_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        april.lcmtypes.panda_info_t o = new april.lcmtypes.panda_info_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        char[] __strbuf = null;
        this.utime = ins.readLong();
 
        this.num_of_formats = ins.readInt();
 
        this.width = new int[(int) num_of_formats];
        for (int a = 0; a < this.num_of_formats; a++) {
            this.width[a] = ins.readInt();
        }
 
        this.height = new int[(int) num_of_formats];
        for (int a = 0; a < this.num_of_formats; a++) {
            this.height[a] = ins.readInt();
        }
 
        this.format = new String[(int) num_of_formats];
        for (int a = 0; a < this.num_of_formats; a++) {
            __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.format[a] = new String(__strbuf);
        }
 
        this.format_index = ins.readInt();
 
        this.num_of_features = ins.readInt();
 
        this.feature_names = new String[(int) num_of_features];
        for (int a = 0; a < this.num_of_features; a++) {
            __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.feature_names[a] = new String(__strbuf);
        }
 
        this.feature_min = new double[(int) num_of_features];
        for (int a = 0; a < this.num_of_features; a++) {
            this.feature_min[a] = ins.readDouble();
        }
 
        this.feature_max = new double[(int) num_of_features];
        for (int a = 0; a < this.num_of_features; a++) {
            this.feature_max[a] = ins.readDouble();
        }
 
        this.feature_values = new double[(int) num_of_features];
        for (int a = 0; a < this.num_of_features; a++) {
            this.feature_values[a] = ins.readDouble();
        }
 
    }
 
    public april.lcmtypes.panda_info_t copy()
    {
        april.lcmtypes.panda_info_t outobj = new april.lcmtypes.panda_info_t();
        outobj.utime = this.utime;
 
        outobj.num_of_formats = this.num_of_formats;
 
        outobj.width = new int[(int) num_of_formats];
        if (this.num_of_formats > 0)
            System.arraycopy(this.width, 0, outobj.width, 0, this.num_of_formats); 
        outobj.height = new int[(int) num_of_formats];
        if (this.num_of_formats > 0)
            System.arraycopy(this.height, 0, outobj.height, 0, this.num_of_formats); 
        outobj.format = new String[(int) num_of_formats];
        if (this.num_of_formats > 0)
            System.arraycopy(this.format, 0, outobj.format, 0, this.num_of_formats); 
        outobj.format_index = this.format_index;
 
        outobj.num_of_features = this.num_of_features;
 
        outobj.feature_names = new String[(int) num_of_features];
        if (this.num_of_features > 0)
            System.arraycopy(this.feature_names, 0, outobj.feature_names, 0, this.num_of_features); 
        outobj.feature_min = new double[(int) num_of_features];
        if (this.num_of_features > 0)
            System.arraycopy(this.feature_min, 0, outobj.feature_min, 0, this.num_of_features); 
        outobj.feature_max = new double[(int) num_of_features];
        if (this.num_of_features > 0)
            System.arraycopy(this.feature_max, 0, outobj.feature_max, 0, this.num_of_features); 
        outobj.feature_values = new double[(int) num_of_features];
        if (this.num_of_features > 0)
            System.arraycopy(this.feature_values, 0, outobj.feature_values, 0, this.num_of_features); 
        return outobj;
    }
 
}
