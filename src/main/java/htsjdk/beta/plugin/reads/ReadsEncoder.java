package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

/**
 * Base interface for reads encoders.
 */
public interface ReadsEncoder extends HtsEncoder<ReadsFormat, SAMFileHeader, SAMRecord> { }
