package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/**
 * CRAM v3.0 decoder.
 */
public class CRAMDecoderV3_0 extends CRAMDecoder {
    private final CRAMFileReader cramReader;
    private final SAMFileHeader samFileHeader;

    public CRAMDecoderV3_0(final Bundle bundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(bundle, readsDecoderOptions);
        cramReader = getCRAMReader(readsDecoderOptions);
        samFileHeader = cramReader.getFileHeader();
    }

    @Override
    public HtsCodecVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    @Override
    public Iterator<SAMRecord> iterator() {
        return cramReader.getIterator();
    }

    @Override
    public boolean isQueryable() {
        return cramReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return cramReader.hasIndex();
    }

    @Override
    public Iterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        final QueryInterval[] queryIntervals = HtsInterval.toQueryIntervalArray(
                intervals,
                samFileHeader.getSequenceDictionary());
        return cramReader.query(queryIntervals, queryRule == HtsQueryRule.CONTAINED);
    }

    @Override
    public Iterator<SAMRecord> queryStart(final String queryName, final long start) {
        return cramReader.queryAlignmentStart(queryName, HtsInterval.toIntegerSafe(start));
    }

    @Override
    public Iterator<SAMRecord> queryUnmapped() {
        return cramReader.queryUnmapped();
    }

    //TODO: this is copied and slightly modified version of the shared implementation in SamReader
    @Override
    public SAMRecord queryMate(SAMRecord rec) {
        if (!rec.getReadPairedFlag()) {
            throw new IllegalArgumentException("queryMate called for unpaired read.");
        }
        if (rec.getFirstOfPairFlag() == rec.getSecondOfPairFlag()) {
            throw new IllegalArgumentException("SAMRecord must be either first and second of pair, but not both.");
        }
        final boolean firstOfPair = rec.getFirstOfPairFlag();
        final Iterator<SAMRecord> it;
        if (rec.getMateReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
            it = queryUnmapped();
        } else {
            it = queryStart(rec.getMateReferenceName(), rec.getMateAlignmentStart());
        }
        try {
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
        } finally {
            //TODO: does the old implementation of this (shared in SamReader) close the underlying stream when
            // the CloseableIterator is closed in the finally block ?
            //it.close();
        }
    }

    @Override
    public void close() {
        cramReader.close();
    }

    private CRAMFileReader getCRAMReader(final ReadsDecoderOptions readsDecoderOptions) {
        final CRAMFileReader cramFileReader;

        final BundleResource readsInput = inputBundle.getOrThrow(BundleResourceType.READS);
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
                throw new RuntimeIOException(e);
            }
        } else {
            try {
                cramFileReader = new CRAMFileReader(
                        readsInput.getInputStream().get(),
                        (SeekableStream) null,
                        getCRAMReferenceSource(),
                        readsDecoderOptions.getValidationStringency());
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        return cramFileReader;
    }

}
