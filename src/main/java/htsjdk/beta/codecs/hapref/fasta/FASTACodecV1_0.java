package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceEncoder;
import htsjdk.beta.plugin.bundle.SignatureProbingStream;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.utils.ValidationUtils;

/**
 * A FASTA codec.
 */
public class FASTACodecV1_0 implements HaploidReferenceCodec {

    public static final HtsVersion VERSION_1 = new HtsVersion(1, 0, 0);

    @Override
    public HtsVersion getVersion() {
        return VERSION_1;
    }

    @Override
    public HaploidReferenceFormat getFileFormat() {
        return HaploidReferenceFormat.FASTA;
    }

    @Override
    public int getSignatureLength() {
        return 1;
    }

    @Override
    public boolean canDecodeStreamSignature(final SignatureProbingStream signatureProbingStream, final String sourceName) {
        int ch = signatureProbingStream.read();
        if (ch == -1) {
            throw new HtsjdkIOException(
                    String.format("Codec %s failed probing signature for resource %s", this.getDisplayName(), sourceName));
        }
        return ((char) ch) == '>';  // for FASTA, this is all we have to go on...
    }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return FileExtensions.FASTA.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

   @Override
    public HaploidReferenceDecoder getDecoder(final Bundle inputBundle, final HtsDecoderOptions options) {
        ValidationUtils.validateArg(options == null, "reference reader options must be null");
        return new FASTADecoderV1_0(inputBundle);
    }

    @Override
    public HaploidReferenceEncoder getEncoder(final Bundle outputBundle, final HtsEncoderOptions options) {
        throw new HtsjdkPluginException("FASTA encoder not implemented");
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not implemented");
    }
}
