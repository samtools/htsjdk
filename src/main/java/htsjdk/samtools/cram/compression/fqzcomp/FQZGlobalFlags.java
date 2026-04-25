package htsjdk.samtools.cram.compression.fqzcomp;

import java.nio.ByteBuffer;

/**
 * Global flags byte for the FQZComp codec, read from the first byte after the version number
 * in the FQZComp parameter header. Controls whether multiple parameter blocks are present,
 * whether a selector table maps records to parameter blocks, and whether quality scores
 * should be reversed for reverse-complemented reads.
 */
public class FQZGlobalFlags {
    public static final int MULTI_PARAM_FLAG_MASK = 0x01;
    public static final int SELECTOR_TABLE_FLAG_MASK = 0x02;
    public static final int DO_REVERSE_FLAG_MASK = 0x04;

    private int globalFlags;

    public FQZGlobalFlags(final ByteBuffer inBuffer) {
        this.globalFlags = inBuffer.get() & 0xFF;
    }

    // returns True if more than one parameter block is present
    public boolean isMultiParam() {
        return ((globalFlags & MULTI_PARAM_FLAG_MASK) != 0);
    }

    // returns True if the parameter selector is mapped through selector table
    public boolean hasSelectorTable() {
        return ((globalFlags & SELECTOR_TABLE_FLAG_MASK) != 0);
    }

    public boolean doReverse() {
        return ((globalFlags & DO_REVERSE_FLAG_MASK) != 0);
    }
}
