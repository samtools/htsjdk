package htsjdk.variant.vcf;

import java.util.List;

/**
 * A no-op implementation of VCFTextTransformer for pre-v43 VCFs, when such encodings are not supported and
 * no transformation need be done.
 */
public class VCFPassThruTextTransformer implements VCFTextTransformer {

    /**
     * No-op decoder for a single string
     * @param rawPart the raw string to be decoded
     * @return the raw string with no transformation done
     */
    @Override
    public String decodeText(final String rawPart) {
        return rawPart;
    }

    /**
     * No-op decoder for lists of strings
     * @param rawParts  a list of raw strings
     * @return the list of raw strings with no transformations done
     */
    @Override
    public List<String> decodeText(final List<String> rawParts) {
        return rawParts;
    }

    /**
     * No-op encoder for a single string
     * @param rawPart the raw string to be decoded
     * @return the raw string with no transformation done
     */
    @Override
    public String encodeText(final String rawPart) {
        return rawPart;
    }
}
