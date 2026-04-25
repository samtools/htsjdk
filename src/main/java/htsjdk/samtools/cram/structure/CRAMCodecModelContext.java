package htsjdk.samtools.cram.structure;

import java.util.List;

/**
 * Context model data for use by CRAM 3.1 codec write implementations that need per-record metadata
 * beyond the raw byte stream. Populated during slice construction from the list of CRAM records,
 * then passed through to {@link htsjdk.samtools.cram.compression.ExternalCompressor#compress} calls.
 *
 * Currently used by FQZComp, which needs per-record quality score lengths and BAM flags to
 * properly compress quality scores with context modeling.
 */
public class CRAMCodecModelContext {

    private int[] qualityScoreLengths;
    private int[] bamFlags;

    /**
     * Populate this context from the records in a slice. Should be called during slice construction
     * before records are written to blocks.
     *
     * @param records the CRAM records for this slice
     */
    public void populateFromRecords(final List<CRAMCompressionRecord> records) {
        qualityScoreLengths = new int[records.size()];
        bamFlags = new int[records.size()];
        for (int i = 0; i < records.size(); i++) {
            final CRAMCompressionRecord record = records.get(i);
            qualityScoreLengths[i] = CRAMCompressionRecord.isForcePreserveQualityScores(record.getCRAMFlags())
                    ? record.getReadLength()
                    : 0;
            bamFlags[i] = record.getBAMFlags();
        }
    }

    /** @return per-record quality score lengths (one per record in the slice), or null if not populated */
    public int[] getQualityScoreLengths() {
        return qualityScoreLengths;
    }

    /** @return per-record BAM flags (one per record in the slice), or null if not populated */
    public int[] getBamFlags() {
        return bamFlags;
    }

    /** @return number of records, or 0 if not populated */
    public int getNumRecords() {
        return qualityScoreLengths != null ? qualityScoreLengths.length : 0;
    }
}
