package htsjdk.beta.plugin.registry;

import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

//TODO: should we add an arguments/overloads that control index resolution for the non-bundles methods ?

/**
 * Class with methods for resolving inputs and outputs to reads encoders and decoders.
 * <p>
 * Provides a convenient typesafe layer over the {@link HtsCodecResolver} used by an
 * {@link HtsCodecRegistry} to manage {@link ReadsCodec}s
 * (see {@link HtsCodecRegistry#getHaploidReferenceResolver()}).
 * <p>
 * Provides typesafe conversion of argument and return types to types that conform to those used with
 * {@link ReadsCodec}s, such as {@link ReadsDecoder}, {@link ReadsEncoder},
 * {@link htsjdk.beta.plugin.reads.ReadsDecoderOptions}.
 */
public class ReadsResolver extends HtsCodecResolver<ReadsCodec>{

    /**
     * Create a ReadsResolver.
     */
    public ReadsResolver() {
        super(BundleResourceType.ALIGNED_READS);
    }

    /**
     * Get a {@link ReadsDecoder} suitable for decoding {@code inputPath}. The {@code inputPath} is
     * inspected to determine the appropriate file format/version.
     *
     * @param inputPath the IOPath to be decoded
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public ReadsDecoder getReadsDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "Input path");

        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), new ReadsDecoderOptions());
    }

    /**
     * Grt a {@link ReadsDecoder} suitable for decoding {@code inputPath} using options in
     * {@code readsDecoderOptions}. The {@code inputPath} is inspected to determine the appropriate
     * file format/version.
     *
     * @param inputPath the IOPath to be decoded
     * @param readsDecoderOptions options to use
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public ReadsDecoder getReadsDecoder(
            final IOPath inputPath,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputPath, "Input path");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options");

        //TODO: this resolves the index automatically
        return getReadsDecoder(ReadsBundle.resolveIndex(inputPath), readsDecoderOptions);
    }

    /**
     * Get a {@link ReadsDecoder} suitable for decoding {@code inputBundle}. The {@code inputBundle} is
     * inspected to determine the appropriate file format/version.
     *
     * @param inputBundle the bundle to be decoded
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public ReadsDecoder getReadsDecoder(final Bundle inputBundle) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");

        return getReadsDecoder(inputBundle, new ReadsDecoderOptions());
    }

    /**
     * Get a {@link ReadsDecoder} suitable for decoding {@code inputBundle} using options in
     * {@code readsDecoderOptions}. The {@code inputBundle} is inspected to determine the appropriate
     * file format/version.
     *
     * @param inputBundle the bundle to be decoded
     * @param readsDecoderOptions {@link ReadsDecoderOptions} options to be used by the decoder
     * @return a {@link ReadsDecoder} suitable for decoding {@code inputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public ReadsDecoder getReadsDecoder(
            final Bundle inputBundle,
            final ReadsDecoderOptions readsDecoderOptions) {
        ValidationUtils.nonNull(inputBundle, "Input bundle");
        ValidationUtils.nonNull(readsDecoderOptions, "Decoder options");

        return (ReadsDecoder) resolveForDecoding(inputBundle).getDecoder(inputBundle, readsDecoderOptions);
    }

    /**
     * Get a {@link ReadsEncoder} suitable for encoding to {@code outputPath}. The path must include
     * a file extension suitable for determining the appropriate file format to use; the newest version
     * of the file format available will be used. To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, String, HtsVersion)}.
     *
     * @param outputPath the IOPath target for encoding
     * @return a {@link ReadsEncoder} suitable for encoding to {@code outputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public ReadsEncoder getReadsEncoder(final IOPath outputPath) {
        ValidationUtils.nonNull(outputPath, "Output path");

        return getReadsEncoder(outputPath, new ReadsEncoderOptions());
    }

    /**
     * Get a {@link ReadsEncoder} suitable for encoding to {@code outputPath}, using the options
     * in {@code readsEncoderOptions}. The path must include a file extension suitable for determining
     * the appropriate file format to use; the newest version of the file format available will be used.
     * To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, String, HtsVersion)}.
     *
     * @param outputPath target path to encode
     * @param readsEncoderOptions {@link ReadsEncoderOptions} options to be used by the encoder
     * @return {@link ReadsEncoder} suitable for encoding to {@code outputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    public ReadsEncoder getReadsEncoder(
            final IOPath outputPath,
            final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputPath, "Output path");
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options");

        return getReadsEncoder(new ReadsBundle(outputPath), readsEncoderOptions);
    }

    /**
     * Get a {@link ReadsEncoder} suitable for encoding to {@code outputBundle}, using the options
     * in {@code readsEncoderOptions}. The outputBundle must include a primary resource with a file
     * extension suitable for determining the appropriate file format to use; or the resource must include
     * a format. The newest version of the selected file format available will be used.
     * To request a specific file format and/or version, use
     * {@link #getReadsEncoder(Bundle, ReadsEncoderOptions, String, HtsVersion)}.
     *
     * @param outputBundle target output to encode to
     * @param readsEncoderOptions {@link ReadsEncoderOptions} to be used by the encoder
     * @return {@link ReadsEncoder} suitable for encoding to {@code outputPath}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions) {
        ValidationUtils.nonNull(outputBundle, "outputBundle");
        ValidationUtils.nonNull(readsEncoderOptions, "Encoder options");

        return (ReadsEncoder) resolveForEncoding(outputBundle).getEncoder(outputBundle, readsEncoderOptions);
    }

    /**
     * Get a {@link ReadsEncoder} suitable for encoding to {@code outputBundle}, using the options
     * in {@code readsEncoderOptions}, and the file format and version specified in readsFormat and formatVersion.
     *
     * @param outputBundle target output bundle
     * @param readsEncoderOptions encode options to be used by the encoder
     * @param readsFormat target file format
     * @param formatVersion target file format version
     * @return {@link ReadsEncoder} suitable for encoding to {@code outputBundle}
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public ReadsEncoder getReadsEncoder(
            final Bundle outputBundle,
            final ReadsEncoderOptions readsEncoderOptions,
            final String readsFormat,
            final HtsVersion formatVersion) {
        ValidationUtils.nonNull(outputBundle, "Output bundle");
        ValidationUtils.nonNull(readsFormat, "Reads format");
        ValidationUtils.nonNull(formatVersion, "File format version");

        return (ReadsEncoder) resolveFormatAndVersion(readsFormat, formatVersion)
                .getEncoder(outputBundle, readsEncoderOptions);
    }

}


