package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

import java.util.List;

/**
 * Interface for transforming attribute values embedded in VCF. VCF version 4.3 supports percent-encoding
 * of characters that have special meaning in VCF. Pre-v43, such encoding are not supported and no transformation
 * needs to be done.
 */
public interface VCFTextTransformer {
    /**
     * Transform a single string.
     *
     * @param rawPart the raw string to be decoded
     * @return the decoded string
     * @throws TribbleException if the the encoding is uninterpretable
     */
    String decodeText(final String rawPart);

    /**
     * Transform a list of strings.
     *
     * @param rawParts  a list of raw strings
     * @return a list of decoded strings
     * @throws TribbleException if the the encoding is uninterpretable
     */
    List<String> decodeText(final List<String> rawParts);

    /**
     * Encode a single string.
     *
     * @param rawPart the raw string to be encoded
     * @return the encoded string
     * @throws TribbleException if the the encoding is unencodable
     */
    String encodeText(final String rawPart);
}
