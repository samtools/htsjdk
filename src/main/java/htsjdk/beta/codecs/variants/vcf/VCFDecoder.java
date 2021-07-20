package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.utils.PrivateAPI;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.AbstractVCFCodec;
import htsjdk.variant.vcf.VCFHeader;

import java.io.IOException;

/**
 * @PrivateAPI
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} decoders.
 */
@PrivateAPI
public abstract class VCFDecoder implements VariantsDecoder {
    private final Bundle inputBundle;
    private final VariantsDecoderOptions variantsDecoderOptions;
    private final AbstractVCFCodec vcfCodec;
    private final String displayName;
    private final FeatureReader<VariantContext> vcfReader;
    private final VCFHeader vcfHeader;


    /**
     * @PrivateAPI
     *
     * Create a new VCF decoder.
     *
     * @param inputBundle the input {@link Bundle} to decode
     * @param vcfCodec the {@link AbstractVCFCodec} to use for this decoder
     * @param variantsDecoderOptions the {@link VariantsDecoderOptions} for this decoder
     */
    @PrivateAPI
    @SuppressWarnings("unchecked")
    public VCFDecoder(
            final Bundle inputBundle,
            final AbstractVCFCodec vcfCodec,
            final VariantsDecoderOptions variantsDecoderOptions) {
        this.inputBundle = inputBundle;
        this.vcfCodec = vcfCodec;
        this.variantsDecoderOptions = variantsDecoderOptions;
        this.displayName = inputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS).getDisplayName();
        vcfReader = getVCFReader(vcfCodec, variantsDecoderOptions);
        vcfHeader = (VCFHeader) vcfReader.getHeader();
    }

    @Override
    final public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public VCFHeader getHeader() {
        return vcfHeader;
    }

    @Override
    public CloseableIterator<VariantContext> iterator() {
        try {
            return vcfReader.iterator();
        } catch (IOException e) {
            throw new HtsjdkIOException("Exception creating variant context iterator", e);
        }
    }

    @Override
    public boolean isQueryable() {
        throw new HtsjdkPluginException("Not implemented");
    }

    @Override
    public boolean hasIndex() {
        throw new HtsjdkPluginException("Not implemented");
    }

    @Override
    public void close() {
        try {
            vcfReader.close();
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Exception closing input stream %s for", getDisplayName()), e);
        }
    }

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

    //TODO: need to also look at the bundle to find the index input/stream
    private FeatureReader<VariantContext> getVCFReader(
            final AbstractVCFCodec vcfCodec,
            final VariantsDecoderOptions decoderOptions) {
        final BundleResource variantsResource = getInputBundle().getOrThrow(BundleResourceType.VARIANT_CONTEXTS);
        if (variantsResource.getIOPath().isPresent()) {
            final FeatureReader<VariantContext> reader = AbstractFeatureReader.getFeatureReader(
                    variantsResource.getIOPath().get().toPath().toString(),
                    vcfCodec,
                    false);
            return reader;
        } else {
            throw new HtsjdkPluginException("VCF reader from stream not implemented");
        }
    }

}
