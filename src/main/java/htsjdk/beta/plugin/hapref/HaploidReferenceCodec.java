package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;

/**
 *  Base class for all {@link HtsContentType#HAPLOID_REFERENCE} codecs.
 */
public interface HaploidReferenceCodec extends HtsCodec<
        HaploidReferenceDecoderOptions,
        HaploidReferenceEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.HAPLOID_REFERENCE; }

}
