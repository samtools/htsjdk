package htsjdk.beta.codecs.reads.sam;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.utils.InternalAPI;
import htsjdk.utils.ValidationUtils;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#READS_SAM} decoders.
 */
@InternalAPI
public abstract class SAMDecoder implements ReadsDecoder {
    private final Bundle inputBundle;
    private final ReadsDecoderOptions readsDecoderOptions;
    private final String displayName;

    /**
     * Create a SAM decoder for the given input bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS}, and the resource must be an
     * appropriate format and version for this encoder (to find an encoder for a bundle, see
     * {@link htsjdk.beta.plugin.registry.ReadsResolver}.
     *
     * @param inputBundle input {@link Bundle} to decode
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use
     */
    @InternalAPI
    public SAMDecoder(final Bundle inputBundle, final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle,"inputBundle");
        ValidationUtils.nonNull(readsDecoderOptions, "readsDecoderOptions");
        this.inputBundle = inputBundle;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
        this.readsDecoderOptions = readsDecoderOptions;
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.SAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    /**
     * Get the input {@link Bundle} for this decoder.
     *
     * @return the input {@link Bundle} for this decoder
     */
    public Bundle getInputBundle() {
        return inputBundle;
    }

    /**
     * Get the {@link ReadsDecoderOptions} for this decoder.
     *
     * @return the {@link ReadsDecoderOptions} for this decoder.
     */
    public ReadsDecoderOptions getReadsDecoderOptions() {
        return readsDecoderOptions;
    }
}
