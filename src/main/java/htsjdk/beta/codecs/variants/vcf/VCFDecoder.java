package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;

/**
 *  Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} decoders.
 */
public abstract class VCFDecoder implements VariantsDecoder {
    private final Bundle inputBundle;
    private final VariantsDecoderOptions variantsDecoderOptions;
    private final String displayName;

    public VCFDecoder(final Bundle inputBundle, final VariantsDecoderOptions variantsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.variantsDecoderOptions = variantsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

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
     * Get the {@link VariantsDecoderOptions} for this decoder.
     *
     * @return the {@link VariantsDecoderOptions} for this decoder.
     */
    public VariantsDecoderOptions getReadsDecoderOptions() {
        return variantsDecoderOptions;
    }

}
