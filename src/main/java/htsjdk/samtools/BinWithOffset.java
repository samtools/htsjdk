package htsjdk.samtools;

import htsjdk.samtools.Bin;

public class BinWithOffset extends Bin {

    private long lOffset;

    public long getlOffset() {
        return lOffset;
    }

    public void setlOffset(long lOffset) {
        this.lOffset = lOffset;
    }

    public BinWithOffset(int referenceSequence, int binNumber, long lOffset) {
        super(referenceSequence, binNumber);
        this.lOffset = lOffset;
    }
}
