package htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2;

import htsjdk.beta.codecs.reads.htsget.HtsgetBAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsIntervalUtils;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.samtools.DefaultSAMRecordFactory;
import htsjdk.samtools.HtsgetBAMFileReader;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.CloseableIterator;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Version 1.2 of {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_HTSGET_BAM} decoder.
 */
public class HtsgetBAMDecoderV1_2 extends HtsgetBAMDecoder {

    final HtsgetBAMFileReader htsgetReader;

    public HtsgetBAMDecoderV1_2(final Bundle inputBundle, final ReadsDecoderOptions decoderOptions) {
        super(inputBundle, decoderOptions);
        final BundleResource readsResource = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS);
        if (!readsResource.getIOPath().isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "Htsget requires an IOPath input resource. The bundle resource %s doesn't contain the required IOPath.",
                    readsResource.getDisplayName()));
        }
        try {
            htsgetReader = new HtsgetBAMFileReader(
                    readsResource.getIOPath().get().getURI(),
                    true,
                    ValidationStringency.DEFAULT_STRINGENCY,
                    DefaultSAMRecordFactory.getInstance(),
                    false);
        } catch (IOException e) {
            throw new HtsjdkIOException(
                    String.format("Failure opening Htsget reader on %s", readsResource.getIOPath().get()), e);
        }
    }

    @Override
    public SAMFileHeader getHeader() {
        return htsgetReader.getFileHeader();
    }

    @Override
    public void close() {
        if (htsgetReader != null) {
            htsgetReader.close();
        }
    }

    @Override
    public CloseableIterator<SAMRecord> iterator() {
        return htsgetReader.getIterator();
    }

    @Override
    public boolean isQueryable() {
        return htsgetReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return htsgetReader.hasIndex();
    }

    @Override
    public CloseableIterator<SAMRecord> query(final String queryString) {
        throw new HtsjdkPluginException("Not implemented");
    }

    @Override
    public CloseableIterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        return htsgetReader.query(
                HtsIntervalUtils.toLocatableList(intervals),
                (queryRule == HtsQueryRule.CONTAINED) == true);
    }

    @Override
    public CloseableIterator<SAMRecord> queryStart(final String queryName, final long start) {
        return htsgetReader.queryAlignmentStart(queryName, HtsIntervalUtils.toIntegerSafe(start));
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        return htsgetReader.queryUnmapped();
    }

    @Override
    public Optional<SAMRecord> queryMate(final SAMRecord rec) {
        //reader doesn't support this
        throw new HtsjdkPluginException("queryMate not implemented for htsget bam reader");
    }
}
