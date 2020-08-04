package htsjdk.beta.codecs.variants.vcf.vcfv4_2;

import htsjdk.beta.codecs.variants.vcf.VCFCodec;
import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.registry.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

public class VCFCodecV4_2 extends VCFCodec {
    public static final HtsCodecVersion VCF_V42_VERSION = new HtsCodecVersion(4, 2,0);
    protected static final String VCF_V42_MAGIC = "##fileformat=VCFv4.2";

    @Override
    public HtsCodecVersion getVersion() { return VCF_V42_VERSION; }

    @Override
    public int getSignatureSize() {
        return VCF_V42_MAGIC.length();
    }

    @Override
    public int getSignatureProbeStreamSize() { return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE; }

    @Override
    public boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        final byte[] signatureBytes = new byte[getSignatureSize()];
        try {
            final InputStream wrappedInputStream = IOUtil.isGZIPInputStream(probingInputStream) ?
                    new GZIPInputStream(probingInputStream) :
                    probingInputStream;
            final int numRead = wrappedInputStream.read(signatureBytes);
            if (numRead < 0) {
                throw new IOException(String.format("0 bytes read from input stream for %s", sourceName));
            }
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading signature from stream for %s", sourceName), e);
        }
        return Arrays.equals(VCF_V42_MAGIC.getBytes(), signatureBytes);
    }

    @Override
    public VCFDecoder getDecoder(final Bundle inputBundle, final VariantsDecoderOptions decoderOptions) {
        return new VCFDecoderV4_2(inputBundle, decoderOptions);
    }

    @Override
    public VCFEncoder getEncoder(final Bundle outputBundle, final VariantsEncoderOptions encoderOptions) {
        return new VCFEncoderV4_2(outputBundle, encoderOptions);
    }

    @Override
    public boolean runVersionUpgrade(final HtsCodecVersion sourceCodecVersion, final HtsCodecVersion targetCodecVersion) {
        throw new RuntimeException("Not yet implemented");
    }

}
