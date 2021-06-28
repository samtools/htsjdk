package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecType;

/**
 *  Defines the type parameters instantiated for variants codecs.
 */
public interface VariantsCodec extends HtsCodec<VariantsFormat, VariantsDecoderOptions, VariantsEncoderOptions> {

    @Override
    default HtsCodecType getCodecType() { return HtsCodecType.VARIANT_CONTEXTS; }

}
