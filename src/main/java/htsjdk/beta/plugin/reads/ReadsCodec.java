package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;

/**
 * Base class for all reads codecs. Defines th type parameters instantiated for reads codecs.
 */
public interface ReadsCodec extends HtsCodec<ReadsFormat, ReadsDecoderOptions, ReadsEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.ALIGNED_READS; }

}
