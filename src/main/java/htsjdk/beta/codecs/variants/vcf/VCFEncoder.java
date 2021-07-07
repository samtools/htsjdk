package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;

public abstract class VCFEncoder implements VariantsEncoder {
    final protected Bundle outputBundle;
    final protected VariantsEncoderOptions variantsEncoderOptions;
    final private String displayName;

    public VCFEncoder(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.variantsEncoderOptions = variantsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
    }

   @Override
    final public String getFormat() { return VariantsFormats.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

}
