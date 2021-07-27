package htsjdk.beta.plugin.reads;

import htsjdk.beta.codecs.reads.bam.BAMDecoderOptions;
import htsjdk.beta.codecs.reads.cram.CRAMDecoderOptions;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.samtools.ValidationStringency;
import htsjdk.utils.PrivateAPI;
import htsjdk.utils.ValidationUtils;

import java.nio.channels.SeekableByteChannel;
import java.util.Optional;
import java.util.function.Function;

// Note: options not carried forward from SamReaderFactory:
//  SAMRecordFactory (doesn't appear to ever ACTUALLY be used in htsjdk, gatk or picard)

/**
 * Reads decoder options (shared/common).
 */
public class ReadsDecoderOptions implements HtsDecoderOptions {
    private ValidationStringency validationStringency   = ValidationStringency.STRICT;
    private boolean eagerlyDecode                       = false;  // honored by BAM and HtsGet
    private boolean cacheFileBasedIndexes               = false;  // honored by BAM and CRAM
    private boolean dontMemoryMapIndexes                = false;  // honored by BAM and CRAM
    //TODO: replace these with a prefetch size args, and use a local channel wrapper implementation
    private Function<SeekableByteChannel, SeekableByteChannel> readsChannelTransformer;
    private Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer;

    private BAMDecoderOptions bamDecoderOptions         = new BAMDecoderOptions();
    private CRAMDecoderOptions cramDecoderOptions       = new CRAMDecoderOptions();

    /**
     * Get the {@link ValidationStringency} for these options. Defaults to {@link ValidationStringency#STRICT}.
     *
     * @return the {@link ValidationStringency} for these options
     */
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    /**
     * Set the {@link ValidationStringency} for these options. Defaults value is {@link ValidationStringency#STRICT}.
     *
     * @param validationStringency the {@link ValidationStringency} for these options
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setValidationStringency(final ValidationStringency validationStringency) {
        ValidationUtils.nonNull(validationStringency, "validationStringency");
        this.validationStringency = validationStringency;
        return this;
    }

    /**
     * Get eagerly decoding state.
     *
     * @return true if eager decoding is enabled, otherwise false
     */
    public boolean isEagerlyDecode() {
        return eagerlyDecode;
    }

    /**
     * Set the eagerly decoding state.
     *
     * @param eagerlyDecode true if eagerly decode is set, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setEagerlyDecode(final boolean eagerlyDecode) {
        this.eagerlyDecode = eagerlyDecode;
        return this;
    }

    /**
     * Get the file based index cache setting.
     *
     * @return true if caching is enabled for index files, otherwise false
     */
    public boolean isCacheFileBasedIndexes() {
        return cacheFileBasedIndexes;
    }

    /**
     * Set file based index caching.
     *
     * @param cacheFileBasedIndexes true to set caching, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setCacheFileBasedIndexes(final boolean cacheFileBasedIndexes) {
        this.cacheFileBasedIndexes = cacheFileBasedIndexes;
        return this;
    }

    /**
     * Get the don't memory map index state.
     *
     * @return true if indexes are not memory mapped, otherwise false
     */
    public boolean isDontMemoryMapIndexes() {
        return dontMemoryMapIndexes;
    }

    /**
     * Set the don't memory map index state.
     *
     * @param dontMemoryMapIndexes true if indexes are not memory mapped, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setDontMemoryMapIndexes(final boolean dontMemoryMapIndexes) {
        this.dontMemoryMapIndexes = dontMemoryMapIndexes;
        return this;
    }

    /**
     * Get the {@link BAMDecoderOptions} for these options.
     *
     * @return the {@link BAMDecoderOptions} for these options
     */
    public BAMDecoderOptions getBAMDecoderOptions() { return bamDecoderOptions; }

    /**
     * Set the {@link BAMDecoderOptions} for these ReadsDecoderOptions.
     *
     * @param bamDecoderOptions the {@link BAMDecoderOptions} for these ReadsDecoderOptions
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setBAMDecoderOptions(final BAMDecoderOptions bamDecoderOptions) {
        ValidationUtils.nonNull(bamDecoderOptions, "bamDecoderOptions");
        this.bamDecoderOptions = bamDecoderOptions;
        return this;
    }

    /**
     * Get the {@link CRAMDecoderOptions} for these options.
     *
     * @return the {@link CRAMDecoderOptions} for these options
     */
    public CRAMDecoderOptions getCRAMDecoderOptions() { return cramDecoderOptions; }

    /**
     * Set the {@link CRAMDecoderOptions} for these ReadsDecoderOptions.
     *
     * @param cramDecoderOptions the {@link CRAMDecoderOptions} for these ReadsDecoderOptions
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setCRAMDecoderOptions(final CRAMDecoderOptions cramDecoderOptions) {
        ValidationUtils.nonNull(cramDecoderOptions, "cramDecoderOptions");
        this.cramDecoderOptions = cramDecoderOptions;
        return this;
    }

    // Temporary channel wrapper implementation.

    /**
     * Get the channel transformer for the reads resource. The channel transformer can be used to implement
     * prefetching for the reads input.
     *
     * @return the channel transformer for the reads resource if present, otherwise Optional.empty()
     */
    @PrivateAPI
    public Optional<Function<SeekableByteChannel, SeekableByteChannel>> getReadsChannelTransformer() {
        return Optional.ofNullable(readsChannelTransformer);
    }

    /**
     * Set the channel transformer for the reads resource. The channel transformer can be used to implement
     * prefetching for the reads input.
     *
     * @param readsChannelTransformer the channel transformer to be used. may be null
     * @return updated ReadsDecoderOptions
     */
    @PrivateAPI
    public ReadsDecoderOptions setReadsChannelTransformer(
            final Function<SeekableByteChannel, SeekableByteChannel> readsChannelTransformer) {
        this.readsChannelTransformer = readsChannelTransformer;
        return this;
    }

    /**
     * Get the channel transformer for the index resource. The channel transformer can be used to implement
     * prefetching for the index input.
     *
     * @return the channel transformer for the index resource if present, otherwise Optional.empty()
     */
    @PrivateAPI
    public Optional<Function<SeekableByteChannel, SeekableByteChannel>> getIndexChannelTransformer() {
        return Optional.ofNullable(indexChannelTransformer);
    }

    /**
     * Set the channel transformer for the index resource. The channel transformer can be used to implement
     * prefetching for the index input.
     *
     * @param indexChannelTransformer the channel transformer to be used. may be null.
     * @return updated ReadsDecoderOptions
     */
    @PrivateAPI
    public ReadsDecoderOptions setIndexChannelTransformer(
            final Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer) {
        this.indexChannelTransformer = indexChannelTransformer;
        return this;
    }

}
