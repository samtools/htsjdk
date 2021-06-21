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

import java.util.Optional;

public class HtsVariantsCodecs {

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

        final Bundle inputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(inputPath, BundleResourceType.VARIANTS))
                .build();
        return getVariantsDecoder(inputBundle, variantsDecoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static VariantsDecoder getVariantsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");

        final VariantsCodec variantsCodec = HtsCodecRegistry.getVariantsCodecs().resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, new VariantsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static VariantsDecoder getVariantsDecoder(
            final Bundle inputBundle,
            final VariantsDecoderOptions variantsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(variantsDecoderOptions, "Decoder options");

        final VariantsCodec variantsCodec = HtsCodecRegistry.getVariantsCodecs().resolveForDecoding(inputBundle);
        return (VariantsDecoder) variantsCodec.getDecoder(inputBundle, variantsDecoderOptions);
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

        final Bundle outputBundle = new BundleBuilder()
                .addPrimary(new IOPathResource(outputPath, BundleResourceType.VARIANTS))
                .build();
        return getVariantsEncoder(outputBundle, new VariantsEncoderOptions());
    }

    public static VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        final VariantsCodec variantsCodec = HtsCodecRegistry.getVariantsCodecs().resolveForEncoding(outputBundle);
        return (VariantsEncoder) variantsCodec.getEncoder(outputBundle, variantsEncoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static VariantsEncoder getVariantsEncoder(
            final Bundle outputBundle,
            final VariantsEncoderOptions variantsEncoderOptions,
            final VariantsFormat variantsFormat,
            final HtsVersion codecVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(variantsFormat, "Format");
        ValidationUtils.nonNull(codecVersion, "Codec version");

        return (VariantsEncoder) HtsCodecRegistry.getVariantsCodecs()
                .getCodecForFormatAndVersion(variantsFormat, codecVersion)
                .getEncoder(outputBundle, variantsEncoderOptions);
    }

}
