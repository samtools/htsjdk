package htsjdk.beta.plugin.reads;

import htsjdk.beta.codecs.reads.bam.BAMDecoderOptions;
import htsjdk.beta.codecs.reads.cram.CRAMDecoderOptions;
import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.samtools.ValidationStringency;
import htsjdk.annotations.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.nio.channels.SeekableByteChannel;
import java.util.Optional;
import java.util.function.Function;

/**
 * Reads decoder options (shared/common).
 */
public class ReadsDecoderOptions implements HtsDecoderOptions {
    private ValidationStringency validationStringency   = ValidationStringency.STRICT;
    private boolean eagerlyDecode                       = false;  // honored by BAM and HtsGet
    private boolean fileBasedIndexCached                = false;  // honored by BAM and CRAM
    private boolean memoryMapIndexes                    = true;   // honored by BAM and CRAM
    //TODO: replace these with a prefetch size args, and use a local channel wrapper implementation
    private Function<SeekableByteChannel, SeekableByteChannel> readsChannelTransformer;
    private Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer;
    private BAMDecoderOptions bamDecoderOptions         = new BAMDecoderOptions();
    private CRAMDecoderOptions cramDecoderOptions       = new CRAMDecoderOptions();

    /**
     * Get the {@link ValidationStringency} used for these options. Defaults to {@link ValidationStringency#STRICT}.
     *
     * @return the {@link ValidationStringency} used for these options
     */
    public ValidationStringency getValidationStringency() {
        return validationStringency;
    }

    /**
     * Set the {@link ValidationStringency} used for these options. Defaults value is
     * {@link ValidationStringency#STRICT}.
     *
     * @param validationStringency the {@link ValidationStringency} used for these options
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setValidationStringency(final ValidationStringency validationStringency) {
        ValidationUtils.nonNull(validationStringency, "validationStringency");
        this.validationStringency = validationStringency;
        return this;
    }

    /**
     * Get eagerly decoding state used for these options.
     *
     * @return true if eager decoding is enabled, otherwise false
     */
    public boolean isDecodeEagerly() {
        return eagerlyDecode;
    }

    /**
     * Set the eagerly decoding state used for these options.
     *
     * @param eagerlyDecode true if eagerly decode is set, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setDecodeEagerly(final boolean eagerlyDecode) {
        this.eagerlyDecode = eagerlyDecode;
        return this;
    }

    /**
     * Get the file based index cache setting used for these options.
     *
     * @return true if caching is enabled for index files, otherwise false
     */
    public boolean isFileBasedIndexCached() {
        return fileBasedIndexCached;
    }

    /**
     * Set id file based index caching is enabled for these options.
     *
     * @param fileBasedIndexCached true to enable caching, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setFileBasedIndexCached(final boolean fileBasedIndexCached) {
        this.fileBasedIndexCached = fileBasedIndexCached;
        return this;
    }

    /**
     * Get whether mapping indexes is enabled. Defaults to true.
     *
     * @return true if indexes are memory mapped, otherwise false
     */
    public boolean isMemoryMapIndexes() {
        return memoryMapIndexes;
    }

    /**
     * Set whether memory mapping indexes is enabled. Defaults to true.
     *
     * @param memoryMapIndexes true if indexes are memory mapped, otherwise false
     * @return updated ReadsDecoderOptions
     */
    public ReadsDecoderOptions setMemoryMapIndexes(final boolean memoryMapIndexes) {
        this.memoryMapIndexes = memoryMapIndexes;
        return this;
    }

    /**
     * Get the {@link BAMDecoderOptions} for these options.
     *
     * @return the {@link BAMDecoderOptions} for these options
     */
    public BAMDecoderOptions getBAMDecoderOptions() { return bamDecoderOptions; }

    /**
     * Set the {@link BAMDecoderOptions} used for these options.
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
    @InternalAPI
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
    @InternalAPI
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
    @InternalAPI
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
    @InternalAPI
    public ReadsDecoderOptions setIndexChannelTransformer(
            final Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer) {
        this.indexChannelTransformer = indexChannelTransformer;
        return this;
    }

}
