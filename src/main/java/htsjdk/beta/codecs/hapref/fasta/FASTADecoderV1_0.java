package htsjdk.beta.codecs.hapref.fasta;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResource;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormats;
import htsjdk.beta.exception.HtsjdkIOException;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.annotations.InternalAPI;

import java.io.IOException;

/**
 * The v1.0 FASTA decoder.
 */
public class FASTADecoderV1_0 implements HaploidReferenceDecoder {
    private final String displayName;
    protected Bundle inputBundle;

    @Override
    public String getDisplayName() { return displayName; }

    private final ReferenceSequenceFile referenceSequenceFile;

    public FASTADecoderV1_0(final Bundle inputBundle) {
        this.inputBundle = inputBundle;
        this.displayName = inputBundle.getPrimaryResource().getDisplayName();
        final BundleResource referenceResource = inputBundle.getOrThrow(BundleResourceType.CT_HAPLOID_REFERENCE);
        if (referenceResource.getIOPath().isPresent()) {
            referenceSequenceFile = ReferenceSequenceFileFactory.getReferenceSequenceFileFromBundle(inputBundle, true, true);
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
    final public String getFileFormat() { return HaploidReferenceFormats.FASTA; }

    @Override
    public SAMSequenceDictionary getHeader() {
        return referenceSequenceFile.getSequenceDictionary();
    }

    @Override
    public HtsVersion getVersion() {
        return FASTACodecV1_0.VERSION_1;
    }

    @Override
    public CloseableIterator<ReferenceSequence> iterator() {
        referenceSequenceFile.reset();
        return new CloseableIterator<ReferenceSequence>() {
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

            @Override
            public void close() {
                try {
                    referenceSequenceFile.close();
                } catch(final IOException e) {
                    throw new HtsjdkIOException(e);
                }
            }
        };
    }

    @Override
    public boolean isQueryable() {
        return hasIndex();
    }

    @Override
    public boolean hasIndex() {
        return bundleContainsIndex(inputBundle) && referenceSequenceFile.isIndexed();
    }

    //TODO: we need a solution here that doesn't depend on this getter...its necessary because
    // the generic decoder interface exports an iterable<ReferenceSequence>, but we need the native
    // (indexed by contig) interface implemented on ReferenceSequenceFile to create a ReferenceSource,
    // it might be possible to write a CRAMReferenceSource implementation that uses the HtsQuery
    // interface query(String)
    @InternalAPI
    public ReferenceSequenceFile getReferenceSequenceFile() {
        return referenceSequenceFile;
    }

    @Override
    public void close() {
        if (referenceSequenceFile != null) {
            try {
                referenceSequenceFile.close();
            } catch (IOException e) {
                throw new HtsjdkIOException(e);
            }
        }
    }

    /**
     * Return true if the input {@link Bundle} contains a reads index resource
     *
     * @param inputBundle input {@link Bundle} to inspect
     * @return true if input {@link Bundle} contains a reads index resource
     */
    private static boolean bundleContainsIndex(final Bundle inputBundle) {
        return inputBundle.get(BundleResourceType.CT_READS_INDEX).isPresent();
    }

}
