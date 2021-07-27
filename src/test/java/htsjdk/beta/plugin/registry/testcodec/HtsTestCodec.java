package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Optional;

// NOTE: Unlike a real codec, this codec has configurable parameters that allow it to impersonate
// several different fictional (reads) file formats and versions, using various custom protocol
// schemes, file extensions and stream signatures.
//
// The stream signature format used by this codec is always: "format" concatenated with "version#"
// (for FORMAT_2, version "1.0.0", the embedded signature would be be "FORMAT_21.0.0").
//
public class HtsTestCodec implements ReadsCodec {
    private final HtsVersion htsVersion;
    private final String htsFormat;
    private final String fileExtension;
    private final String protocolScheme;
    private final boolean useGzippedInputs;

    public HtsTestCodec() {
        // This codec is for registry testing only, and should only be instantiated by test code
        // using the other (non-standard) constructor that allows it to be configured with test
        // parameters. Since this no-arg constructor is the one that will be called if the codec
        // is ever instantiated through normal codec discovery, throw if it ever gets called, since
        // it probably indicates a packaging issue.
        throw new HtsjdkPluginException("The HtsTestCodec codec should never be instantiated using the no-arg constructor");
    }

    // used by tests to create a variety of different test codecs that vary by format/version/extensions/protocol
    public HtsTestCodec(
            final String htsFormat,
            final HtsVersion htsVersion,
            final String fileExtension,
            final String protocolScheme,
            final boolean useGzippedInputs) {
        this.htsFormat              = htsFormat;
        this.htsVersion             = htsVersion;
        this.fileExtension          = fileExtension;
        this.protocolScheme         = protocolScheme;
        this.useGzippedInputs       = useGzippedInputs;
    }

    @Override
    public HtsContentType getContentType() {
        // being a test codec, this isn't a real ALIGNED_READS codec, but since all codecs are
        // constrained to be one of the known content types from the HtsContentType enum, it has
        // to masquerade as one of those, so we use aligned reads (it also implements ReadsCodec)
        return HtsContentType.ALIGNED_READS;
    }

    @Override
    public String getFileFormat() {
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
        return htsFormat.length() + htsVersion.toString().length();
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
    public boolean canDecodeSignature(final SignatureStream probingInputStream, final String sourceName) {
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
                return Arrays.equals(signatureBytes, (htsFormat + htsVersion).getBytes());
            }
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName), e);
        }
    }

    @Override
    public ReadsDecoder getDecoder(final Bundle inputBundle, final ReadsDecoderOptions decoderOptions) {
        return new HtsTestDecoder(inputBundle, decoderOptions, htsFormat, htsVersion);
    }

    @Override
    public ReadsEncoder getEncoder(final Bundle outputBundle, final ReadsEncoderOptions encoderOptions) {
        return new HtsTestEncoder(outputBundle, encoderOptions, htsFormat, htsVersion);
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkPluginException("Not yet implemented");
    }

}
