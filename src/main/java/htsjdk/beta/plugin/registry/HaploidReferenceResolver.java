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
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

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
        super(BundleResourceType.HAPLOID_REFERENCE);
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

        final Bundle referenceBundle = new BundleBuilder().addPrimary(
                new IOPathResource(inputPath, BundleResourceType.HAPLOID_REFERENCE)).build();

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

}
