package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.plugin.HtsContentType;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsFormats;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * InternalAPI
 *
 * Base class for concrete implementations of {@link HtsContentType#VARIANT_CONTEXTS} codecs.
 */
@InternalAPI
public abstract class VCFCodec implements VariantsCodec {
    // FileExtensions.VCF_LIST includes BCF, which we don't want included here
    private static final Set<String> extensionMap = new HashSet<String>() {
        private static final long serialVersionUID = 1L;
        {
            add(FileExtensions.VCF);
            add(FileExtensions.COMPRESSED_VCF);
            add(FileExtensions.COMPRESSED_VCF_BGZ);
        }
    };

    @Override
    public String getFileFormat() { return VariantsFormats.VCF; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");

        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

    @Override
    public int getSignatureLength() {
        return getSignatureString().length();
    }

    @Override
    public int getSignatureProbeLength() { return BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE; }

    @Override
    public boolean canDecodeSignature(final SignatureStream probingInputStream, final String sourceName) {
        ValidationUtils.nonNull(probingInputStream, "probingInputStream");
        ValidationUtils.nonNull(sourceName, "sourceName");

        final byte[] signatureBytes = new byte[getSignatureLength()];
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
        return Arrays.equals(getSignatureString().getBytes(), signatureBytes);
    }

    /**
     * Get the signature string for this codec.
     *
     * @return the signature string for this codec
     */
    protected abstract String getSignatureString();

}
