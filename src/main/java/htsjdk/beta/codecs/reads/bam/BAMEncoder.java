package htsjdk.beta.codecs.reads.bam;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsEncoder;

/**
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_BAM} encoders.
 */
public abstract class BAMEncoder implements ReadsEncoder {
    private final Bundle outputBundle;
    private final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    /**
     * Create a BAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle outoput{@link Bundle} to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to use
     */
    public BAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
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
