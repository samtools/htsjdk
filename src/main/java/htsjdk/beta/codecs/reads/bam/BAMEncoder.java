package htsjdk.beta.codecs.reads.bam;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.utils.ValidationUtils;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#FMT_READS_BAM} encoders.
 */
public abstract class BAMEncoder implements ReadsEncoder {
    private final Bundle outputBundle;
    private final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    /**
     * InternalAPI
     *
     * Create a BAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#CT_ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * NOTE: callers that provide an output stream resource should provide a buffered output stream
     * if buffering is desired, since the encoder does not provide an additional buffering layer.
     *
     * @param outputBundle output {@link Bundle} to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to use
     */
    public BAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle,"outputBundle");
        ValidationUtils.nonNull(readsEncoderOptions, "readsEncoderOptions");

        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.CT_ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.BAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    /**
     * Get the output {@link Bundle} for this encoder.
     *
     * @return the output {@link Bundle} for this encoder
     */
    public Bundle getOutputBundle() {
        return outputBundle;
    }

    /**
     * Get the {@link ReadsEncoderOptions} for this encoder.
     *
     * @return the {@link ReadsEncoderOptions} for this encoder.
     */
    public ReadsEncoderOptions getReadsEncoderOptions() {
        return readsEncoderOptions;
    }

}
