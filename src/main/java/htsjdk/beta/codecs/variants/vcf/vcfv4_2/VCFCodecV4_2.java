package htsjdk.beta.codecs.variants.vcf.vcfv4_2;

import htsjdk.beta.codecs.variants.vcf.VCFCodec;
import htsjdk.beta.codecs.variants.vcf.VCFDecoder;
import htsjdk.beta.codecs.variants.vcf.VCFEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.exception.HtsjdkPluginException;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.IOUtil;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

public class VCFCodecV4_2 extends VCFCodec {
    public static final HtsVersion VCF_V42_VERSION = new HtsVersion(4, 2,0);
    protected static final String VCF_V42_MAGIC = "##fileformat=VCFv4.2";

    @Override
    public HtsVersion getVersion() { return VCF_V42_VERSION; }

    @Override
    public int getSignatureActualSize() {
        return VCF_V42_MAGIC.length();
    }

    @Override
    public int getSignatureProbeSize() { return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE; }

    @Override
    public boolean canDecodeStreamSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        final byte[] signatureBytes = new byte[getSignatureActualSize()];
        try {
            final InputStream wrappedInputStream = IOUtil.isGZIPInputStream(probingInputStream) ?
                    new GZIPInputStream(probingInputStream) :
                    probingInputStream;
            final int numRead = wrappedInputStream.read(signatureBytes);
            if (numRead < 0) {
                throw new HtsjdkIOException(String.format("0 bytes read from input stream for %s", sourceName));
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
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

}
