package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsRecord;
import htsjdk.samtools.util.CloseableIterator;

/**
 * Query methods specific to aligned reads decoders. Reads-specific extension of HtsQuery)
 */
public interface ReadsQuery<R extends HtsRecord> {

    // query unmapped Reads
    CloseableIterator<R> queryUnmapped();

    // Fetch the mate for the given read.
    R queryMate(R rec);
}
