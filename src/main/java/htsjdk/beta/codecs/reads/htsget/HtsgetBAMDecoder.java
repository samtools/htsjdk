package htsjdk.beta.codecs.reads.htsget;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;

/**
 * Base class for concrete implementations of reads decoders that handle
 * {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_HTSGET_BAM} decoding.
 */
public abstract class HtsgetBAMDecoder implements ReadsDecoder {
    protected final Bundle inputBundle;
    protected final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;

    public HtsgetBAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.readsDecoderOptions = readsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.BAM; }

    @Override
    public HtsVersion getVersion() {
        return HtsgetBAMCodec.HTSGET_VERSION;
    }

    @Override
    final public String getDisplayName() { return displayName; }

}
