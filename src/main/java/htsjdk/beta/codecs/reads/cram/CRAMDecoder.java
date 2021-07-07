package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;

/**
 * Base class for CRAM decoders.
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
    final public String getFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    // TODO: If we've been handed a CRAMReferenceSource from the caller, then we don't want to close it
    // when the decoder is closed, but if we create it, then we need to close it.
    //TODO: creation of the source should be separate from the getting of the source, and the result
    // cached, so we don't create multiple reference Sources
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

}
