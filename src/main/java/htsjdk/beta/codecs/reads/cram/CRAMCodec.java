package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.codecs.hapref.fasta.FASTADecoderV1_0;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.beta.exception.HtsjdkUnsupportedOperationException;
import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.SignatureStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.utils.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#READS_CRAM} codecs.
 */
@InternalAPI
public abstract class CRAMCodec implements ReadsCodec {
    protected static final Set<String> extensionMap = new HashSet<>(Arrays.asList(FileExtensions.CRAM));

    @Override
    public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");

        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

    @Override
    public boolean canDecodeSignature(final SignatureStream signatureStream, final String sourceName) {
        ValidationUtils.nonNull(signatureStream, "signatureStream");
        ValidationUtils.nonNull(sourceName, "sourceName");

        try {
            final byte[] signatureBytes = new byte[getSignatureLength()];
            final int numRead = signatureStream.read(signatureBytes);
            if (numRead < getSignatureLength()) {
                throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName));
            }
            return Arrays.equals(signatureBytes, getSignatureString().getBytes());
        } catch (IOException e) {
            throw new HtsjdkIOException(String.format("Failure reading content from stream for %s", sourceName));
        }
    }

    @Override
    public boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion) {
        throw new HtsjdkUnsupportedOperationException("Version upgrade not implemented");
    }

    @InternalAPI
    static CRAMReferenceSource getCRAMReferenceSource(final IOPath referencePath) {
        ValidationUtils.nonNull(referencePath, "referencePath");

        final FASTADecoderV1_0 fastaDecoder = (FASTADecoderV1_0)
                HtsDefaultRegistry.getHaploidReferenceResolver().getHaploidReferenceDecoder(referencePath);
        if (fastaDecoder == null) {
            throw new HtsjdkException(String.format("Unable to get reference codec for %s", referencePath));
        }

        //TODO: we need a solution here doesn't require access to this getter...its necessary because
        // the generic decoder interface is an iterable<ReferenceSequence>, but we need the native (indexed
        // by contig) interface implemented on ReferenceSequenceFile to create a ReferenceSource, so we
        // need to cast the decoder to get access to the ReferenceSequenceFile; it might be possible to
        // write a CRAMReferenceSource implementation that uses the HtsQuery interface query(String)
        final ReferenceSequenceFile refSeqFile = fastaDecoder.getReferenceSequenceFile();
        return new ReferenceSource(refSeqFile);
    }

    /**
     * Get the signature string for this codec.
     *
     * @return the signature string for this codec
     */
    protected abstract String getSignatureString();

}
