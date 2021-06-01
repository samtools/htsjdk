package htsjdk.beta.codecs.reads.htsget.htsgetBAMV1_2;

import htsjdk.beta.codecs.reads.htsget.HtsgetBAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.DefaultSAMRecordFactory;
import htsjdk.samtools.HtsgetBAMFileReader;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class HtsgetBAMDecoderV1_2 extends HtsgetBAMDecoder {

    final HtsgetBAMFileReader htsgetReader;

    public HtsgetBAMDecoderV1_2(final Bundle inputBundle, final ReadsDecoderOptions decoderOptions) {
        super(inputBundle, decoderOptions);
        final BundleResource readsResource = inputBundle.getOrThrow(BundleResourceType.READS);
        if (!readsResource.getIOPath().isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "Htsget required a non-stream input resource. The bundle resource %s doesn't contain the required URI",
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
            throw new RuntimeIOException(
                    String.format("Failure opening Htsget reader on %s", readsResource.getIOPath().get().toString()), e);
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
    public Iterator<SAMRecord> iterator() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean isQueryable() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean hasIndex() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public Iterator<SAMRecord> query(final String queryString) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> query(final String queryName, final long start, final long end, final HtsQueryRule queryRule) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryOverlapping(final String queryName, final long start, final long end) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryContained(final String queryName, final long start, final long end) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> query(final HtsInterval interval, final HtsQueryRule queryRule) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryOverlapping(final HtsInterval interval) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryContained(final HtsInterval interval) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        return htsgetReader.query(
                HtsInterval.toLocatableList(intervals, getHeader().getSequenceDictionary()),
                queryRule == HtsQueryRule.CONTAINED == true);
    }

    @Override
    public Iterator<SAMRecord> queryOverlapping(final List<HtsInterval> intervals) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryContained(final List<HtsInterval> intervals) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryStart(final String queryName, final long start) {
        return null;
    }

    @Override
    public Iterator<SAMRecord> queryUnmapped() {
        return null;
    }

    @Override
    public SAMRecord queryMate(final SAMRecord rec) {
        throw new IllegalArgumentException("queryMate not implemented for htsget bam reader");
    }
}
