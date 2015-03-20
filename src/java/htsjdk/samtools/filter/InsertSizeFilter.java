package htsjdk.samtools.filter;

import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;

/**
 * Filter things that fall outside a specified range of insert sizes.
 * This will automatically omit unpaired reads.
 */
public class InsertSizeFilter implements SamRecordFilter {
    final int minInsertSize;
    final int maxInsertSize;

    public InsertSizeFilter(final int minInsertSize, final int maxInsertSize) {
        if (minInsertSize > maxInsertSize) throw new SAMException("Cannot have minInsertSize > maxInsertSize");
        this.minInsertSize = minInsertSize;
        this.maxInsertSize = maxInsertSize;
    }

    @Override
    public boolean filterOut(final SAMRecord rec) {
        if (!rec.getReadPairedFlag()) return true;
        final int ins = Math.abs(rec.getInferredInsertSize());
        return ins < minInsertSize || ins > maxInsertSize;
    }

    @Override
    public boolean filterOut(final SAMRecord r1, final SAMRecord r2) {
        return filterOut(r1) || filterOut(r2);
    }
}