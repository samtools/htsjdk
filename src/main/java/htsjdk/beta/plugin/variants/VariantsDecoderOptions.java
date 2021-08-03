package htsjdk.beta.plugin.variants;

import htsjdk.beta.plugin.HtsDecoderOptions;
import htsjdk.annotations.InternalAPI;

import java.nio.channels.SeekableByteChannel;
import java.util.Optional;
import java.util.function.Function;

public class VariantsDecoderOptions implements HtsDecoderOptions {
    //TODO: replace these with a prefetch size args, and use a local channel wrapper implementation
    private Function<SeekableByteChannel, SeekableByteChannel> variantsChannelTransformer;
    private Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer;

    // Temporary channel wrapper implementation.

    /**
     * Get the channel transformer for the variants resource. The channel transformer can be used to implement
     * prefetching for the variants input.
     *
     * @return the channel transformer for the reads resource if present, otherwise Optional.empty()
     */
    @InternalAPI
    public Optional<Function<SeekableByteChannel, SeekableByteChannel>> getVariantsChannelTransformer() {
        return Optional.ofNullable(variantsChannelTransformer);
    }

    /**
     * Set the channel transformer for the variants resource. The channel transformer can be used to implement
     * prefetching for the variants input.
     *
     * @param variantsChannelTransformer the channel transformer to be used. may be null
     * @return updated VariantsDecoderOptions
     */
    @InternalAPI
    public VariantsDecoderOptions setVariantsChannelTransformer(
            final Function<SeekableByteChannel, SeekableByteChannel> variantsChannelTransformer) {
        this.variantsChannelTransformer = variantsChannelTransformer;
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
     * @return updated VariantsDecoderOptions
     */
    @InternalAPI
    public VariantsDecoderOptions setIndexChannelTransformer(
            final Function<SeekableByteChannel, SeekableByteChannel> indexChannelTransformer) {
        this.indexChannelTransformer = indexChannelTransformer;
        return this;
    }

}
