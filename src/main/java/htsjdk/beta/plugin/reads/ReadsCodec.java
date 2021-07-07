package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;

/**
 * Base class for all {@link HtsContentType#ALIGNED_READS} codecs.
 */
public interface ReadsCodec extends HtsCodec<ReadsDecoderOptions, ReadsEncoderOptions> {

    @Override
    default HtsContentType getContentType() { return HtsContentType.ALIGNED_READS; }

}
