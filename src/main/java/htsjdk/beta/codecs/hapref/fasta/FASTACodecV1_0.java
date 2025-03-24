package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoderOptions;
import htsjdk.beta.plugin.hapref.HaploidReferenceEncoder;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.plugin.hapref.HaploidReferenceEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormats;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * The v1.0 FASTA codec.
 */
public class FASTACodecV1_0 implements HaploidReferenceCodec {
    public static final HtsVersion VERSION_1 = new HtsVersion(1, 0, 0);

    @Override
    public HtsVersion getVersion() {
        return VERSION_1;
    }

    @Override
    public String getFileFormat() {
        return HaploidReferenceFormats.FASTA;
    }

    @Override
    public int getSignatureLength() { return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE; }

    @Override
    public boolean canDecodeSignature(final SignatureStream signatureStream, final String sourceName) {
        ValidationUtils.nonNull(signatureStream, "input signatureStream may notbe null");
        ValidationUtils.nonNull(sourceName, "sourceName");

        try {
            final InputStream wrappedInputStream = IOUtil.isGZIPInputStream(signatureStream) ?
                    new GZIPInputStream(signatureStream) :
                    signatureStream;
            int ch = wrappedInputStream.read();
            if (ch == -1) {
                throw new HtsjdkIOException(
                        String.format("Codec %s failed probing signature for resource %s", this.getDisplayName(), sourceName));
            }
            return ((char) ch) == '>';  // for FASTA, this is all we have to go on...
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading signature from stream for %s", sourceName), e);
        }
    }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        return FileExtensions.FASTA.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

   @Override
    public HaploidReferenceDecoder getDecoder(final Bundle inputBundle, final HaploidReferenceDecoderOptions options) {
       ValidationUtils.nonNull(inputBundle, "input bundle");
       ValidationUtils.nonNull(options, "reference encoder options");
        return new FASTADecoderV1_0(inputBundle);
    }

    @Override
    public HaploidReferenceEncoder getEncoder(final Bundle outputBundle, final HaploidReferenceEncoderOptions options) {
        ValidationUtils.nonNull(outputBundle, "output bundle");
        ValidationUtils.nonNull(options, "reference encoder options");
        throw new HtsjdkUnsupportedOperationException("FASTA encoder not implemented");
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkUnsupportedOperationException("Not implemented");
    }
}
