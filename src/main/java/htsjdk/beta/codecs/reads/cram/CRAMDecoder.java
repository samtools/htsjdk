package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.samtools.CRAMFileReader;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.utils.PrivateAPI;

import java.io.IOException;
import java.util.Optional;

/**
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_CRAM} decoders.
 */
public abstract class CRAMDecoder implements ReadsDecoder {
    protected final Bundle inputBundle;
    protected final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;

    public CRAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.readsDecoderOptions = readsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    // TODO: If we've been handed a CRAMReferenceSource from the caller, then we don't want to close it
    // when the decoder is closed, but if we create it, then we need to close it.
    //TODO: creation of the source should be separate from the getting of the source, and the result
    // cached, so we don't create multiple reference Sources
    @PrivateAPI
    protected CRAMReferenceSource getCRAMReferenceSource() {
        final CRAMDecoderOptions cramDecoderOptions = readsDecoderOptions.getCRAMDecoderOptions();
        if (cramDecoderOptions.getReferenceSource().isPresent()) {
            return cramDecoderOptions.getReferenceSource().get();
        } else if (cramDecoderOptions.getReferencePath().isPresent()) {
            return CRAMCodec.getCRAMReferenceSource(cramDecoderOptions.getReferencePath().get());
        }
        // if none is specified, get the default "lazy" reference source that throws when queried, to allow
        // operations that don't require a reference
        return ReferenceSource.getDefaultCRAMReferenceSource();
    }

    @PrivateAPI
    protected CRAMFileReader getCRAMReader(final ReadsDecoderOptions readsDecoderOptions) {
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

}
