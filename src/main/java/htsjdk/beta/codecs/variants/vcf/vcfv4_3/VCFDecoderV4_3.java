package htsjdk.beta.codecs.variants.vcf.vcfv4_3;

import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;

/**
 * VCF V4.3 decoder.
 */
public class VCFDecoderV4_3 extends VCFDecoder {

    /**
     * Create a new VCF V4.3 decoder.
     *
     * @param inputBundle the input {@link Bundle} to decode
     * @param variantsDecoderOptions the {@link VariantsDecoderOptions} for this decoder
     */
    public VCFDecoderV4_3(final Bundle inputBundle, final VariantsDecoderOptions variantsDecoderOptions) {
        super(inputBundle, new htsjdk.variant.vcf.VCFCodec(), variantsDecoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_3.VCF_V43_VERSION;
    }

    @Override
    public VCFHeader getHeader() {
        final VCFHeader vcfHeader = super.getHeader();

        // This decoder uses the multi-version legacy codec htsjdk.variant.vcf.VCFCodec as its underlying
        // implementation; which in turn uses {@link VCFStandardHeaderLines#repairStandardHeaderLines(VCFHeader)
        // to in-place upgrade any pre-v4.2 input to v4.2. Most of the other codecs in codecs.variants.vcf
        // that wrap the same legacy codec have to tolerate the in-place upgrades and accept the "upgraded"
        // inputs that do not match the nominal version supported by the codec - i.e, VCFDecoderV4_0 will
        // see vcf4.2 inputs because the underlying implementation upgrades them to 4.2 in-place), but those
        // upgradesare not be done by {@link VCFStandardHeaderLines#repairStandardHeaderLines(VCFHeader) for
        // VCF4.3+, so this codec can include a sanity check and require that it only ever sees v4.3 inputs.
        if (!vcfHeader.getVCFHeaderVersion().equals(VCFHeaderVersion.VCF4_3)) {
            throw new RuntimeException(
                    String.format(
                            "The VCF %s version decoder cannot be used to write a version %s VCF header",
                            VCFCodecV4_3.VCF_V43_VERSION,
                            vcfHeader.getVCFHeaderVersion().toString()));
        }
        return vcfHeader;
    }
}
