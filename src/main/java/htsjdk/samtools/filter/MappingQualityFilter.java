package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;

/**
 * Filter things with low mapping quality.
 */
public class MappingQualityFilter implements SamRecordFilter {

    private int minimumMappingQuality = Integer.MIN_VALUE;

    public MappingQualityFilter(final int minimumMappingQuality) {
        this.minimumMappingQuality = minimumMappingQuality;
    }

    @Override
    public boolean filterOut(final SAMRecord record) {
        return record.getMappingQuality() < this.minimumMappingQuality;
    }

    @Override
    public boolean filterOut(final SAMRecord first, final SAMRecord second) {
        return filterOut(first) || filterOut(second);
    }
}
