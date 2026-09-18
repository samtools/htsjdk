package htsjdk.samtools;

/**
 * An individual bin of a CSI index for BAM files.
 * Extends the BAI index bin {@link Bin} with a 64 bit value,
 * representing the virtual file offset of the first
 * overlapping record.
 *
 * @deprecated nothing in htsjdk creates one any longer; see {@link htsjdk.index.ReferenceBins#getLoffset(int)}
 */
@Deprecated
public class BinWithOffset extends Bin {

    private final long lOffset;

    public long getlOffset() {
        return lOffset;
    }

    public BinWithOffset(int referenceSequence, int binNumber, long lOffset) {
        super(referenceSequence, binNumber);
        this.lOffset = lOffset;
    }
}
