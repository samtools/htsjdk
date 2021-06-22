package htsjdk.beta.codecs.reads.bam.bamV1_0;

import htsjdk.beta.codecs.reads.bam.BAMCodec;
import htsjdk.beta.codecs.reads.bam.BAMDecoder;
import htsjdk.beta.codecs.reads.bam.BAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.exception.HtsjdkPluginException;
import htsjdk.samtools.SamStreams;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;

/**
 * BAM codec.
 */
public class BAMCodecV1_0 extends BAMCodec {
    protected static final HtsVersion VERSION_1 = new HtsVersion(1, 0, 0);

    @Override
    public HtsVersion getVersion() {
        return VERSION_1;
    }

    @Override
    public int getSignatureProbeSize() { return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE; }

    @Override
    public int getSignatureLength() {
        return BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE;
    }

    @Override
    public boolean canDecodeStreamSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream);
        ValidationUtils.nonNull(sourceName);

        try {
            // technically this should check the version, but its BAM so there isn't one...
            return SamStreams.isBAMFile(probingInputStream);
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading signature from stream for %s", sourceName), e);
        }
    }

    @Override
    public BAMDecoder getDecoder(final Bundle inputBundle, final ReadsDecoderOptions decoderOptions) {
        return new BAMDecoderV1_0(inputBundle, decoderOptions);
    }

    @Override
    public BAMEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions encoderOptions) {
        return new BAMEncoderV1_0(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

}
