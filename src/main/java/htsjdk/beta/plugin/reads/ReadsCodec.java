package htsjdk.beta.plugin.reads;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecType;

/**
 * Base class for all reads codecs.
 */
public interface ReadsCodec extends HtsCodec<ReadsFormat, ReadsDecoderOptions, ReadsEncoderOptions> {

    @Override
    default HtsCodecType getCodecType() { return HtsCodecType.ALIGNED_READS; }

}
