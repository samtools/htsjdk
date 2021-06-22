package htsjdk.beta.codecs.reads.bam;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.utils.ValidationUtils;

/**
 * Base class for BAM decoders.
 */
public abstract class BAMDecoder implements ReadsDecoder {
    protected final Bundle inputBundle;
    protected final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;

    public BAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle,"inputBundle");
        ValidationUtils.nonNull(readsDecoderOptions, "readsDecoderOptions");
        this.inputBundle = inputBundle;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
        this.readsDecoderOptions = readsDecoderOptions;
    }

    @Override
    final public ReadsFormat getFormat() { return ReadsFormat.BAM; }

    @Override
    final public String getDisplayName() { return displayName; }

}
