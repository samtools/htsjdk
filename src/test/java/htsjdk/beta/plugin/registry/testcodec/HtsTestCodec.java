package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureProbingStream;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

// NOTE: Unlike real codec, this codec has configurable parameters that allow it to impersonate several different
// kinds fictional file formats and versions for a fictional codec type, supporting various formats,
// versions, protocol schemes, and stream signatures.
//
// Its ok for it to be dynamically discovered when running in a test configuration, but it should
// never be discovered/included at runtime.
//
// The stream signature used by this codec is always: format concatenated with version# (for FORMAT_2, version
// "1.0.0", the embedded signature would be be "FORMAT_21.0.0".
//
public class HtsTestCodec implements HtsCodec<
        HtsTestCodecFormat,
        HtsTestDecoderOptions,
        HtsTestEncoderOptions>
{
    private final HtsVersion htsVersion;
    private final HtsTestCodecFormat htsFormat;
    private final String fileExtension;
    private final String protocolScheme;
    private final boolean useGzippedInputs;

    public HtsTestCodec() {
        // This codec is for registry testing only, and should only be instantiated by test code
        // using the other (non-standard) configuration constructor. Since this no-arg constructor
        // is the one that will be called if this codec is instantiated through normal dynamic codec
        // discovery, throw if it ever gets called.
        throw new HtsjdkPluginException("The HtsTestCodec codec should never be instantiated using the no-arg constructor");
    }

    // used by tests to create a variety of different test codecs that vary by format/version/extensions/protocol
    public HtsTestCodec(
            final HtsTestCodecFormat htsFormat,
            final HtsVersion htsVersion,
            final String fileExtension,
            final String protocolScheme,
            final boolean useGzippedInputs
    ) {
        this.htsFormat              = htsFormat;
        this.htsVersion             = htsVersion;
        this.fileExtension          = fileExtension;
        this.protocolScheme         = protocolScheme;
        this.useGzippedInputs       = useGzippedInputs;
    }

    @Override
    public HtsContentType getContentType() {
        //this isn't really an ALIGNED_READS codec, but codecs are constrained by type to use a value from
        // the HtsContentType enum
        return HtsContentType.ALIGNED_READS;
    }

    @Override
    public HtsTestCodecFormat getFileFormat() {
        return htsFormat;
    }

    @Override
    public HtsVersion getVersion() {
        return htsVersion;
    }

    @Override
    public int getSignatureProbeLength() { return 64 * 1024; }

    @Override
    public int getSignatureLength() {
        return htsFormat.name().length() + htsVersion.toString().length();
    }

    @Override
    public boolean ownsURI(final IOPath ioPath) {
        return protocolScheme != null && protocolScheme.equals(ioPath.getScheme());
    }

   @Override
    public boolean canDecodeURI(IOPath resource) {
        final Optional<String> extension = resource.getExtension();
        return extension.isPresent() && extension.get().equals(fileExtension);
    }

    @Override
    public boolean canDecodeStreamSignature(final SignatureProbingStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream);
        ValidationUtils.nonNull(sourceName);

        try {
            final int signatureSize = getSignatureLength();
            final byte[] signatureBytes = new byte[signatureSize];

            if (useGzippedInputs) {
                // first, probe to see if it looks gzipped
                final boolean isBlockCompressed = BlockCompressedInputStream.isValidFile(probingInputStream);
                probingInputStream.reset();
                if (!isBlockCompressed) {
                    return false; // this codec requires gzipped input but this input isn't gzipped
                }
            }
            try (final InputStream streamToUse =
                         useGzippedInputs ?
                                 new BlockCompressedInputStream(probingInputStream) :
                                 probingInputStream) {
                if (streamToUse.read(signatureBytes) <= 0) {
                    throw new HtsjdkIOException(String.format("Failure reading content from input stream for %s", sourceName));
                }
                return Arrays.equals(signatureBytes, (htsFormat.name() + htsVersion).getBytes());
            }
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName), e);
        }
    }

    @Override
    public HtsTestDecoder getDecoder(final Bundle inputBundle, final HtsTestDecoderOptions decoderOptions) {
        return new HtsTestDecoder(inputBundle, decoderOptions, htsFormat, htsVersion);
    }

    @Override
    public HtsTestEncoder getEncoder(final Bundle outputBundle, final HtsTestEncoderOptions encoderOptions) {
        return new HtsTestEncoder(outputBundle, encoderOptions, htsFormat, htsVersion);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

}
