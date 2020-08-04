package htsjdk.beta.codecs.reads.bam.bamV1_0;

import htsjdk.beta.codecs.reads.bam.BAMEncoder;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.BAMFileWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;

public class BAMEncoderV1_0 extends BAMEncoder {
    private SAMFileWriter samFileWriter;

    public BAMEncoderV1_0(final Bundle outputBundle, final ReadsEncoderOptions readsEncoderOptions) {
        super(outputBundle, readsEncoderOptions);
    }

    @Override
    public HtsCodecVersion getVersion() {
        return BAMCodecV1_0.VERSION_1;
    }

    @Override
    public void setHeader(final SAMFileHeader samFileHeader) {
        samFileWriter = getBAMFileWriter(readsEncoderOptions, samFileHeader);
    }

    @Override
    public void write(final SAMRecord record) {
        samFileWriter.addAlignment(record);
    }

    @Override
    public void close() {
        if (samFileWriter != null) {
            samFileWriter.close();
        }
    }

    private SAMFileWriter getBAMFileWriter(final HtsEncoderOptions htsEncoderOptions, final SAMFileHeader samFileHeader) {
        //TODO: expose presorted
        final boolean preSorted = true;

        final ReadsEncoderOptions readsEncoderOptions = (ReadsEncoderOptions) htsEncoderOptions;
        final BundleResource bundleResource = outputBundle.get(BundleResourceType.READS).get();

        if (bundleResource.getIOPath().isPresent()) {
            //TODO: SAMFileWriterFactory doesn't expose getters for all options (currently most are not exposed),
            // so this is currently not fully honoring the SAMFileWriterFactory
            return readsEncoderOptions.getSamFileWriterFactory().makeBAMWriter(
                    samFileHeader,
                    preSorted,
                    bundleResource.getIOPath().get().toPath());
        } else {
            //TODO: this stream constructor required changing the member access to protected...
            final BAMFileWriter bamFileWriter = new BAMFileWriter(
                    bundleResource.getOutputStream().get(),
                    getDisplayName(),
                    readsEncoderOptions.getSamFileWriterFactory().getCompressionLevel(),
                    BlockCompressedOutputStream.getDefaultDeflaterFactory());
            bamFileWriter.setHeader(samFileHeader);
            return bamFileWriter; }
    }
}
