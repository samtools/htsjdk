package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.utils.PrivateAPI;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFHeader;


/**
 * @PrivateAPI
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} encoders.
 */
@PrivateAPI
public abstract class VCFEncoder implements VariantsEncoder {
    private final Bundle outputBundle;
    private final VariantsEncoderOptions variantsEncoderOptions;
    private final String displayName;
    private VariantContextWriter vcfWriter;

    /**
     * @PrivateAPI
     *
     * Create a new VCF encoder.
     *
     * @param outputBundle the output {@link Bundle} to encode
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    @PrivateAPI
    public VCFEncoder(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.variantsEncoderOptions = variantsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
    }

   @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public void setHeader(final VCFHeader vcfHeader) {
        vcfWriter = getVCFWriter(getVariantsEncoderOptions(), vcfHeader);
        vcfWriter.writeHeader(vcfHeader);
    }

    @Override
    public void write(final VariantContext variantContext) {
        vcfWriter.add(variantContext);
    }

    @Override
    public void close() {
        if (vcfWriter != null) {
            vcfWriter.close();
        }
    }

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

    private VariantContextWriter getVCFWriter(final VariantsEncoderOptions variantsEncoderOptions, final VCFHeader vcfHeader) {
        final BundleResource variantsResource = getOutputBundle().getOrThrow(BundleResourceType.VARIANT_CONTEXTS);
        if (variantsResource.getIOPath().isPresent()) {
            final VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
            return builder
                    .setOutputPath(variantsResource.getIOPath().get().toPath())
                    .unsetOption(Options.INDEX_ON_THE_FLY)
                    .build();
        } else {
            throw new HtsjdkPluginException("VCF writer to stream not yet implemented");
        }
    }

}
