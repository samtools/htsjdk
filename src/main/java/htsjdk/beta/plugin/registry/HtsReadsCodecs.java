package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

//        getReadsDecoder(Bundle)
//        getReadsDecoder(Bundle, ReadsDecoderOptions)
//        getReadsEncoder(Bundle)
//        getReadsEncoder(Bundle, ReadsEncoderOptions)
//        getReadsEncoder(Bundle, ReadsEncoderOptions, HtsCodecVersion)

public class HtsReadsCodecs {
    private static HtsCodecsByFormat<ReadsFormat, ReadsCodec> readsCodecs = HtsCodecRegistry.getReadsCodecs();

    HtsReadsCodecs() {}

    //***********************
    // Decoders

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "Input path");
        return getReadsDecoder(new ReadsBundle(inputPath), new ReadsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(
            final IOPath inputPath,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options must not be null");
        return getReadsDecoder(new ReadsBundle(inputPath), readsDecoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        return getReadsDecoder(inputBundle, new ReadsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(
            final Bundle inputBundle,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options");

        final ReadsCodec readsCodec = readsCodecs.resolveCodecForInput(
                inputBundle,
                BundleResourceType.READS,
                ReadsFormat::mapContentSubTypeToFormat);
        return (ReadsDecoder) readsCodec.getDecoder(inputBundle, readsDecoderOptions);
    }

    //***********************
    // Encoders

    @SuppressWarnings("unchecked")
    public static ReadsEncoder getReadsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");
        return getReadsEncoder(outputPath, new ReadsEncoderOptions());
    }

    public static ReadsEncoder getReadsEncoder(
            final IOPath outputPath,
            final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options");

        return getReadsEncoder(new ReadsBundle(outputPath), readsEncoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options ");

        final ReadsCodec readsCodec = readsCodecs.resolveCodecForOutput(
                outputBundle,
                BundleResourceType.READS,
                Optional.empty(),           // no requested version
                ReadsFormat::mapContentSubTypeToFormat);

        return (ReadsEncoder) readsCodec.getEncoder(outputBundle, readsEncoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions,
            final ReadsFormat readsFormat,
            final HtsCodecVersion codecVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(readsFormat, "Codec format");
        ValidationUtils.nonNull(codecVersion, "Codec version");

        final ReadsCodec readsCodec = readsCodecs.getCodecForFormatAndVersion(readsFormat, codecVersion);
        return (ReadsEncoder) readsCodec.getEncoder(outputBundle, readsEncoderOptions);
    }

}


