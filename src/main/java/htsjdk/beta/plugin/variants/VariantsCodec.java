package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;

/**
 *  Defines the type parameters instantiated for variants codecs.
 */
public interface VariantsCodec extends HtsCodec<VariantsDecoderOptions, VariantsEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.VARIANT_CONTEXTS; }

}
