package htsjdk.beta.plugin.reads;

import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.samtools.SamReaderFactory;

/**
 * ReadsDecoderOptions.
 *
 * NOTE: Pretty skeletal at this point, but this will be expanded to contain all of the various
 * SamReaderFactory methods/options.
 */
public class ReadsDecoderOptions implements HtsDecoderOptions {
    private SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
    private IOPath referencePath;

    public SamReaderFactory getSamReaderFactory() {
        return samReaderFactory;
    }

    public ReadsDecoderOptions setSamReaderFactory(final SamReaderFactory samReaderFactory) {
        this.samReaderFactory = samReaderFactory;
        return this;
    }

    public IOPath getReferencePath() {
        return referencePath;
    }

    public ReadsDecoderOptions setReferencePath(final IOPath referencePath) {
        this.referencePath = referencePath;
        return this;
    }
}
