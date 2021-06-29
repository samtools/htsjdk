package htsjdk.beta.codecs.reads.bam;

import htsjdk.samtools.util.BlockGunzipper;
import htsjdk.samtools.util.zip.InflaterFactory;
import htsjdk.utils.ValidationUtils;

//These option classes establish a default for every option; keeps them insulated from changes to
// SamReaderFactory; and ensure that getters never return null
public class BAMDecoderOptions {
    //SAMRecordFactory isn't carried over from SAMReaderFactory tothese options, since it
    // populates SAMRecords with a SamReader. Most of the codec implementations don't use a
    // SamReader (those that do are temporary), and we don't want the plugin APIs to have a
    // dependency on that.
    // The only usage I've found is GATK CreateHadoopSplittingIndex, where the indexer reaches
    // back into the underlying SAMReader to get the current block compressed file offset. That
    // only works for BAM. We may need to provide an alternative, such as delegating support for
    // splitting index creation into htsjdk.

    private InflaterFactory inflaterFactory = BlockGunzipper.getDefaultInflaterFactory();
    private boolean includeSourceInRecords  = false;  // used by CreateHadoopSplittingIndex for BAM only
    private boolean useAsyncIO              = false;
    private boolean validateCRCChecksums    = false;

    public InflaterFactory getInflaterFactory() {
        return inflaterFactory;
    }

    public BAMDecoderOptions setInflaterFactory(final InflaterFactory inflaterFactory) {
        ValidationUtils.nonNull(inflaterFactory, "InflaterFactory");
        this.inflaterFactory = inflaterFactory;
        return this;
    }

    public boolean isIncludeSourceInRecords() {
        return includeSourceInRecords;
    }

    public BAMDecoderOptions setIncludeSourceInRecords(final boolean includeSourceInRecords) {
        this.includeSourceInRecords = includeSourceInRecords;
        return this;
    }

    public boolean isUseAsyncIO() {
        return useAsyncIO;
    }

    public BAMDecoderOptions setUseAsyncIO(final boolean useAsyncIO) {
        this.useAsyncIO = useAsyncIO;
        return this;
    }

    public boolean isValidateCRCChecksums() {
        return validateCRCChecksums;
    }

    public BAMDecoderOptions setValidateCRCChecksums(final boolean validateCRCChecksums) {
        this.validateCRCChecksums = validateCRCChecksums;
        return this;
    }

}
