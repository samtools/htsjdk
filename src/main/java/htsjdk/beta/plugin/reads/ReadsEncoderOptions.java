package htsjdk.beta.plugin.reads;

import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.samtools.SAMFileWriterFactory;

/**
 * ReadsEncoderOptions.
 *
 * NOTE: Pretty skeletal at this point, but this will be expanded to contain all of the various
 * SamFileWriterFactory methods/options.
 */
public class ReadsEncoderOptions implements HtsEncoderOptions {
    private SAMFileWriterFactory samFileWriterFactory = new SAMFileWriterFactory();
    private IOPath referencePath;

    public SAMFileWriterFactory getSamFileWriterFactory() {
        return samFileWriterFactory;
    }

    public ReadsEncoderOptions setSamFileWriterFactory(final SAMFileWriterFactory samFileWriterFactory) {
        this.samFileWriterFactory = samFileWriterFactory;
        return this;
    }

    public IOPath getReferencePath() {
        return referencePath;
    }

    public ReadsEncoderOptions setReferencePath(final IOPath referencePath) {
        this.referencePath = referencePath;
        return this;
    }
}
