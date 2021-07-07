package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.variants.VariantsCodec;
import htsjdk.beta.plugin.variants.VariantsDecoder;
import htsjdk.beta.plugin.variants.VariantsDecoderOptions;
import htsjdk.beta.plugin.variants.VariantsEncoder;
import htsjdk.beta.plugin.variants.VariantsEncoderOptions;
import htsjdk.beta.plugin.variants.VariantsFormat;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

/**
 * Class with methods for resolving inputs and outputs to variants encoders and decoders.
 * <p>
 * Provides a convenient typesafe layer over the {@link HtsCodecResolver} thats used by an
 * {@link HtsCodecRegistry} to manage {@link VariantsCodec}s
 * (see {@link HtsCodecRegistry#getHaploidReferenceResolver()}).
 * <p>
 * Provides typesafe conversion of argument and return types to types that conform to those
 * used with {@link VariantsCodec}s, such as {@link VariantsDecoder}, {@link VariantsEncoder},
 * {@link htsjdk.beta.plugin.variants.VariantsDecoderOptions}.
 */
public class VariantsResolver extends HtsCodecResolver<VariantsFormat, VariantsCodec> {

    /**
     * Create a new VariantsResolver.
     */
    public VariantsResolver() {
        super(BundleResourceType.VARIANT_CONTEXTS, VariantsFormat.VCF);
    }

    public VariantsDecoder getVariantsDecoder(final IOPath inputPath) {
        return getVariantsDecoder(inputPath, new VariantsDecoderOptions());
    }

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

    @SuppressWarnings("unchecked")
    public VariantsDecoder getVariantsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");

        final VariantsCodec variantsCodec = resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, new VariantsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public VariantsDecoder getVariantsDecoder(
            final Bundle inputBundle,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final VariantsCodec variantsCodec = resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, variantsDecoderOptions);
    }

    public VariantsEncoder getVariantsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");
        return getVariantsEncoder(outputPath, new VariantsEncoderOptions());
    }

    public VariantsEncoder getVariantsEncoder(
            final IOPath outputPath,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(variantsEncoderOptions, "Encoder options must not be null");

        final Bundle outputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(outputPath, BundleResourceType.VARIANT_CONTEXTS))
                .build();
        return getVariantsEncoder(outputBundle, new VariantsEncoderOptions());
    }

    public VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        final VariantsCodec variantsCodec = resolveForEncoding(outputBundle);
        return (VariantsEncoder) variantsCodec.getEncoder(outputBundle, variantsEncoderOptions);
    }

    @SuppressWarnings("unchecked")
    public VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions,
            final VariantsFormat variantsFormat,
            final HtsVersion codecVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(variantsFormat, "Format");
        ValidationUtils.nonNull(codecVersion, "Codec version");

        return (VariantsEncoder) resolveForFormatAndVersion(variantsFormat, codecVersion)
                .getEncoder(outputBundle, variantsEncoderOptions);
    }

}
