package htsjdk.beta.plugin.reads;

import htsjdk.beta.codecs.reads.bam.BAMEncoderOptions;
import htsjdk.beta.codecs.reads.cram.CRAMEncoderOptions;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

/**
 * ReadsEncoderOptions.
 */
public class ReadsEncoderOptions implements HtsEncoderOptions {
    private SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();

    private BAMEncoderOptions bamEncoderOptions;
    private CRAMEncoderOptions cramEncoderOptions;

    public SAMFileWriterFactory getSamFileWriterFactory() {
        return samFileWriterFactory;
    }

    public ReadsEncoderOptions setSamFileWriterFactory(final SAMFileWriterFactory samFileWriterFactory) {
        this.samFileWriterFactory = samFileWriterFactory;
        return this;
    }

    public Optional<BAMEncoderOptions> getBAMEncoderOptions() {
        return Optional.ofNullable(bamEncoderOptions);
    }

    public ReadsEncoderOptions setBAMEncoderOptions(final BAMEncoderOptions bamEncoderOptions) {
        ValidationUtils.nonNull(bamEncoderOptions, "bamDecoderOptions");
        this.bamEncoderOptions = bamEncoderOptions;
        return this;
    }

    public Optional<CRAMEncoderOptions> getCRAMEncoderOptions() {
        return Optional.ofNullable(cramEncoderOptions);
    }

    public ReadsEncoderOptions setCRAMEncoderOptions(final CRAMEncoderOptions cramEncoderOptions) {
        ValidationUtils.nonNull(cramEncoderOptions, "cramDecoderOptions");
        this.cramEncoderOptions = cramEncoderOptions;
        return this;
    }

}
