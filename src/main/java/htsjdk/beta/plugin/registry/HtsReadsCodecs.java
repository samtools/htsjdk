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

//TODO: document that the non-Bundle overloads resolve the index automatically, but the Bundle ones do not,
// per the protocol that says that a bundles are taken as-is
//TODO: should we add an argument that controls index resolution for non-bundles APIs ?

/**
 * Class with methods for resolving inputs and outputs to reads encoders and decoders.
 *
 * Provides typesafe access layer over the {@link HtsCodecResolver} thats used by
 * the {@link HtsCodecRegistry} to manage reads codecs (see {@link HtsCodecRegistry#getReadsCodecResolver()}).
 * It exposes methods that accept common types, such as IOPath, with automatic conversion to types
 * appropriate for reads such as {@link ReadsBundle}, and argument and return types that conform to
 * those used by {@link htsjdk.beta.plugin.reads.ReadsCodec}s, such as
 * {@link htsjdk.beta.plugin.reads.ReadsEncoder},
 * {@link htsjdk.beta.plugin.reads.ReadsEncoder}, and
 * {@link htsjdk.beta.plugin.reads.ReadsDecoderOptions}.
 */
public class HtsReadsCodecs {

    HtsReadsCodecs() {}

    //***********************
    // Decoders

    /**
     * Return a {@link ReadsDecoder} suitable for decoding {@code inputPath}. The {@code inputPath} is
     * inspected to determine the appropriate file format/version.
     *
     * @param inputPath the IOPath to be decoded
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputPath}
     */
    public static ReadsDecoder getReadsDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "Input path");
        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), new ReadsDecoderOptions());
    }

    /**
     * Return a {@link ReadsDecoder} suitable for decoding {@code inputPath} using options in
     * {@code readsDecoderOptions}. The {@code inputPath} is inspected to determine the appropriate
     * file format/version.
     *
     * @param inputPath the IOPath to be decoded
     * @param readsDecoderOptions options to use
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputPath}
     */
    public static ReadsDecoder getReadsDecoder(
            final IOPath inputPath,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options");
        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), readsDecoderOptions);
    }

    /**
     * Return a {@link ReadsDecoder} suitable for decoding {@code inputBundle}. The {@code inputBundle} is
     * inspected to determine the appropriate file format/version.
     *
     * @param inputBundle the bundle to be decoded
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputBundle}
     */
    public static ReadsDecoder getReadsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        return getReadsDecoder(inputBundle, new ReadsDecoderOptions());
    }

    /**
     * Return a {@link ReadsDecoder} suitable for decoding {@code inputBundle} using options in
     * {@code readsDecoderOptions}. The {@code inputBundle} is inspected to determine the appropriate
     * file format/version.
     *
     * @param inputBundle the bundle to be decoded
     * @param readsDecoderOptions {@link ReadsDecoderOptions} options to be used by the decoder
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputBundle}
     */
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

    /**
     * Return a {@link ReadsEncoder} suitable for encoding to {@code outputPath}. The path must include
     * a file extension suitable for determining the appropriate file format to use; the newest version
     * of the file format available will be used. To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, ReadsFormat, HtsVersion)}.
     *
     * @param outputPath the IOPath target for encoding
     * @return a {@link ReadsEncoder} suitable for encoding to {@code outputPath}
     */
    public static ReadsEncoder getReadsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");
        return getReadsEncoder(outputPath, new ReadsEncoderOptions());
    }

    /**
     * Return a {@link ReadsEncoder} suitable for encoding to {@code outputPath}, using the options
     * in {@code readsEncoderOptions}. The path must include a file extension suitable for determining
     * the appropriate file format to use; the newest version of the file format available will be used.
     * To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, ReadsFormat, HtsVersion)}.
     *
     * @param outputPath target path to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} options to be used by the encoder
     * @return
     */
    public static ReadsEncoder getReadsEncoder(
            final IOPath outputPath,
            final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options");

        return getReadsEncoder(new ReadsBundle(outputPath), readsEncoderOptions);
    }

    /**
     * Return a {@link ReadsEncoder} suitable for encoding to {@code outputBundle}, using the options
     * in {@code readsEncoderOptions}. The outputBundle must include a primary resource with a file
     * extension suitable for determining the appropriate file format to use; or the resource must include
     * a subContentType. The newest version of the selected file format available will be used.
     * To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, ReadsFormat, HtsVersion)}.
     *
     * @param outputBundle target output to encode to
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to be used by the encoder
     * @return
     */
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

    /**
     * Return a {@link ReadsEncoder} suitable for encoding to {@code outputBundle}, using the options
     * in {@code readsEncoderOptions}, and the file format and version specified in readsFormat and formatVersion.
     *
     * @param outputBundle target output bundle
     * @param readsEncoderOptions encode options to be used by the encoder
     * @param readsFormat target file format
     * @param formatVersion target file format version
     * @return {@link ReadsEncoder} suitable for encoding to {@code outputBundle}
     */
    @SuppressWarnings("unchecked")
    public static ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions,
            final ReadsFormat readsFormat,
            final HtsVersion formatVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(readsFormat, "Reads format");
        ValidationUtils.nonNull(formatVersion, "File format version");

        return (ReadsEncoder) HtsCodecRegistry.getReadsCodecResolver()
                .resolveForFormatAndVersion(readsFormat, formatVersion)
                .getEncoder(outputBundle, readsEncoderOptions);
    }

}


