package htsjdk.beta.codecs.reads.bam;

import htsjdk.samtools.util.BlockGunzipper;
import htsjdk.samtools.util.zip.InflaterFactory;
import htsjdk.utils.ValidationUtils;

//TODO:
// do we need to retain custom SAMRecordFactory ? in SamReaderFactory this sets a SamReader field on
// SAMRecords (appears to only be used by GATK when creating a HadoopSplittingIndex, since it allows
// the indexer to reach back from a given SamRecord into the underlying reader to get the current file
// offset), but we don't want to pollute the new API with use the SamReader type.

//These option classes establish a default for every option; keeps them insulated from changes to
// SamReaderFactory; and ensure that getters never return null
public class BAMDecoderOptions {
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

    public boolean getIncludeSourceInRecords() {
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
