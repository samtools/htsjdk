package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.codecs.reads.ReadsCodecUtils;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#FMT_READS_CRAM} decoders.
 */
@InternalAPI
public abstract class CRAMDecoder implements ReadsDecoder {
    private final Bundle inputBundle;
    private final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;
    private final SAMFileHeader samFileHeader;
    private boolean iteratorExists = false;
    private final SamReader samReader;

    /**
     * InternalAPI
     *
     * Common constructor for CRAM decoders.
     *
     * @param inputBundle an {@link Bundle} containing cram resource
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use.
     */
    @InternalAPI
    public CRAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "inputBundle");
        ValidationUtils.nonNull(readsDecoderOptions, "inputBundle");

        this.inputBundle = inputBundle;
        this.readsDecoderOptions = readsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.CT_ALIGNED_READS).getDisplayName();

        samReader = getSamReaderForCRAM(inputBundle, readsDecoderOptions);
        samFileHeader = samReader.getFileHeader();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    @Override
    public void close() {
        try {
            samReader.close();
        } catch (final IOException e) {
            throw new HtsjdkIOException(String.format("Failure closing CRAM reader stream", getDisplayName()), e);
        }
    }

    @Override
    public CloseableIterator<SAMRecord> iterator() { return getIteratorMonitor(() -> samReader.iterator()); }

    @Override
    public boolean isQueryable() {
        return ReadsCodecUtils.bundleContainsIndex(getInputBundle()) && samReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return ReadsCodecUtils.bundleContainsIndex(getInputBundle()) && samReader.hasIndex();
    }

    @Override
    public CloseableIterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        ValidationUtils.nonNull(intervals, "intervals");
        ValidationUtils.nonNull(queryRule, "queryRule");

        final QueryInterval[] queryIntervals = HtsIntervalUtils.toQueryIntervalArray(
                intervals,
                samFileHeader.getSequenceDictionary());
        return getIteratorMonitor(() -> samReader.query(queryIntervals, queryRule == HtsQueryRule.CONTAINED));
    }

    @Override
    public CloseableIterator<SAMRecord> queryStart(final String queryName, final long start) {
        ValidationUtils.nonNull(queryName, "queryName");
        return getIteratorMonitor(() -> samReader.queryAlignmentStart(queryName, HtsIntervalUtils.toIntegerSafe(start)));
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        return getIteratorMonitor(() -> samReader.queryUnmapped());
    }

    // This method is a slightly modified version of the shared implementation in
    // SamReader. It delegates to other query methods on this decoder (queryUnmapped and queryStart),
    // which will throw if an existing iterator is already opened
    @Override
    public Optional<SAMRecord> queryMate(SAMRecord rec) {
        ValidationUtils.nonNull(rec, "rec");

        if (!rec.getReadPairedFlag()) {
            throw new IllegalArgumentException(String.format("queryMate called for unpaired read on %s.", getDisplayName()));
        }
        if (rec.getFirstOfPairFlag() == rec.getSecondOfPairFlag()) {
            throw new IllegalArgumentException(String.format("SAMRecord must be either first and second of pair, but not both (%s).",
                    getDisplayName()));
        }
        final boolean firstOfPair = rec.getFirstOfPairFlag();
        // its important that this method closes the iterators it creates, since otherwise the caller
        // will never be able to create another iterator after calling this method
        try (final CloseableIterator<SAMRecord> it =
                     rec.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX ?
                             queryUnmapped() :
                             queryStart(rec.getMateReferenceName(), rec.getMateAlignmentStart())) {
            SAMRecord mateRec = null;
            while (it.hasNext()) {
                final SAMRecord next = it.next();
                if (!next.getReadPairedFlag()) {
                    if (rec.getReadName().equals(next.getReadName())) {
                        throw new SAMFormatException(String.format("Paired and unpaired reads with same name: %s (on %s)",
                                rec.getReadName(),
                                getInputBundle()));
                    }
                    continue;
                }
                if (firstOfPair) {
                    if (next.getFirstOfPairFlag()) continue;
                } else {
                    if (next.getSecondOfPairFlag()) continue;
                }
                if (rec.getReadName().equals(next.getReadName())) {
                    if (mateRec != null) {
                        throw new SAMFormatException(
                                String.format("Multiple SAMRecord with read name %s for %s end on %s.",
                                        rec.getReadName(),
                                        (firstOfPair ? "second" : "first"),
                                        getInputBundle()));
                    }
                    mateRec = next;
                }
            }
            return Optional.ofNullable(mateRec);
        }
    }

    /**
     * Get the input {@link Bundle} for this decoder.
     *
     * @return the input {@link Bundle} for this decoder
     */
    public Bundle getInputBundle() {
        return inputBundle;
    }

    /**
     * Get the {@link ReadsDecoderOptions} for this decoder.
     *
     * @return the {@link ReadsDecoderOptions} for this decoder.
     */
    public ReadsDecoderOptions getReadsDecoderOptions() {
        return readsDecoderOptions;
    }

    // TODO: If we've been handed a CRAMReferenceSource from the caller, then we don't want to close it
    // when the decoder is closed, but if we create it, then we need to close it.
    //TODO: creation of the source should be separate from the getting of the source, and the result
    // cached, so we don't create multiple reference Sources
    @InternalAPI
    public static CRAMReferenceSource getCRAMReferenceSource(final CRAMDecoderOptions cramDecoderOptions) {
        ValidationUtils.nonNull(cramDecoderOptions, "cramDecoderOptions");

        if (cramDecoderOptions.getReferenceSource().isPresent()) {
            return cramDecoderOptions.getReferenceSource().get();
        } else if (cramDecoderOptions.getReferencePath().isPresent()) {
            return CRAMCodec.getCRAMReferenceSource(cramDecoderOptions.getReferencePath().get());
        }
        // if none is specified, get the default "lazy" reference source that throws when queried, to allow
        // operations that don't require a reference
        return ReferenceSource.getDefaultCRAMReferenceSource();
    }

    // create an iterator wrapper that can notify this decoder when its closed, so the decoder can ensure
    // that only one iterator is ever outstanding at a time
    private CloseableIterator<SAMRecord> getIteratorMonitor(final Supplier<CloseableIterator<SAMRecord>> newIterator) {
        toggleIteratorExists(true);
        return new CloseableIteratorMonitor<>(newIterator.get());
    }

    private void toggleIteratorExists(final boolean newState) {
        if (iteratorExists == newState) {
            if (iteratorExists == true) {
                throw new IllegalStateException(String.format(
                        "The previous iterator must be closed before starting a new iterator on %s", getDisplayName()));
            } else {
                // this indicates a problem with this codec
                throw new IllegalStateException(String.format("No outstanding iterator exists for %s", getDisplayName()));
            }
        }
        // reset the iterator monitor
        iteratorExists = newState;
    }

    // Iterator wrapper to monitor attempts to open more than one iterator.
    private class CloseableIteratorMonitor<T> implements CloseableIterator<T> {
        final CloseableIterator<T> wrappedIterator;

        public CloseableIteratorMonitor(final CloseableIterator<T> wrappedIterator) {
            ValidationUtils.nonNull(wrappedIterator, "wrappedIterator");
            this.wrappedIterator = wrappedIterator;
        }

        @Override
        public void close() {
            // notify the outer class iterator monitor that the iterator is closed
            CRAMDecoder.this.toggleIteratorExists(false);
            wrappedIterator.close();
        }

        @Override
        public boolean hasNext() { return wrappedIterator.hasNext(); }

        @Override
        public T next() {
            return wrappedIterator.next();
        }
    }

    // Propagate all reads decoder options and all bam decoder options to either a SamReaderFactory
    // or a SamInputResource, and return the resulting SamReader
    private static SamReader getSamReaderForCRAM(
            final Bundle inputBundle,
            final ReadsDecoderOptions readsDecoderOptions) {
        // note that some reads decoder options, such as cloud wrapper values, need to be propagated
        // to the samInputResource, not to the SamReaderFactory
        final SamInputResource samInputResource =
                ReadsCodecUtils.bundleToSamInputResource(inputBundle, readsDecoderOptions);
        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        cramDecoderOptionsToSamReaderFactory(samReaderFactory, readsDecoderOptions.getCRAMDecoderOptions());

        return samReaderFactory.open(samInputResource);
    }

    private static void cramDecoderOptionsToSamReaderFactory(
            final SamReaderFactory samReaderFactory,
            final CRAMDecoderOptions cramDecoderOptions) {
        //TODO: CRAMFileReader doesn't honor the requested inflater, but it should
        //samReaderFactory.inflaterFactory(cramDecoderOptions.getInflaterFactory());
        samReaderFactory.referenceSource(getCRAMReferenceSource(cramDecoderOptions));
    }

}
