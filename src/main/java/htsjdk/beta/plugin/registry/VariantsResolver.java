package htsjdk.beta.plugin.registry;

import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

/**
 * Class with methods for resolving inputs and outputs to variants encoders and decoders.
 * <p>
 * Provides a convenient typesafe layer over the {@link HtsCodecResolver} used by an
 * {@link HtsCodecRegistry} to manage {@link VariantsCodec}s
 * (see {@link HtsCodecRegistry#getHaploidReferenceResolver()}).
 * <p>
 * Provides typesafe conversion of argument and return types to types that conform to those
 * used with {@link VariantsCodec}s, such as {@link VariantsDecoder}, {@link VariantsEncoder},
 * {@link htsjdk.beta.plugin.variants.VariantsDecoderOptions}.
 */
public class VariantsResolver extends HtsCodecResolver<VariantsCodec> {

    /**
     * Create a VariantsResolver.
     */
    public VariantsResolver() {
        super(BundleResourceType.VARIANT_CONTEXTS);
    }

    /**
     * Get a {@link VariantsDecoder} suitable for decoding {@code inputPath}.
     *
     * @param inputPath the input path to decode
     * @return a {@link VariantsDecoder} suitable for decoding {@code inputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public VariantsDecoder getVariantsDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "Input path");

        return getVariantsDecoder(inputPath, new VariantsDecoderOptions());
    }

    /**
     * Get a {@link VariantsDecoder} suitable for decoding {@code inputPath} using {@code variantsDecoderOptions}.
     *
     * @param inputPath the input path to decode
     * @param variantsDecoderOptions decoder options to use
     * @return a {@link VariantsDecoder} suitable for decoding {@code inputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public VariantsDecoder getVariantsDecoder(
            final IOPath inputPath,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final Bundle inputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(inputPath, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        return getVariantsDecoder(inputBundle, variantsDecoderOptions);
    }

    /**
     * Get a {@link VariantsDecoder} suitable for decoding {@code inputBundle}.
     *
     * @param inputBundle the input bundle containing resources to decode
     * @return a {@link VariantsDecoder} suitable for decoding {@code inputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public VariantsDecoder getVariantsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");

        final VariantsCodec variantsCodec = resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, new VariantsDecoderOptions());
    }

    /**
     * Get a {@link VariantsDecoder} suitable for decoding {@code inputBundle} using {@code variantsDecoderOptions}.
     *
     * @param inputBundle the input bundle to decode
     * @param variantsDecoderOptions decoder options to use
     * @return a {@link VariantsDecoder} suitable for decoding {@code inputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public VariantsDecoder getVariantsDecoder(
            final Bundle inputBundle,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final VariantsCodec variantsCodec = resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, variantsDecoderOptions);
    }

    /**
     * Get a {@link VariantsEncoder} suitable for encoding to {@code outputPath}.
     *
     * @param outputPath path to encode to
     * @return a {@link VariantsEncoder} suitable for encoding to {@code outputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public VariantsEncoder getVariantsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");

        return getVariantsEncoder(outputPath, new VariantsEncoderOptions());
    }

    /**
     * Get a {@link VariantsEncoder} suitable for encoding to {@code outputPath} using {@code variantsEncoderOptions}.
     *
     * @param outputPath path to encode to
     * @param variantsEncoderOptions encoder options to use
     * @return a {@link VariantsEncoder} suitable for encoding to {@code outputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public VariantsEncoder getVariantsEncoder(
            final IOPath outputPath,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(variantsEncoderOptions, "Encoder options");

        final Bundle outputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(outputPath, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        return getVariantsEncoder(outputBundle, variantsEncoderOptions);
    }

    /**
     * Get a {@link VariantsEncoder} suitable for encoding to {@code outputBundle} using {@code variantsEncoderOptions}.
     *
     * @param outputBundle output bundle containg resources to encode to
     * @param variantsEncoderOptions options to use
     * @return a {@link VariantsEncoder} suitable for encoding to {@code outputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(variantsEncoderOptions, "Encoder options");

        final VariantsCodec variantsCodec = resolveForEncoding(outputBundle);
        return (VariantsEncoder) variantsCodec.getEncoder(outputBundle, variantsEncoderOptions);
    }

    /**
     * Get a {@link VariantsEncoder} suitable for encoding to {@code outputBundle} using
     * {@code variantsEncoderOptions}, specifying a version and output format.
     *
     * @param outputBundle output bundle containing resources to encode to
     * @param variantsEncoderOptions options to use
     * @param variantsFormat the output format to use
     * @param formatVersion the format version to use
     * @return a {@link VariantsEncoder} suitable for encoding to {@code outputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions,
            final String variantsFormat,
            final HtsVersion formatVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(variantsEncoderOptions, "Encoder options");
        ValidationUtils.nonNull(variantsFormat, "Format");
        ValidationUtils.nonNull(formatVersion, "Format version");

        return (VariantsEncoder) resolveFormatAndVersion(variantsFormat, formatVersion)
                .getEncoder(outputBundle, variantsEncoderOptions);
    }

}
