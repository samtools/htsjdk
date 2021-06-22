package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

//        getReadsDecoder(Bundle)
//        getReadsDecoder(Bundle, ReadsDecoderOptions)
//        getReadsEncoder(Bundle)
//        getReadsEncoder(Bundle, ReadsEncoderOptions)
//        getReadsEncoder(Bundle, ReadsEncoderOptions, HtsCodecVersion)

//TODO: document that the non-Bundle overloads resolve the index automatically, but the Bundle ones do not,
// per the protocol that says that a bundles are taken as-is
//TODO: should we add an argument that controls index resolution for non-bundles APIs ?
public class HtsReadsCodecs {

    HtsReadsCodecs() {}

    //***********************
    // Decoders

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "Input path");
        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), new ReadsDecoderOptions());
    }

    @SuppressWarnings("unchecked")
    public static ReadsDecoder getReadsDecoder(
            final IOPath inputPath,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options");
        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), readsDecoderOptions);
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

        return (ReadsDecoder) HtsCodecRegistry.getReadsCodecResolver()
                .resolveForDecoding(inputBundle)
                .getDecoder(inputBundle, readsDecoderOptions);
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
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options");

        return (ReadsEncoder) HtsCodecRegistry.getReadsCodecResolver()
                .resolveForEncoding(outputBundle)
                .getEncoder(outputBundle, readsEncoderOptions);
    }

    @SuppressWarnings("unchecked")
    public static ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions,
            final ReadsFormat readsFormat,
            final HtsVersion codecVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(readsFormat, "Codec format");
        ValidationUtils.nonNull(codecVersion, "Codec version");

        return (ReadsEncoder) HtsCodecRegistry.getReadsCodecResolver()
                .resolveFormatAndVersion(readsFormat, codecVersion)
                .getEncoder(outputBundle, readsEncoderOptions);
    }

}


