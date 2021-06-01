package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.beta.plugin.reads.ReadsDecoder;

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
        this.displayName = inputBundle.getOrThrow(BundleResourceType.READS).getDisplayName();
    }

    @Override
    final public ReadsFormat getFormat() { return ReadsFormat.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

}
