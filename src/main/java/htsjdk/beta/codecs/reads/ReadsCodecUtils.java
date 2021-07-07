package htsjdk.beta.codecs.reads;

import htsjdk.beta.codecs.reads.bam.BAMDecoderOptions;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.SamInputResource;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.utils.PrivateAPI;

import java.util.Optional;

// Shareable (and sometimes temporary) utilities for use with private local reads encoder/decoder implementations
@PrivateAPI
final public class ReadsCodecUtils {

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

    // Propagate all readsDecoderOptions, except BAM and CRAM specific options, which are propagated separately,
    // and cloud wrappers, since those are not SamReaderFactory options, but rather are added when the Sam
    // InputResource is created.
    @PrivateAPI
    public static void readsDecoderOptionsToSamReaderFactory(
            final SamReaderFactory samReaderFactory,
            final ReadsDecoderOptions readsDecoderOptions) {
        samReaderFactory.validationStringency(readsDecoderOptions.getValidationStringency());
        samReaderFactory.setOption(SamReaderFactory.Option.EAGERLY_DECODE, readsDecoderOptions.isEagerlyDecode());
        samReaderFactory.setOption(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES,
                readsDecoderOptions.isCacheFileBasedIndexes());
        samReaderFactory.setOption(SamReaderFactory.Option.DONT_MEMORY_MAP_INDEX,
                readsDecoderOptions.isDontMemoryMapIndexes());
    }

    // Propagate all BAMDecoderOptions to the samReaderFactory.
    @PrivateAPI
    public static void bamDecoderOptionsToSamReaderFactory(
            final SamReaderFactory samReaderFactory,
            final BAMDecoderOptions bamDecoderOptions) {
        samReaderFactory.inflaterFactory(bamDecoderOptions.getInflaterFactory());
        samReaderFactory.setOption(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS,
                bamDecoderOptions.isIncludeSourceInRecords());
        samReaderFactory.setUseAsyncIo(bamDecoderOptions.isUseAsyncIO());
        samReaderFactory.setOption(SamReaderFactory.Option.VALIDATE_CRC_CHECKSUMS,
                bamDecoderOptions.isValidateCRCChecksums());
    }

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
                if (readsDecoderOptions.getDataWrapper().isPresent()) {
                    //TODO: use a local cloud channel wrapper instead of requiring the user to pass a lambda
                    return SamInputResource.of(readsInput.getIOPath().get().toPath(),
                            readsDecoderOptions.getDataWrapper().get());
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
                    if (readsDecoderOptions.getIndexWrapper().isPresent()) {
                        //TODO: use a local cloud channel wrapper instead of requiring the user to pass a lambda
                        SamInputResource.of(indexResource.getIOPath().get().toPath(),
                                readsDecoderOptions.getIndexWrapper().get());
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
