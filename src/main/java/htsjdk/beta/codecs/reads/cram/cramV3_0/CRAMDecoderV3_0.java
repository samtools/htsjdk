package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * CRAM v3.0 decoder.
 */
public class CRAMDecoderV3_0 extends CRAMDecoder {
    private final CRAMFileReader cramReader;
    private final SAMFileHeader samFileHeader;
    private boolean iteratorExists = false;

    public CRAMDecoderV3_0(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
        cramReader = getCRAMReader(readsDecoderOptions);
        samFileHeader = cramReader.getFileHeader();
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    @Override
    public CloseableIterator<SAMRecord> iterator() { return getIteratorMonitor(() -> cramReader.getIterator()); }

    @Override
    public boolean isQueryable() {
        return cramReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return cramReader.hasIndex();
    }

    @Override
    public CloseableIterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        final QueryInterval[] queryIntervals = HtsIntervalUtils.toQueryIntervalArray(
                intervals,
                samFileHeader.getSequenceDictionary());
        return getIteratorMonitor(() -> cramReader.query(queryIntervals, queryRule == HtsQueryRule.CONTAINED));
    }

    @Override
    public CloseableIterator<SAMRecord> queryStart(final String queryName, final long start) {
        return getIteratorMonitor(() -> cramReader.queryAlignmentStart(queryName, HtsIntervalUtils.toIntegerSafe(start)));
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        return getIteratorMonitor(() -> cramReader.queryUnmapped());
    }

    //Note that this method  is a copied and slightly modified version of the shared implementation in
    // SamReader. It delegates to other query methods on this decoder (queryUnmapped and queryStart),
    // which will throw if an existing iterator is already opened
    @Override
    public SAMRecord queryMate(SAMRecord rec) {
        if (!rec.getReadPairedFlag()) {
            throw new IllegalArgumentException("queryMate called for unpaired read.");
        }
        if (rec.getFirstOfPairFlag() == rec.getSecondOfPairFlag()) {
            throw new IllegalArgumentException("SAMRecord must be either first and second of pair, but not both.");
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
                        throw new SAMFormatException("Paired and unpaired reads with same name: " + rec.getReadName());
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
                        throw new SAMFormatException("Multiple SAMRecord with read name " + rec.getReadName() +
                                " for " + (firstOfPair ? "second" : "first") + " end.");
                    }
                    mateRec = next;
                }
            }
            return mateRec;
        }
    }

    @Override
    public void close() {
        cramReader.close();
    }

    private CRAMFileReader getCRAMReader(final ReadsDecoderOptions readsDecoderOptions) {
        final CRAMFileReader cramFileReader;

        final BundleResource readsInput = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS);
        final Optional<BundleResource> indexInput = inputBundle.get(BundleResourceType.READS_INDEX);
        if (indexInput.isPresent()) {
            final BundleResource indexResource = indexInput.get();
            if (!indexResource.hasSeekableStream()) {
                throw new IllegalArgumentException(String.format(
                        "A seekable stream is required for CRAM index inputs but was not provided: %s",
                        indexResource
                ));
            }
            final SeekableStream seekableIndexStream = indexResource.getSeekableStream().get();
            try {
                cramFileReader = new CRAMFileReader(
                        readsInput.getSeekableStream().get(),
                        seekableIndexStream,
                        getCRAMReferenceSource(),
                        readsDecoderOptions.getValidationStringency());
            } catch (IOException e) {
                throw new HtsjdkIOException(e);
            }
        } else {
            try {
                cramFileReader = new CRAMFileReader(
                        readsInput.getInputStream().get(),
                        (SeekableStream) null,
                        getCRAMReferenceSource(),
                        readsDecoderOptions.getValidationStringency());
            } catch (IOException e) {
                throw new HtsjdkIOException(e);
            }
        }

        return cramFileReader;
    }

    // create an iterator wrapper that can notify this decoder when its closed, so the decoder can ensure
    // that only one iterator is ever outstanding at a time
    private CloseableIterator<SAMRecord> getIteratorMonitor(final Supplier<CloseableIterator> newIterator) {
        if (iteratorExists == true) {
            throw new IllegalStateException("This decoder already has an existing iterator open");
        } else {
            iteratorExists = true;
            return new CloseableIteratorMonitor(newIterator.get());
        }
    }

    private void toggleIteratorExists() {
        if (iteratorExists == false) {
            throw new IllegalStateException("Attempt to close a non-existent monitored iterator");
        }
        // reset the iterator monitor
        iteratorExists = false;
    }

    // but this needs to delegate back to the outer class so it has a reference to it anyway...
    private class CloseableIteratorMonitor<T> implements CloseableIterator<T> {
        final CloseableIterator<T> wrappedIterator;

        public CloseableIteratorMonitor(final CloseableIterator<T> wrappedIterator) {
            ValidationUtils.nonNull(wrappedIterator, "wrappedIterator");
            this.wrappedIterator = wrappedIterator;
        }

        @Override
        public void close() {
            // notify the outer class iterator monitor that the iterator is closed
            CRAMDecoderV3_0.this.toggleIteratorExists();
            wrappedIterator.close();
        }

        @Override
        public boolean hasNext() { return wrappedIterator.hasNext(); }

        @Override
        public T next() {
            return wrappedIterator.next();
        }
    }

}
