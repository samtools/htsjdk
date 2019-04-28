package htsjdk.variant.vcf;

import java.util.List;

/**
 * A no-op implementation of VCFTextTransformer for pre-v43 VCFs, when such encodings are not supported and
 * no transformation need be done.
 */
public class VCFPassThruTextTransformer extends VCFTextTransformer {

    @Override
    public String transformEncodedText(final String rawPart) {
        return rawPart;
    }

    @Override
    public List<String> transformEncodedText(final List<String> rawParts) {
        return rawParts;
    }
}
