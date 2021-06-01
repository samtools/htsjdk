package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.util.Iterator;

/**
 * A FASTA file decoder.
 */
public class FASTADecoderV1_0 implements HaploidReferenceDecoder {
    protected Bundle haprefBundle;
    private final String displayName;

    @Override
    public String getDisplayName() { return displayName; }

    private ReferenceSequenceFile referenceSequenceFile;

    public FASTADecoderV1_0(final Bundle inputBundle) {
        this.haprefBundle = inputBundle;
        this.displayName = inputBundle.getPrimaryResource().getDisplayName();
        final BundleResource referenceResource = inputBundle.getOrThrow(BundleResourceType.HAPLOID_REFERENCE);
        if (referenceResource.getIOPath().isPresent()) {
            referenceSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(
                    referenceResource.getIOPath().get().toPath(), true);
        } else {
            final SeekableStream seekableStream = referenceResource.getSeekableStream().orElseThrow(
                    () -> new IllegalArgumentException(
                            String.format("The reference resource %s is not able to supply the required seekable stream",
                                    referenceResource.getDisplayName())));
            referenceSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFile(
                    referenceResource.getDisplayName(),
                    seekableStream,
                    null
            );
        }
    }

    @Override
    final public HaploidReferenceFormat getFormat() { return HaploidReferenceFormat.FASTA; }

    @Override
    public SAMSequenceDictionary getHeader() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public HtsCodecVersion getVersion() {
        return FASTACodecV1_0.VERSION_1;
    }

    //TODO: this needs to consult the inputBundle
    @Override
    public Iterator<ReferenceSequence> iterator() {
        referenceSequenceFile.reset();
        return new Iterator<ReferenceSequence>() {
            ReferenceSequence nextSeq = referenceSequenceFile.nextSequence();

            @Override
            public boolean hasNext() {
                return nextSeq != null;
            }

            @Override
            public ReferenceSequence next() {
                final ReferenceSequence tmpSeq = nextSeq;
                nextSeq = referenceSequenceFile.nextSequence();
                return tmpSeq;
            }
        };
    }

    //TODO: this shouldn't be necessary
    public ReferenceSequenceFile getReferenceSequenceFile() {
        return referenceSequenceFile;
    }

    @Override
    public void close() {
        if (referenceSequenceFile != null) {
            try {
                referenceSequenceFile.close();
            } catch (IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

}
