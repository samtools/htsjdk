package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormat;

import java.io.InputStream;

public abstract class VCFDecoder implements VariantsDecoder {
    protected final Bundle inputBundle;
    protected final VariantsDecoderOptions variantsDecoderOptions;
    private final String displayName;

    public VCFDecoder(final Bundle inputBundle, final VariantsDecoderOptions variantsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.variantsDecoderOptions = variantsDecoderOptions;
        this.displayName = inputBundle.get(BundleResourceType.VARIANTS).get().getDisplayName();
    }

    @Override
    final public VariantsFormat getFormat() { return VariantsFormat.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

}
