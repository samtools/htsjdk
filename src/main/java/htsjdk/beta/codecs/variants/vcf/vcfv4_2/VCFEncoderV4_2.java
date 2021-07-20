package htsjdk.beta.codecs.variants.vcf.vcfv4_2;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;

import static htsjdk.variant.variantcontext.writer.Options.INDEX_ON_THE_FLY;

/**
 * VCF V4.2 encoder.
 */
public class VCFEncoderV4_2 extends VCFEncoder {

    /**
     * Create a new VCF V4.2 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV4_2(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_2.VCF_V42_VERSION;
    }

}
