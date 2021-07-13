package htsjdk.beta.codecs.reads.bam;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.util.BlockGunzipper;
import htsjdk.samtools.util.zip.InflaterFactory;
import htsjdk.utils.ValidationUtils;

/**
 * Decoder options specific to BAM decoders.
 */
public class BAMDecoderOptions {
    //SAMRecordFactory isn't carried over from SAMReaderFactory as an option, since it doesn't appear to
    // actually be used anywhere anymore (??)
    //
    // includeInSource isn't carried over since it populates SAMRecords with a SamReader, which the plugin
    // codecs can't provide. The only internal usage is GATK CreateHadoopSplittingIndex, which uses it
    // to reach back into the underlying SAMReader to get the current block compressed file offset.
    // That only works for BAM. We may need to provide an alternative, such as delegating support for
    // (all, including splitting) index creation into htsjdk.

    private InflaterFactory inflaterFactory = BlockGunzipper.getDefaultInflaterFactory();
    private boolean useAsyncIO              = Defaults.USE_ASYNC_IO_READ_FOR_SAMTOOLS;
    private boolean validateCRCChecksums    = false;

    /**
     * Get the {@link InflaterFactory} included in these options. Defaults to
     * {@link BlockGunzipper#getDefaultInflaterFactory()}.
     *
     * @return the {@link InflaterFactory} included in these options
     */
    public InflaterFactory getInflaterFactory() {
        return inflaterFactory;
    }

    /**
     * Set the {@link InflaterFactory} to use for these options. Defaults value is
     * {@link BlockGunzipper#getDefaultInflaterFactory()}.
     *
     * @param inflaterFactory inflater factory to use
     * @return updated BAMDecoderOptions
     */
    public BAMDecoderOptions setInflaterFactory(final InflaterFactory inflaterFactory) {
        ValidationUtils.nonNull(inflaterFactory, "InflaterFactory");
        this.inflaterFactory = inflaterFactory;
        return this;
    }

    /**
     * Determine if async IO is enabled for these options. Defaults to {@link Defaults#USE_ASYNC_IO_READ_FOR_SAMTOOLS}.
     *
     * @return true if async IO is enabled for these options
     */
    public boolean isUseAsyncIO() {
        return useAsyncIO;
    }

    /**
     * Set whether async IO is enable for these options. Defaults value is
     * {@link Defaults#USE_ASYNC_IO_READ_FOR_SAMTOOLS}.
     *
     * @param useAsyncIO true if async IO should be used,otherwise false
     * @return updated BAMDecoderOptions
     */
    public BAMDecoderOptions setUseAsyncIO(final boolean useAsyncIO) {
        this.useAsyncIO = useAsyncIO;
        return this;
    }

    /**
     * Determine whether validation of CRC checksums is enabled for these options. Defaults to false.
     *
     * @return true if CRC validation is enabled, otherwise false
     */
    public boolean isValidateCRCChecksums() {
        return validateCRCChecksums;
    }

    /**
     * Set whether validation of CRC checksums should be enabled for these options. Defaults value is false.
     *
     * @param validateCRCChecksums true to enable CRC validation, otherwise false
     * @return updated BAMDecoderOptions
     */
    public BAMDecoderOptions setValidateCRCChecksums(final boolean validateCRCChecksums) {
        this.validateCRCChecksums = validateCRCChecksums;
        return this;
    }

}
