/* LCM type definition class file
 * This file was automatically generated by lcm-gen
 * DO NOT MODIFY BY HAND!!!!
 */

package april.lcmtypes;
 
import java.io.*;
import java.nio.*;
import java.util.*;
import lcm.lcm.*;
 
public final class image_path_t implements lcm.lcm.LCMEncodable
{
    public long utime;
    public String id;
    public String dir;
    public String img_path;
    public int width;
    public int height;
    public String format;
 
    public image_path_t()
    {
    }
 
    public static final long LCM_FINGERPRINT;
    public static final long LCM_FINGERPRINT_BASE = 0x00eb7393d8ed2b35L;
 
    static {
        LCM_FINGERPRINT = _hashRecursive(new ArrayList<Class>());
    }
 
    public static long _hashRecursive(ArrayList<Class> classes)
    {
        if (classes.contains(april.lcmtypes.image_path_t.class))
            return 0L;
 
        classes.add(april.lcmtypes.image_path_t.class);
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
 
        __strbuf = new char[this.id.length()]; this.id.getChars(0, this.id.length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
 
        __strbuf = new char[this.dir.length()]; this.dir.getChars(0, this.dir.length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
 
        __strbuf = new char[this.img_path.length()]; this.img_path.getChars(0, this.img_path.length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
 
        outs.writeInt(this.width); 
 
        outs.writeInt(this.height); 
 
        __strbuf = new char[this.format.length()]; this.format.getChars(0, this.format.length(), __strbuf, 0); outs.writeInt(__strbuf.length+1); for (int _i = 0; _i < __strbuf.length; _i++) outs.write(__strbuf[_i]); outs.writeByte(0); 
 
    }
 
    public image_path_t(byte[] data) throws IOException
    {
        this(new LCMDataInputStream(data));
    }
 
    public image_path_t(DataInput ins) throws IOException
    {
        if (ins.readLong() != LCM_FINGERPRINT)
            throw new IOException("LCM Decode error: bad fingerprint");
 
        _decodeRecursive(ins);
    }
 
    public static april.lcmtypes.image_path_t _decodeRecursiveFactory(DataInput ins) throws IOException
    {
        april.lcmtypes.image_path_t o = new april.lcmtypes.image_path_t();
        o._decodeRecursive(ins);
        return o;
    }
 
    public void _decodeRecursive(DataInput ins) throws IOException
    {
        char[] __strbuf = null;
        this.utime = ins.readLong();
 
        __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.id = new String(__strbuf);
 
        __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.dir = new String(__strbuf);
 
        __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.img_path = new String(__strbuf);
 
        this.width = ins.readInt();
 
        this.height = ins.readInt();
 
        __strbuf = new char[ins.readInt()-1]; for (int _i = 0; _i < __strbuf.length; _i++) __strbuf[_i] = (char) (ins.readByte()&0xff); ins.readByte(); this.format = new String(__strbuf);
 
    }
 
    public april.lcmtypes.image_path_t copy()
    {
        april.lcmtypes.image_path_t outobj = new april.lcmtypes.image_path_t();
        outobj.utime = this.utime;
 
        outobj.id = this.id;
 
        outobj.dir = this.dir;
 
        outobj.img_path = this.img_path;
 
        outobj.width = this.width;
 
        outobj.height = this.height;
 
        outobj.format = this.format;
 
        return outobj;
    }
 
}
