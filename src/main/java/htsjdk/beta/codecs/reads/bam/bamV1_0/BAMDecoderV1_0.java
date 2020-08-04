package htsjdk.beta.codecs.reads.bam.bamV1_0;

import htsjdk.beta.codecs.reads.bam.BAMDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.interval.HtsInterval;
import htsjdk.beta.plugin.interval.HtsQueryRule;

import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.QueryInterval;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

//TODO: need to guard against multiple iterators

public class BAMDecoderV1_0 extends BAMDecoder {
    private final SamReader samReader;
    private final SAMFileHeader samFileHeader;

    public BAMDecoderV1_0(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(inputBundle, readsDecoderOptions);
        ValidationUtils.nonNull(readsDecoderOptions);
        samReader = getSamReader(readsDecoderOptions);
        samFileHeader = samReader.getFileHeader();
    }

    @Override
    public HtsCodecVersion getVersion() {
        return BAMCodecV1_0.VERSION_1;
    }

    @Override
    public SAMFileHeader getHeader() {
        return samFileHeader;
    }

    // HtsQuery methods

    @Override
    public Iterator<SAMRecord> iterator() {
        return samReader.iterator();
    }

    @Override
    public boolean isQueryable() {
        return samReader.isQueryable();
    }

    @Override
    public boolean hasIndex() {
        return samReader.hasIndex();
    }

    @Override
    public Iterator<SAMRecord> query(final List<HtsInterval> intervals, final HtsQueryRule queryRule) {
        final QueryInterval[] queryIntervals = HtsInterval.toQueryIntervalArray(
                intervals,
                samFileHeader.getSequenceDictionary());
        return samReader.query(queryIntervals, queryRule == HtsQueryRule.CONTAINED);
    }

    @Override
    public Iterator<SAMRecord> queryStart(final String queryName, final long start) {
        return samReader.queryAlignmentStart(queryName, HtsInterval.toIntegerSafe(start));
    }

    // ReadsQuery interface methods

    @Override
    public Iterator<SAMRecord> queryUnmapped() {
        return samReader.queryUnmapped();
    }

    @Override
    public SAMRecord queryMate(final SAMRecord rec) {
        return samReader.queryMate(rec);
    }

    @Override
    public void close() {
        try {
            samReader.close();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception closing input stream %s on", inputBundle), e);
        }
    }

    private SamReader getSamReader(final ReadsDecoderOptions readsDecoderOptions) {

        //TODO: SamReaderFactory doesn't expose getters for all options (currently most are not exposed),
        // so this is currently not fully honoring the SAMFileWriterFactory
        final Optional<BundleResource> readsInput = inputBundle.get(BundleResourceType.READS);
        if (!readsInput.isPresent()) {
            throw new IllegalArgumentException(String.format(
                    "No readable (input) reads resource was provided in bundle %s", inputBundle));
        } else if (!readsInput.get().isInputResource()) {
            throw new IllegalArgumentException(String.format(
                    "The provided reads resource is not an input (readable): %s", readsInput.get()));
        }

        final SamInputResource readsResource = getSamInputResourceFromBundleResource(readsInput.get(), readsDecoderOptions);

        // add the index if there is one
        final Optional<BundleResource> indexInput = inputBundle.get(BundleResourceType.READS_INDEX);
        if (indexInput.isPresent()) {
            final BundleResource indexResource = indexInput.get();
            if (indexResource.getIOPath().isPresent()) {
                readsResource.index(indexResource.getIOPath().get().toPath());
            } else if (indexResource.getSeekableStream().isPresent()) {
                readsResource.index(indexResource.getSeekableStream().get());
            } else if (indexResource.getInputStream().isPresent()) {
                readsResource.index(indexResource.getInputStream().get());
            }
        }
        //TODO: this existing code in SamReaderFactory will automatically resolve a
        // companion index if there is one. We may want to suppress that somehow if
        // the contract for codecs is that the index must always be resolved by the
        // caller; otherwise when we change this code path in the future to no longer
        // resolve the index, backward incompatibilities will be introduced.
        return readsDecoderOptions.getSamReaderFactory().open(readsResource);
    }

    //TODO: move this somewhere so it can be shared by multiple (BAM/CRAM (/VCF?)) decoders
    private SamInputResource getSamInputResourceFromBundleResource(
            final BundleResource bundleResource,
            final ReadsDecoderOptions readsDecoderOptions) {
        if (bundleResource.isRandomAccessResource()) {
            if (bundleResource.getIOPath().isPresent()) {
                //TODO: obtain the cloud channel wrapper from readsDecoderOptions and pass through to SamInputResource
                //Function<SeekableByteChannel, SeekableByteChannel> testPrefetcher =
                //        (SeekableByteChannel sbc) -> {
                //            System.out.println("prefetcher called");
                //            return sbc;
                //        };
                //return SamInputResource.of(bundleResource.getIOPath().get().toPath(), testPrefetcher);
                return SamInputResource.of(bundleResource.getIOPath().get().toPath());
            } else if (bundleResource.getSeekableStream().isPresent()) {
                return SamInputResource.of(bundleResource.getSeekableStream().get());
            }
        }
        return SamInputResource.of(bundleResource.getInputStream().get());
    }

}
