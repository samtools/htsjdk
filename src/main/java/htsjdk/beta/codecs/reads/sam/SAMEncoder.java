package htsjdk.beta.codecs.reads.sam;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;

/**
 * @PrivateAPI
 *
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_SAM} encoders.
 */
public abstract class SAMEncoder implements ReadsEncoder {
    private final Bundle outputBundle;
    private final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    /**
     * @PrivateAPI
     *
     * Create a SAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * NOTE: callers that provide an output stream resource should provide a buffered output stream
     * if buffering is desired, since the encoder does not provide an additional buffering layer.
     *
     * @param outputBundle output {@link Bundle} to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to use
     */
    public SAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.SAM; }

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
