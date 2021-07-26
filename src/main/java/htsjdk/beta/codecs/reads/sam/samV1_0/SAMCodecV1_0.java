package htsjdk.beta.codecs.reads.sam.samV1_0;

import htsjdk.beta.codecs.reads.sam.SAMCodec;
import htsjdk.beta.codecs.reads.sam.SAMDecoder;
import htsjdk.beta.codecs.reads.sam.SAMEncoder;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;

/**
 * SAM v1.0 codec.
 */
public class SAMCodecV1_0 extends SAMCodec {
    public static final HtsVersion VERSION_1 = new HtsVersion(1, 0, 0);

    @Override
    public HtsVersion getVersion() { return VERSION_1; }

    @Override
    public SAMDecoder getDecoder(final Bundle inputBundle, final ReadsDecoderOptions decoderOptions) {
        return new SAMDecoderV1_0(inputBundle, decoderOptions);
    }

    @Override
    public SAMEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions encoderOptions) {
        return new SAMEncoderV1_0(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

}
