package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.codecs.hapref.fasta.FASTADecoderV1_0;
import htsjdk.beta.plugin.registry.HtsHaploidReferenceCodecs;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.util.FileExtensions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Base class for CRAM codecs.
 */
public abstract class CRAMCodec implements ReadsCodec {
    private static final Set<String> extensionMap = new HashSet(Arrays.asList(FileExtensions.CRAM));

    @Override
    public ReadsFormat getFileFormat() { return ReadsFormat.CRAM; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

    public static CRAMReferenceSource getCRAMReferenceSource(final IOPath referencePath) {
        //TODO: we need something better here than requiring this case...its necessary because the
        // generic decoder interface is an iterable<ReferenceSequence>, but we need the native (indexed
        // by contig) interface implemented on ReferenceSequenceFile, so we need to cast the decoder in
        // order to get access to the ReferenceSequenceFile
        // maybe the indexing interface could handle this via query(String)
        final FASTADecoderV1_0 fastaV1Decoder = (FASTADecoderV1_0) HtsHaploidReferenceCodecs.getHapRefDecoder(referencePath);
        if (fastaV1Decoder == null) {
            throw new RuntimeException(String.format("Unable to get reference codec for %s", referencePath));
        }

        final ReferenceSequenceFile refSeqFile = fastaV1Decoder.getReferenceSequenceFile();
        return new ReferenceSource(refSeqFile);
    }

}
