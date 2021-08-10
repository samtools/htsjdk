package htsjdk.beta.codecs.reads.sam;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.io.IOPath;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.util.Arrays;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#READS_SAM} codecs.
 */
@InternalAPI
public abstract class SAMCodec implements ReadsCodec {
    private static String SAM_HEADER_SENTINEL = "@HD";
    private static String SAM_EXTENSION = ".sam";

    @Override
    public String getFileFormat() { return ReadsFormats.SAM; }

    @Override
    public String getDisplayName() {
        return ReadsCodec.super.getDisplayName();
    }

    @Override
    public boolean ownsURI(IOPath ioPath) { return false; }

    @Override
    public boolean canDecodeURI(IOPath ioPath) {
        return ioPath.hasExtension(SAM_EXTENSION);
    }

    @Override
    public boolean canDecodeSignature(final SignatureStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream);
        ValidationUtils.nonNull(sourceName);

        final byte[] streamSignature = new byte[getSignatureProbeLength()];
        try {
            probingInputStream.read(streamSignature);
            return Arrays.equals(streamSignature, SAM_HEADER_SENTINEL.getBytes());
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading signature from stream for %s", sourceName), e);
        }
    }

    @Override
    public int getSignatureProbeLength() { return SAM_HEADER_SENTINEL.length(); }

    @Override
    public int getSignatureLength() {
        return SAM_HEADER_SENTINEL.length();
    }

}
