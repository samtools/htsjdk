package htsjdk.beta.plugin.hapref;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecType;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.beta.plugin.HtsEncoderOptions;

//TODO:should this use a more specific HaploidReferenceOptions class, even if its a no-op

/**
 *  Defines the type parameters instantiated for haploid reference codecs.
 */
public interface HaploidReferenceCodec extends HtsCodec<HaploidReferenceFormat, HtsDecoderOptions, HtsEncoderOptions> {

    @Override
    default HtsCodecType getCodecType() { return HtsCodecType.HAPLOID_REFERENCE; }

}
