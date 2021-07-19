package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;

/**
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} encoders.
 */
public abstract class VCFEncoder implements VariantsEncoder {
    private final Bundle outputBundle;
    private final VariantsEncoderOptions variantsEncoderOptions;
    private final String displayName;

    public VCFEncoder(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.variantsEncoderOptions = variantsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
    }

   @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

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
     * Get the {@link VariantsEncoderOptions} for this encoder.
     *
     * @return the {@link VariantsEncoderOptions} for this encoder.
     */
    public VariantsEncoderOptions getVariantsEncoderOptions() {
        return variantsEncoderOptions;
    }

}
