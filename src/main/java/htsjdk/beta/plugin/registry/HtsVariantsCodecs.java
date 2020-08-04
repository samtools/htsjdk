package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodecVersion;
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

import java.util.Optional;

public class HtsVariantsCodecs {
    private static HtsCodecsByFormat<VariantsFormat, VariantsCodec> variantCodecs = HtsCodecRegistry.getVariantCodecs();

    @SuppressWarnings("unchecked")
    public static VariantsDecoder getVariantsDecoder(final IOPath inputPath) {
        return getVariantsDecoder(inputPath, new VariantsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static VariantsDecoder getVariantsDecoder(
            final IOPath inputPath,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final Bundle inputBundle = BundleBuilder.start()
                .addPrimary(new IOPathResource(inputPath, BundleResourceType.VARIANTS))
                .getBundle();
        return getVariantsDecoder(inputBundle, variantsDecoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static VariantsDecoder getVariantsDecoder(
            final Bundle inputBundle,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final VariantsCodec readsCodec = variantCodecs.resolveCodecForInput(
                inputBundle,
                BundleResourceType.VARIANTS,
                VariantsFormat::mapContentSubTypeToVariantsFormat);
        return (VariantsDecoder) readsCodec.getDecoder(inputBundle, variantsDecoderOptions);
    }

    public static VariantsEncoder getVariantsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");
        return getVariantsEncoder(outputPath, new VariantsEncoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static VariantsEncoder getVariantsEncoder(
            final IOPath outputPath,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(variantsEncoderOptions, "Encoder options must not be null");

        final Bundle outputBundle = BundleBuilder.start()
                .addPrimary(new IOPathResource(outputPath, BundleResourceType.VARIANTS))
                .getBundle();
        return (VariantsEncoder) variantCodecs.resolveCodecForOutput(
                outputBundle,
                BundleResourceType.VARIANTS,
                Optional.empty(),
                VariantsFormat::mapContentSubTypeToVariantsFormat).getEncoder(outputBundle, variantsEncoderOptions);
    }

    //TODO: this needs to have VariantsEncoderOptions
    @SuppressWarnings("unchecked")
    public static VariantsEncoder getVariantsEncoder(
            final IOPath outputPath,
            final VariantsFormat variantsFormat,
            final HtsCodecVersion codecVersion) {
        ValidationUtils.nonNull(outputPath, "Output path must not be null");
        ValidationUtils.nonNull(variantsFormat, "Format must not be null");
        ValidationUtils.nonNull(codecVersion, "Codec version must not be null");

        final Bundle outputBundle = BundleBuilder.start()
                .addPrimary(new IOPathResource(outputPath, BundleResourceType.VARIANTS))
                .getBundle();
        final VariantsCodec variantCodec = variantCodecs.getCodecForFormatAndVersion(variantsFormat, codecVersion);
        return (VariantsEncoder) variantCodec.getEncoder(outputBundle, new VariantsEncoderOptions());
    }

}
