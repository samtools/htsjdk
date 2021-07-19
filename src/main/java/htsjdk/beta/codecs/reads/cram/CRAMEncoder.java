package htsjdk.beta.codecs.reads.cram;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.CRAMReferenceSource;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.utils.PrivateAPI;

/**
 /**
 * Base class for {@link htsjdk.beta.plugin.bundle.BundleResourceType#READS_CRAM} decoders.
 */
@PrivateAPI
public abstract class CRAMEncoder implements ReadsEncoder {
    protected final Bundle outputBundle;
    protected final ReadsEncoderOptions readsEncoderOptions;
    private final String displayName;

    private CRAMFileWriter cramFileWriter;

    /**
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    @PrivateAPI
    public CRAMEncoder(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        this.outputBundle = outputBundle;
        this.readsEncoderOptions = readsEncoderOptions;
        this.displayName = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS).getDisplayName();
    }

    @Override
    final public String getFileFormat() { return ReadsFormats.CRAM; }

    @Override
    final public String getDisplayName() { return displayName; }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        cramFileWriter = getCRAMWriter(samFileHeader, readsEncoderOptions);
    }

    @Override
    public void write(final SAMRecord record) {
        cramFileWriter.addAlignment(record);
    }

    @Override
    public void close() {
        if (cramFileWriter != null) {
            cramFileWriter.close();
        }
    }

    /**
     * Return a {@link CRAMReferenceSource} using the {@link ReadsEncoderOptions}, or a default source.
     *
     * @param readsEncoderOptions options to use
     * @return a {@link CRAMReferenceSource}
     */
    private static CRAMReferenceSource getCRAMReferenceSource(final ReadsEncoderOptions readsEncoderOptions) {
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

    private CRAMFileWriter getCRAMWriter(final SAMFileHeader samFileHeader, final ReadsEncoderOptions readsEncoderOptions) {

        // TODO: respect presorted, options, CrAMDecoderOptions

        final BundleResource outputResource = outputBundle.getOrThrow(BundleResourceType.ALIGNED_READS);
        if (outputResource.getIOPath().isPresent()) {
            cramFileWriter = new CRAMFileWriter(
                    outputResource.getIOPath().get().getOutputStream(),
                    getCRAMReferenceSource(readsEncoderOptions),
                    samFileHeader,
                    outputResource.getIOPath().get().toString());
            return cramFileWriter;
        } else {
            cramFileWriter = new CRAMFileWriter(
                    outputResource.getOutputStream().get(),
                    getCRAMReferenceSource(readsEncoderOptions),
                    samFileHeader,
                    outputResource.getDisplayName());
            return cramFileWriter;
        }
    }

}
