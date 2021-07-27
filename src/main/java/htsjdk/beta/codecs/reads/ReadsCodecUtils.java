package htsjdk.beta.codecs.reads;

import htsjdk.beta.codecs.reads.bam.BAMDecoderOptions;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.utils.PrivateAPI;

import java.util.Optional;

/**
 * @PrivateAPI
 *
 * Utilities for use by reads encoder/decoder implementations.
 */
@PrivateAPI
final public class ReadsCodecUtils {

    /**
     * @PrivateAPI
     *
     * Convert an input {@link Bundle} containing reads to a {@link SamInputResource}.
     *
     * @param inputBundle input {@link Bundle} to convert (must contain a reads resource)
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use
     *
     * @return a {@link SamInputResource}
     */
    @PrivateAPI
    public static SamInputResource bundleToSamInputResource(
            final Bundle inputBundle,
            final ReadsDecoderOptions readsDecoderOptions) {
        final SamInputResource samInputResource = readsToSamInputResource(
                inputBundle,
                BundleResourceType.ALIGNED_READS,
                readsDecoderOptions);
        indexToSamInputResource(
                inputBundle,
                BundleResourceType.READS_INDEX,
                readsDecoderOptions,
                samInputResource);
        return samInputResource;
    }

    /**
     * @PrivateAPI
     *
     * Propagate options from a {@link ReadsDecoderOptions} to a SamReaderFactory.
     *
     * @param readsDecoderOptions {@link ReadsDecoderOptions} to use
     * @param samReaderFactory {@link SamReaderFactory}
     */
    @PrivateAPI
    public static void readsDecoderOptionsToSamReaderFactory(
            final ReadsDecoderOptions readsDecoderOptions,
            final SamReaderFactory samReaderFactory) {
        samReaderFactory.validationStringency(readsDecoderOptions.getValidationStringency());
        samReaderFactory.setOption(SamReaderFactory.Option.EAGERLY_DECODE, readsDecoderOptions.isEagerlyDecode());
        samReaderFactory.setOption(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES,
                readsDecoderOptions.isCacheFileBasedIndexes());
        samReaderFactory.setOption(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX,
                readsDecoderOptions.isDontMemoryMapIndexes());
    }

    /**
     * @PrivateAPI
     *
     * Return true if the input {@link Bundle} contains a reads index resource
     *
     * @param inputBundle input {@link Bundle} to inspect
     * @return true if input {@link Bundle} contains a reads index resource
     */
    @PrivateAPI
    public static boolean bundleContainsIndex(final Bundle inputBundle) {
        return inputBundle.get(BundleResourceType.READS_INDEX).isPresent();
    }

    /**
     * @PrivateAPI
     *
     * The stated contract for decoders is that the index must be included in the bundle in order to use
     * index queries, but some codecs use readers that *always* tries to resolve the index, which would
     * violate that and allow some cases to work that shouldn't, so enforce the contract manually so that
     * someday when we use a different implementation, no backward compatibility issue will be introduced.
     *
     * @param inputBundle input {@link Bundle} to inspect
     */
    @PrivateAPI
    public static void assertBundleContainsIndex(final Bundle inputBundle) {
        if (!bundleContainsIndex(inputBundle)) {
            throw new IllegalArgumentException(String.format(
                    "To make index queries, an index resource must be provided in the resource bundle: %s",
                    inputBundle
            ));
        }
    }

    /**
     * Propagate all reads decoder options and all bam decoder options to either a SamReaderFactory
     * or a SamInputResource, and return the resulting SamReader
     */
    @PrivateAPI
    public static SamReader getSamReader(
            final Bundle inputBundle,
            final ReadsDecoderOptions readsDecoderOptions,
            final SamReaderFactory samReaderFactory) {
        // note that some reads decoder options, such as cloud wrapper values, need to be propagated
        // to the samInputResource, not to the SamReaderFactory
        final SamInputResource samInputResource =
                ReadsCodecUtils.bundleToSamInputResource(inputBundle, readsDecoderOptions);
        ReadsCodecUtils.readsDecoderOptionsToSamReaderFactory(readsDecoderOptions, samReaderFactory);
        bamDecoderOptionsToSamReaderFactory(samReaderFactory, readsDecoderOptions.getBAMDecoderOptions());

        return samReaderFactory.open(samInputResource);
    }

    @PrivateAPI
    public static void bamDecoderOptionsToSamReaderFactory(
            final SamReaderFactory samReaderFactory,
            final BAMDecoderOptions bamDecoderOptions) {
        samReaderFactory.inflaterFactory(bamDecoderOptions.getInflaterFactory());
        samReaderFactory.setUseAsyncIo(bamDecoderOptions.isUseAsyncIO());
        samReaderFactory.setOption(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS,
                bamDecoderOptions.isValidateCRCChecksums());
    }

    // convert an input bundle to a SamInputResource
    private static SamInputResource readsToSamInputResource(
            final Bundle inputBundle,
            final String contentType,
            final ReadsDecoderOptions readsDecoderOptions) {
        final BundleResource readsInput = inputBundle.getOrThrow(contentType);
        if (!readsInput.hasInputType()) {
            throw new IllegalArgumentException(String.format(
                    "The provided %s resource (%s) must be a readable/input resource", contentType, readsInput));
        }

        if (readsInput.hasSeekableStream()) {
            if (readsInput.getIOPath().isPresent()) {
                if (readsDecoderOptions.getReadsChannelTransformer().isPresent()) {
                    //TODO: use a local cloud channel wrapper instead of requiring the user to pass a lambda
                    return SamInputResource.of(readsInput.getIOPath().get().toPath(),
                            readsDecoderOptions.getReadsChannelTransformer().get());
                } else {
                    return SamInputResource.of(readsInput.getIOPath().get().toPath());
                }
            } else if (readsInput.getSeekableStream().isPresent()) {
                return SamInputResource.of(readsInput.getSeekableStream().get());
            }
        }
        return SamInputResource.of(readsInput.getInputStream().get());
    }

    // add the index from the bundle to the SamInputResource, if there is one
    private static void indexToSamInputResource(
            final Bundle inputBundle,
            final String contentType,
            final ReadsDecoderOptions readsDecoderOptions,
            final SamInputResource samInputResource) {
        final Optional<BundleResource> indexInput = inputBundle.get(contentType);
        if (indexInput.isPresent()) {
            final BundleResource indexResource = indexInput.get();
            if (indexResource.getIOPath().isPresent()) {
                if (indexResource.getIOPath().isPresent()) {
                    if (readsDecoderOptions.getIndexChannelTransformer().isPresent()) {
                        //TODO: use a local cloud channel wrapper instead of requiring the user to pass a lambda
                        SamInputResource.of(indexResource.getIOPath().get().toPath(),
                                readsDecoderOptions.getIndexChannelTransformer().get());
                        samInputResource.index(indexResource.getIOPath().get().toPath());
                    } else if (indexResource.getSeekableStream().isPresent()) {
                        samInputResource.index(indexResource.getSeekableStream().get());
                    } else if (indexResource.getInputStream().isPresent()) {
                        samInputResource.index(indexResource.getInputStream().get());
                    }
                }
            }
        }
    }

}
