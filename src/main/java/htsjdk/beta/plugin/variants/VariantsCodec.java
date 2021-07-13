package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;

/**
 *  Base class for all {@link HtsContentType#VARIANT_CONTEXTS} codecs.
 */
public interface VariantsCodec extends HtsCodec<VariantsDecoderOptions, VariantsEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.VARIANT_CONTEXTS; }

}
