package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.codecs.hapref.fasta.FASTADecoderV1_0;
import htsjdk.beta.plugin.registry.HtsDefaultRegistry;
import htsjdk.beta.exception.HtsjdkException;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.util.FileExtensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_CRAM} codecs.
 */
public abstract class CRAMCodec implements ReadsCodec {
    private static final Set<String> extensionMap = new HashSet(Arrays.asList(FileExtensions.CRAM));

    @Override
    public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

    public static CRAMReferenceSource getCRAMReferenceSource(final IOPath referencePath) {
        final FASTADecoderV1_0 fastaDecoder = (FASTADecoderV1_0)
                HtsDefaultRegistry.getHaploidReferenceResolver().getHapRefDecoder(referencePath);
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

}
