package htsjdk.beta.codecs.reads.bam.bamV1_0;

import htsjdk.beta.codecs.reads.ReadsCodecUtils;
import htsjdk.beta.codecs.reads.bam.BAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;

import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloseableIterator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public class BAMDecoderV1_0 extends BAMDecoder {
    private final SamReader samReader;
    private final SAMFileHeader samFileHeader;

    public BAMDecoderV1_0(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(inputBundle, readsDecoderOptions);
        samReader = getSamReader(inputBundle, readsDecoderOptions);
        samFileHeader = samReader.getFileHeader();
    }

    @Override
    public HtsVersion getVersion() {
        return BAMCodecV1_0.VERSION_1;
    }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    // HtsQuery methods

    @Override
    public CloseableIterator<SAMRecord> iterator() {
        return samReader.iterator();
    }

    @Override
    public boolean isQueryable() {
        return indexProvidedInInputBundle() && samReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return indexProvidedInInputBundle() && samReader.hasIndex();
    }

    @Override
    public CloseableIterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        assertIndexProvided();
        final QueryInterval[] queryIntervals = HtsIntervalUtils.toQueryIntervalArray(
                intervals,
                samFileHeader.getSequenceDictionary());
        return samReader.query(queryIntervals, queryRule == HtsQueryRule.CONTAINED);
    }

    @Override
    public CloseableIterator<SAMRecord> queryStart(final String queryName, final long start) {
        assertIndexProvided();
        return samReader.queryAlignmentStart(queryName, HtsIntervalUtils.toIntegerSafe(start));
    }

    // ReadsQuery interface methods

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        assertIndexProvided();
        return samReader.queryUnmapped();
    }

    @Override
    public Optional<SAMRecord> queryMate(final SAMRecord rec) {
        assertIndexProvided();
        return Optional.ofNullable(samReader.queryMate(rec));
    }

    @Override
    public void close() {
        try {
            samReader.close();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception closing input stream %s on", inputBundle), e);
        }
    }

    protected static SamReader getSamReader(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        // note that some reads decoder options, such as cloud wrapper values, need to be propagate to the
        // samInputResource
        final SamInputResource samInputResource =
                ReadsCodecUtils.bundleToSamInputResource(inputBundle, readsDecoderOptions);

        final SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(samReaderFactory, readsDecoderOptions);
        ReadsCodecUtils.bamDecoderOptionsToSamReaderFactory(samReaderFactory, readsDecoderOptions.getBAMDecoderOptions());

        return samReaderFactory.open(samInputResource);
    }

    // the stated contract for decoders is that the index must be included in the bundle in order to use
    // index queries, but this uses BAMFileReader which *always* tries to resolve the index, which would
    // violate that, so enforce the contract manually so that someday when we use a different implementation,
    // no backward compatibility issue will be introduced
    private void assertIndexProvided() {
        if (!indexProvidedInInputBundle()) {
            throw new IllegalArgumentException(String.format(
                    "An index resource must be provided in the resource bundle to make index queries: %s",
                    inputBundle
            ));
        }
    }

    private boolean indexProvidedInInputBundle() {
        return inputBundle.get(BundleResourceType.READS_INDEX).isPresent();
    }

}
