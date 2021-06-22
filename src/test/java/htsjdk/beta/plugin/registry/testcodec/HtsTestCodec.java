package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.exception.HtsjdkPluginException;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

// NOTE: This codec has configurable parameters that allow the tests to instantiate several variations
// of codecs for a fictional file format, with different sub content types, versions, protocol schemes,
// and stream signatures.
//
// Its ok for it to be dynamically discovered when running in a test configuration, but it should
// never be discovered/included at runtime.
//
public class HtsTestCodec implements HtsCodec<
        HtsTestCodecFormat,
        HtsTestDecoderOptions,
        HtsTestEncoderOptions>
{
    private final HtsVersion htsVersion;
    private final HtsTestCodecFormat htsFormat;
    private final  String contentSubType;
    private final  String fileExtension;
    private final  String streamSignature;
    private final  String protocolScheme;
    private final boolean useGzippedInputs;

    public HtsTestCodec() {
        // This codec is for registry testing only, and should only be instantiated by test code
        // using the other (non-standard) configuration constructor. Since this no-arg constructor
        // is the one that will be called if this codec is instantiated through normal dynamic codec
        // discovery, throw if it ever gets called.
        throw new HtsjdkPluginException("This codec should never be instantiated using the no-arg constructor");
    }

    // used by tests to create a variety of different test codecs that vary by format/version/extensions/protocol
    public HtsTestCodec(
            final HtsTestCodecFormat htsFormat,
            final HtsVersion htsVersion,
            final String contentSubType,
            final String fileExtension,
            final String streamSignature,
            final String protocolScheme,
            final boolean useGzippedInputs
    ) {
        this.htsFormat              = htsFormat;
        this.htsVersion             = htsVersion;
        this.contentSubType         = contentSubType;
        this.fileExtension          = fileExtension;
        this.streamSignature        = streamSignature;
        this.protocolScheme         = protocolScheme;
        this.useGzippedInputs       = useGzippedInputs;
    }

    @Override
    public HtsCodecType getCodecType() {
        //this isn't really an ALIGNED_READS codec, but codecs are constrianed by type to use a value from
        // the HtsCodecType enum
        return HtsCodecType.ALIGNED_READS;
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
    public int getSignatureProbeSize() { return 64 * 1024; }

    @Override
    public int getSignatureActualSize() {
        return streamSignature.length() + htsVersion.toString().length();
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
    public boolean canDecodeStreamSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream);
        ValidationUtils.nonNull(sourceName);

        try {
            final int signatureSize = getSignatureActualSize();
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
                return Arrays.equals(signatureBytes, (streamSignature + htsVersion).getBytes());
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
