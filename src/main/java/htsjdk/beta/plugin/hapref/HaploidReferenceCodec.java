package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.beta.plugin.HtsEncoderOptions;

/**
 *  Base class for all haploid reference codecs.
 */
public interface HaploidReferenceCodec extends HtsCodec<
        HaploidReferenceFormat,
        HaploidReferenceDecoderOptions,
        HaploidReferenceEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.HAPLOID_REFERENCE; }

}
