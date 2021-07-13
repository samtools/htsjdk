package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;

import java.util.Optional;

/**
 * Base class for all {@link HtsContentType#ALIGNED_READS} decoders. Defines the type parameters instantiated for
 * reads decoders.
 */
public interface ReadsDecoder extends HtsDecoder<SAMFileHeader, SAMRecord>, ReadsQuery<SAMRecord> {

    /**
     * {@inheritDoc}
     *
     * Requires an index resource to be included in the input {@link Bundle}.
     */
    @Override
    CloseableIterator<SAMRecord> queryUnmapped();

    /**
     * {@inheritDoc}
     *
     * Requires an index resource to be included in the input {@link Bundle}.
     */
    @Override
    Optional<SAMRecord> queryMate(SAMRecord rec);
}

