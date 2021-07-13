package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

/**
 * CRAM v3.0 encoder.
 */
public class CRAMEncoderV3_0 extends CRAMEncoder {

    final private ReadsEncoderOptions readsEncoderOptions;
    private CRAMFileWriter cramFileWriter;

    /**
     * Create a CRAM encoder for the given output bundle. The primary resource in the bundle must
     * have content type {@link BundleResourceType#ALIGNED_READS} (to find a decoder for a bundle,
     * see {@link htsjdk.beta.plugin.registry.ReadsResolver}).
     *
     * @param outputBundle bundle to encode
     * @param readsEncoderOptions options to use
     */
    public CRAMEncoderV3_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
        this.readsEncoderOptions = readsEncoderOptions;
    }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        cramFileWriter = getCRAMWriter(samFileHeader, readsEncoderOptions);
    }

    @Override
    public void write(final SAMRecord record) {
        cramFileWriter.addAlignment(record);
    }

    @Override
    public HtsVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

    @Override
    public void close() {
        if (cramFileWriter != null) {
            cramFileWriter.close();
        }
    }

    private CRAMFileWriter getCRAMWriter(final SAMFileHeader samFileHeader, final ReadsEncoderOptions readsEncoderOptions) {
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
