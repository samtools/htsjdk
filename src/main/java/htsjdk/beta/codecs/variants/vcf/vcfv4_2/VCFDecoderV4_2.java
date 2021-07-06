package htsjdk.beta.codecs.variants.vcf.vcfv4_2;

import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFReader;

import java.io.IOException;

public class VCFDecoderV4_2 extends VCFDecoder {
    private final VCFReader vcfReader;
    private final VCFHeader vcfHeader;

    public VCFDecoderV4_2(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        super(inputBundle, decoderOptions);
        vcfReader = getVCFReader(decoderOptions);
        vcfHeader = vcfReader.getHeader();
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_2.VCF_V42_VERSION;
    }

    @Override
    public VCFHeader getHeader() {
        return vcfHeader;
    }

    @Override
    public CloseableIterator<VariantContext> iterator() {
        return vcfReader.iterator();
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

    //TODO: need to also look at the bundle to find the index input/stream
    private VCFReader getVCFReader(final VariantsDecoderOptions decoderOptions) {
        final BundleResource variantsResource = inputBundle.getOrThrow(BundleResourceType.VARIANT_CONTEXTS);
        if (variantsResource.getIOPath().isPresent()) {
            return new VCFFileReader(variantsResource.getIOPath().get().toPath(),false);
        } else {
            throw new HtsjdkPluginException("VCF reader from stream not implemented");
        }
    }

}
