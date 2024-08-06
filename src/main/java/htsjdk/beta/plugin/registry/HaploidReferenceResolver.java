package htsjdk.beta.plugin.registry;

import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoderOptions;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.reference.ReferenceSequenceFileFactory;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

/**
 * Class with methods for resolving inputs and outputs to haploid reference encoders and decoders.
 * <p>
 * Provides a typesafe layer over the {@link HtsCodecResolver} used by an {@link HtsCodecRegistry}
 * to manage {@link HaploidReferenceCodec}s (see {@link HtsCodecRegistry#getHaploidReferenceResolver()}).
 * <p>
 * Provides typesafe conversion of argument and return types to types that conform to those used by
 * {@link HaploidReferenceCodec}s, such as {@link htsjdk.beta.plugin.hapref.HaploidReferenceDecoder} and
 * {@link htsjdk.beta.plugin.hapref.HaploidReferenceEncoder}.
 */
public class HaploidReferenceResolver extends HtsCodecResolver<HaploidReferenceCodec> {

    /**
     * Create a new HaploidReferenceResolver.
     */
    public HaploidReferenceResolver() {
        super(BundleResourceType.CT_HAPLOID_REFERENCE);
    }

    /**
     * Get a {@link HaploidReferenceDecoder} for the given inputPath.
     *
     * @param inputPath the path to the resource to be decoded
     * @return a HaploidReferenceDecoder for the given inputPath
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public HaploidReferenceDecoder getHaploidReferenceDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "inputPath");

        return getHaploidReferenceDecoder(inputPath, new HaploidReferenceDecoderOptions());
    }

    /**
     * Get a {@link HaploidReferenceDecoder} suitable for decoding {@code inputPath} using options in
     * {@code HaploidReferenceDecoderOptions}. The {@code inputPath} is inspected to determine the appropriate
     * file format/version. The index is automatically resolved. To bypass index resolution, use
     * {@link #getHaploidReferenceDecoder}.
     *
     * @param inputPath the IOPath to be decoded
     * @param HaploidReferenceDecoderOptions options to use
     * @return a {@link HaploidReferenceDecoder} suitable for decoding {@code inputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public HaploidReferenceDecoder getHaploidReferenceDecoder(
            final IOPath inputPath,
            final HaploidReferenceDecoderOptions HaploidReferenceDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(HaploidReferenceDecoderOptions, "Decoder options");

        final Bundle referenceBundle = referenceBundleFromFastaPath(inputPath, HtsPath::new);
        return getHaploidReferenceDecoder(referenceBundle, HaploidReferenceDecoderOptions);
    }

    /**
     * Get a {@link HaploidReferenceDecoder} for the given input Bundle.
     *
     * @param inputBundle the path to the bundle containing the resource to be decoded
     * @return a HaploidReferenceDecoder for the given inputPath
     *
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public HaploidReferenceDecoder getHaploidReferenceDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "inputPath");

        return getHaploidReferenceDecoder(inputBundle, new HaploidReferenceDecoderOptions());
    }

    /**
     * Get a {@link HaploidReferenceDecoder} suitable for decoding {@code inputBundle} using options in
     * {@code HaploidReferenceDecoderOptions}. The {@code inputBundle} is inspected to determine the appropriate
     * file format/version.
     *
     * @param inputBundle the bundle to be decoded
     * @param HaploidReferenceDecoderOptions {@link HaploidReferenceDecoderOptions} options to be used by the decoder
     * @return a {@link HaploidReferenceDecoder} suitable for decoding {@code inputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public HaploidReferenceDecoder getHaploidReferenceDecoder(
            final Bundle inputBundle,
            final HaploidReferenceDecoderOptions HaploidReferenceDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(HaploidReferenceDecoderOptions, "Decoder options");

        return (HaploidReferenceDecoder) resolveForDecoding(inputBundle).getDecoder(inputBundle, HaploidReferenceDecoderOptions);
    }

    /**
     * Create q reference bundle given only a fasta path, including an index and a dictionary
     * file if they are present and located in the same directory as the fasta.
     *
     * @param fastaPath location of the fasta
     * @param ioPathConstructor a constructor used to create IOPath-derived objects for the bundle
     * @return a reference Bundle
     * @param <T>
     */
    public static <T extends IOPath> Bundle referenceBundleFromFastaPath(final IOPath fastaPath, final Function<String, T> ioPathConstructor)  {
        final BundleBuilder referenceBundleBuilder = new BundleBuilder();
        referenceBundleBuilder.addPrimary(new IOPathResource(fastaPath, BundleResourceType.CT_HAPLOID_REFERENCE));

        final Path dictPath = ReferenceSequenceFileFactory.getDefaultDictionaryForReferenceSequence(fastaPath.toPath());
        if (Files.exists(dictPath)) {
            referenceBundleBuilder.addSecondary(
                    new IOPathResource(
                            ioPathConstructor.apply(dictPath.toUri().toString()),
                            BundleResourceType.CT_REFERENCE_DICTIONARY));
        }

        final Path idxPath = ReferenceSequenceFileFactory.getFastaIndexFileName(fastaPath.toPath());
        if (Files.exists(idxPath)) {
            referenceBundleBuilder.addSecondary(
                    new IOPathResource(
                            ioPathConstructor.apply(idxPath.toUri().toString()),
                            BundleResourceType.CT_REFERENCE_INDEX));
        }

        try {
            if (IOUtil.isBlockCompressed(fastaPath.toPath(), true)) {
                final Path gziPath = GZIIndex.resolveIndexNameForBgzipFile(fastaPath.toPath());
                referenceBundleBuilder.addSecondary(
                        new IOPathResource(
                                ioPathConstructor.apply(gziPath.toUri().toString()),
                                BundleResourceType.CT_REFERENCE_INDEX_GZI));
            }
        } catch (IOException e) {
            throw new HtsjdkException("Error while checking for block compression", e);
        }
        return referenceBundleBuilder.build();
    }

}
