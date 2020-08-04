package htsjdk.beta.codecs.reads.cram;

import htsjdk.io.IOPath;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.SamReaderFactory;

/**
 * Custom decoder options for CRAM encoder/decoders. This enables decoders that can accept
 * things such as a custom encoding map or other CRAM-specific params.
 *
 * NOTE: Currently this doesn't implement any specific options, and is just to illustrate the
 * ability to pass custom options to codecs that can use them.
 */
public class CRAMDecoderOptions extends ReadsDecoderOptions {
    private SamReaderFactory samReaderFactory = SamReaderFactory.makeDefault();
    private IOPath referencePath;

    public SamReaderFactory getSamReaderFactory() {
        return samReaderFactory;
    }

    public CRAMDecoderOptions setSamReaderFactory(final SamReaderFactory samReaderFactory) {
        this.samReaderFactory = samReaderFactory;
        return this;
    }

    public IOPath getReferencePath() {
        return referencePath;
    }

    public CRAMDecoderOptions setReferencePath(final IOPath referencePath) {
        this.referencePath = referencePath;
        return this;
    }

}
