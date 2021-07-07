package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

/**
 * Base interface for {@link HtsContentType#ALIGNED_READS} encoders.
 */
public interface ReadsEncoder extends HtsEncoder<SAMFileHeader, SAMRecord> { }
