package htsjdk.beta.codecs.variants.vcf.vcfv4_3;

import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderVersion;


/**
 * VCF V4.3 encoder.
 */
public class VCFEncoderV4_3 extends VCFEncoder {
    protected final static Log LOG = Log.getInstance(VCFEncoderV4_3.class);

    /**
     * Create a new VCF V4.3 encoder.
     *
     * @param outputBundle the output {@link Bundle} to encoder
     * @param variantsEncoderOptions the {@link VariantsEncoderOptions} to use
     */
    public VCFEncoderV4_3(final Bundle outputBundle, final VariantsEncoderOptions variantsEncoderOptions) {
        super(outputBundle,variantsEncoderOptions);
    }

    @Override
    public HtsVersion getVersion() {
        return VCFCodecV4_3.VCF_V43_VERSION;
    }

    @Override
    public void setHeader(final VCFHeader vcfHeader) {
        ValidationUtils.nonNull(vcfHeader, "vcfHeader");

        if (!vcfHeader.getVCFHeaderVersion().equals(VCFHeaderVersion.VCF4_3)) {
            LOG.warn(String.format("Attempting to set a version %s VCF header on a version %s VCF encoder",
                    vcfHeader.getVCFHeaderVersion(),
                    VCFCodecV4_3.VCF_V43_VERSION));
        }
        super.setHeader(vcfHeader);
    }

}
