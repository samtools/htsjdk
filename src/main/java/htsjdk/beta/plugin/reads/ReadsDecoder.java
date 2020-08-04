package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

/**
 * Base class for all reads decoders.
 */
public interface ReadsDecoder extends HtsDecoder<ReadsFormat, SAMFileHeader, SAMRecord>, ReadsQuery<SAMRecord> { }
