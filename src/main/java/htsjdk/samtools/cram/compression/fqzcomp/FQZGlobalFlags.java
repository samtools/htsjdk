package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;

public class FQZGlobalFlags {
    public static final int MULTI_PARAM_FLAG_MASK = 0x01;
    public static final int SELECTOR_TABLE_FLAG_MASK = 0x02;
    public static final int DO_REVERSE_FLAG_MASK = 0x04;

    private int globalFlags;

    public FQZGlobalFlags(final ByteBuffer inBuffer) {
        this.globalFlags = inBuffer.get() & 0xFF;
    }

    // returns True if more than one parameter block is present
    public boolean isMultiParam(){
        return ((globalFlags & MULTI_PARAM_FLAG_MASK)!=0);
    }

    // returns True if the parameter selector is mapped through selector table
    public boolean hasSelectorTable(){
        return ((globalFlags & SELECTOR_TABLE_FLAG_MASK)!=0);
    }

    public boolean doReverse(){
        return ((globalFlags & DO_REVERSE_FLAG_MASK)!=0);
    }

}