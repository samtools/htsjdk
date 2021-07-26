package htsjdk.beta.codecs.reads.sam.samV1_0;

import htsjdk.beta.codecs.reads.ReadsCodecUtils;
import htsjdk.beta.codecs.reads.sam.SAMDecoder;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.util.CloseableIterator;

import java.io.IOException;
import java.util.Optional;

/**
 * SAM v1.0 decoder.
 */
public class SAMDecoderV1_0 extends SAMDecoder {
    private final SamReader samReader;
    private final SAMFileHeader samFileHeader;

    /**
     * Create a V1.0 SAM decoder for the given input bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS}, and the resource must be an
     * appropriate format and version for this encoder (to find an encoder for a bundle, see
     * {@link htsjdk.beta.plugin.registry.ReadsResolver}.
     *
     * @param inputBundle bundle to decoder
     * @param readsDecoderOptions options to use
     */
    public SAMDecoderV1_0(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        super(inputBundle, readsDecoderOptions);
        samReader = ReadsCodecUtils.getSamReader(inputBundle, readsDecoderOptions, SamReaderFactory.makeDefault());
        samFileHeader = samReader.getFileHeader();
    }

    @Override
    public HtsVersion getVersion() {
        return SAMCodecV1_0.VERSION_1;
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
    public boolean isQueryable() { return false; }

    @Override
    public boolean hasIndex() {
        return false;
    }

    // ReadsQuery interface methods

    @Override
    public void close() {
        try {
            samReader.close();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception closing reader for %s", getInputBundle()), e);
        }
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        throw new IllegalArgumentException(String.format("This decoder is not queryable: %s", getDisplayName()));
    }

    @Override
    public Optional<SAMRecord> queryMate(SAMRecord rec) {
        throw new IllegalArgumentException(String.format("This decoder is not queryable: %s", getDisplayName()));
    }
}
