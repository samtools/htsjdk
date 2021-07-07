package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;

import java.util.Optional;

/**
 * Base class for all reads decoders. Defines the type parameters instantiated for reads decoders.
 */
public interface ReadsDecoder extends HtsDecoder<ReadsFormat, SAMFileHeader, SAMRecord>, ReadsQuery<SAMRecord> {

    /**
     * {@InheritDoc}
     *
     * Requires an index resource to be included in the input {@link Bundle}.
     */
    @Override
    CloseableIterator<SAMRecord> queryUnmapped();

    /**
     * {@InheritDoc}
     *
     * Requires an index resource to be included in the input {@link Bundle}.
     */
    @Override
    Optional<SAMRecord> queryMate(SAMRecord rec);
}

