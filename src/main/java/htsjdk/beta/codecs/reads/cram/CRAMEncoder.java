package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;

import java.util.Optional;

/**
 /**
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_CRAM} decoders.
 */
public abstract class CRAMEncoder implements ReadsEncoder {
    protected final Bundle outputBundle;
    protected final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    /**
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    public CRAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    /**
     * Return a {@link CRAMReferenceSource} using the {@link ReadsEncoderOptions}, or a default source.
     *
     * @param readsEncoderOptions options to use
     * @return a {@link CRAMReferenceSource}
     */
    protected static CRAMReferenceSource getCRAMReferenceSource(final ReadsEncoderOptions readsEncoderOptions) {
        final CRAMEncoderOptions cramEncoderOptions = readsEncoderOptions.getCRAMEncoderOptions();
        if (cramEncoderOptions.getReferenceSource().isPresent()) {
            return cramEncoderOptions.getReferenceSource().get();
        } else if (cramEncoderOptions.getReferencePath().isPresent()) {
            return CRAMCodec.getCRAMReferenceSource(cramEncoderOptions.getReferencePath().get());
        }

        // if none is specified, get the default "lazy" reference source that throws when queried, to allow
        // operations that don't require a reference
        return ReferenceSource.getDefaultCRAMReferenceSource();
    }

}
