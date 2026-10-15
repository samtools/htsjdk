package htsjdk.beta.codecs.variants.vcf;

import htsjdk.annotations.InternalAPI;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.vcf.VariantFileType;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Optional;

/**
 * InternalAPI
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} codecs.
 */
@InternalAPI
public abstract class VCFCodec implements VariantsCodec {
    @Override
    public String getFileFormat() {
        return VariantsFormats.VCF;
    }

    /**
     * @return true if the URI's path (or, for an opaque URI, its scheme-specific part) names a VCF, compressed or
     *     not, as
     *     {@link VariantFileType#fromFileName(String)} recognises it; BCF is not this codec's
     */
    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        // the URI rather than ioPath.toPath(), which an htsget or other remote URI may not resolve to; an opaque URI
        // (a jar or zip file system's, say) has no path, but its scheme-specific part ends with the file name too
        final URI uri = ioPath.getURI();
        final String name = uri.getPath() != null ? uri.getPath() : uri.getSchemeSpecificPart();
        final Optional<VariantFileType> type = VariantFileType.fromFileName(name);
        return type.isPresent() && type.get() != VariantFileType.BCF;
    }

    @Override
    public int getSignatureLength() {
        return getSignatureString().length();
    }

    @Override
    public int getSignatureProbeLength() {
        return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE;
    }

    @Override
    public boolean canDecodeSignature(final SignatureStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream, "probingInputStream");
        ValidationUtils.nonNull(sourceName, "sourceName");

        final byte[] signatureBytes = new byte[getSignatureLength()];
        try {
            final InputStream wrappedInputStream = IOUtil.isGZIPInputStream(probingInputStream)
                    ? IOUtil.openGzipOrBgzfStream(probingInputStream)
                    : probingInputStream;
            final int numRead = wrappedInputStream.read(signatureBytes);
            if (numRead < 0) {
                throw new HtsjdkIOException(String.format("0 bytes read from input stream for %s", sourceName));
            }
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading signature from stream for %s", sourceName), e);
        }
        return Arrays.equals(getSignatureString().getBytes(), signatureBytes);
    }

    /**
     * Get the signature string for this codec.
     *
     * @return the signature string for this codec
     */
    protected abstract String getSignatureString();
}
