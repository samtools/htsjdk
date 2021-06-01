package htsjdk.beta.codecs.reads.cram.cramV3_0;

import htsjdk.beta.codecs.reads.cram.CRAMCodec;
import htsjdk.beta.codecs.reads.cram.CRAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.CRAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.ref.ReferenceSource;

/**
 * CRAM v3.0 encoder.
 */
public class CRAMEncoderV3_0 extends CRAMEncoder {

    final private ReadsEncoderOptions readsEncoderOptions;
    private CRAMFileWriter cramFileWriter;

    public CRAMEncoderV3_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
        this.readsEncoderOptions = readsEncoderOptions;
    }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        cramFileWriter = getCRAMWriter(samFileHeader);
    }

    @Override
    public void write(final SAMRecord record) {
        cramFileWriter.addAlignment(record);
    }

    @Override
    public HtsCodecVersion getVersion() {
        return CRAMCodecV3_0.VERSION_3_0;
    }

    @Override
    public void close() {
        if (cramFileWriter != null) {
            cramFileWriter.close();
        }
    }

    private CRAMFileWriter getCRAMWriter(final SAMFileHeader samFileHeader) {
        final BundleResource outputResource = outputBundle.getOrThrow(BundleResourceType.READS);
        if (outputResource.getIOPath().isPresent()) {
            cramFileWriter = new CRAMFileWriter(
                    outputResource.getIOPath().get().getOutputStream(),
                    getCRAMReferenceSource(),
                    samFileHeader,
                    outputResource.getIOPath().get().toString());
            return cramFileWriter;
        } else {
            cramFileWriter = new CRAMFileWriter(
                    outputResource.getOutputStream().get(),
                    getCRAMReferenceSource(),
                    samFileHeader,
                    outputResource.getDisplayName());
            return cramFileWriter;
        }
    }

}
