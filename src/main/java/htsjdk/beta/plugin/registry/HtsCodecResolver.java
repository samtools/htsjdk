package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.exception.HtsjdkException;
import htsjdk.exception.HtsjdkPluginException;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.Log;
import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class used to resolve an input or output resource to an appropriate codec (encoder/decoder) for a
 * single codec type. Methods in this class accept a bundle, along with some auxiliary arguments,
 * and return one or more codecs appropriate for encoding or decoding the input.
 *
 * @param <F> enum representing the possible formats for this codec type
 * @param <C> the HtsCodec type
 */
final class HtsCodecResolver<F extends Enum<F>, C extends HtsCodec<F, ?, ?>> {
    private static final Log LOG = Log.getInstance(HtsCodecResolver.class);

    final static String NO_SUPPORTING_CODEC_ERROR = "No registered codec accepts the provided resource";
    final static String MULTIPLE_SUPPORTING_CODECS_ERROR = "Multiple codecs accept the provided resource";

    private final String requiredContentType;
    private final Map<F, Map<HtsVersion, C>> codecs = new HashMap<>();
    private final Function<String, Optional<F>> contentSubTypeToFormat;

    /**
     * Create a resolver for a given codec type.
     *
     * @param requiredContentType the primary content type this resolver will use to interrogate a bundle
     *                            to locate the primary resource when attempting to resolve the bundle to a codec
     * @param contentSubTypeToFormat a mapping function that takes a String contentSubType and returns
     *                              the corresponding format, if one exists
     */
    public HtsCodecResolver(
            final String requiredContentType,
            final Function<String, Optional<F>> contentSubTypeToFormat) {
        this.requiredContentType = requiredContentType;
        this.contentSubTypeToFormat = contentSubTypeToFormat;
    }

    /**
     * Register a codec of type {@link C} for file format {@link F}. If a codec for the same
     * format and version is already registered, the registry is updated with the new codec,
     * and the previously registered codec is returned.
     *
     * @param codec a codec of type {@link C} for file format {@link F}
     * @return the previously registered codec for the same format and version, or null if no
     * codec was previously registered
     */
    public C registerCodec(final C codec) {
        final F fileFormat = codec.getFileFormat();
        final Map<HtsVersion, C> versionMap = codecs.get(fileFormat);
        if (versionMap == null) {
            // first codec for this format
            final Map<HtsVersion, C> newMap = new HashMap<>();
            newMap.put(codec.getVersion(), codec);
            codecs.put(fileFormat, newMap);
            return null;
        } else {
            // update the version map for this codec
            final C oldCodec = versionMap.get(codec.getVersion());
            versionMap.put(codec.getVersion(), codec);
            if (oldCodec != null) {
                LOG.warn(String.format("A previously registered HTS codec (%s) was replaced with the (%s) codec ",
                        oldCodec.getDisplayName(),
                        codec.getDisplayName()));
            }
            return oldCodec;
        }
    }

    /**
     * Inspect a bundle and find a codec that can decode the primary resource.
     *
     * @param bundle the bundle to resolve to a codec
     * @return a codec that can decode the bundle resources
     *
     * @throws RuntimeException if more than one codec matches. this usually indicates a registered codec
     * that is poorly behaved.
     */
    public C resolveForDecoding(final Bundle bundle) {
        ValidationUtils.nonNull(bundle, "bundle");

        final BundleResource bundleResource = getPrimaryResource(bundle, true);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource);
        final List<C> candidatesCodecs = resolveFormat(optFormat);

        final List<C> resolvedCodecs = bundleResource.getIOPath().isPresent() ?
                resolveForDecodingIOPath(bundleResource, candidatesCodecs) :
                resolveForDecodingStream(bundleResource, candidatesCodecs);

        return getOneOrThrow(resolvedCodecs,
                () -> String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "(NONE)",
                        bundleResource));
    }

    /**
     * Inspect a bundle and find a codec that can encode content based on the primary resource.
     *
     * @param bundle the bundle to resolve to an encoding codec. for bundles with a primary content
     *               type that is an IOPath, the structure of the IOPath (protocol scheme, file extension,
     *               and query parameters) are used to determine the file format used. For resources
     *               that are ambiguous (i.e., a stream, which has no file extension), the bundle resource
     *               must include a subContentType that corresponds to one of the formats for the content
     *               type used by this codec type. The newest version of the file format will be used.
     *               To request a specific version or format, see {@link #resolveForEncoding(Bundle, HtsVersion)}
     *               or {@link #resolveFormatAndVersion(Enum, HtsVersion)}.
     * @return a codec that can decode the input bundle
     *
     * @throws RuntimeException if more than one codec matches. this usually indicates a registered codec
     * that is poorly behaved.
     */
    public C resolveForEncoding(final Bundle bundle) { return resolveForEncoding(bundle, HtsVersion.NEWEST); }

    /**
     * Find a codec that can read the contentType required for this bundle. For encoding, the output
     * format is derived primary bundle resource. For streams, the primary resources must in include a
     * subcontent type that specifies the output format.
     *
     * NOTE: If an output stream resources is provided as the target output, the bundle resource must include
     * a file format, otherwise multiple codecs will accept the bundle, and an exception will be thrown.
     */
    public C resolveForEncoding(final Bundle bundle, final HtsVersion htsVersion) {
        ValidationUtils.nonNull(bundle, "bundle");
        ValidationUtils.nonNull(htsVersion, "htsVersion");

        final BundleResource bundleResource = getPrimaryResource(bundle, false);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource);
        final List<C> candidateCodecs = resolveFormat(optFormat);

        // If the resource is an IOPath resource, see if any codec(s) claim ownership of the URI,
        // specifically the protocol scheme.
        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> filteredCodecs = bundleResource.getIOPath().isPresent() ?
                resolveForEncodingIOPath(ioPath.get(), candidateCodecs) :
                candidateCodecs; // there isn't anything else to probe when the output is to a stream
        final List<C> resolvedCodecs = filterByVersion(filteredCodecs, htsVersion);

        return getOneOrThrow(resolvedCodecs,
                () ->  String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "(NONE)",
                        bundleResource));
    }

    public List<C> resolveFormat(final F rf) {
        final Map<HtsVersion, C> allCodecsForFormat = codecs.get(rf);
        if (allCodecsForFormat != null) {
            return allCodecsForFormat.values().stream().collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    public C resolveFormatAndVersion(final F format, final HtsVersion formatVersion) {
        final List<C> matchingCodecs = resolveFormat(format)
                .stream()
                .filter(codec -> codec.getFileFormat().equals(format) && codec.getVersion().equals(formatVersion))
                .collect(Collectors.toList());
        return getOneOrThrow(matchingCodecs, () -> String.format("%s/%s", format, formatVersion));
    }

    public List<C> getCodecs() {
        // flatten out the codecs into a single list
        final List<C> cList = codecs
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
        return cList;
    }

    @SuppressWarnings("rawtypes")
    private static<T extends HtsCodec<?, ?, ?>> boolean canDecodeIOPathSignature(
            final T codec,
            final BundleResource bundleResource,
            final IOPath inputPath,
            final int streamPrefixSize) {
        if (!inputPath.hasFileSystemProvider()) {
            // We're about to query the input stream to probe for signatures; if the input IOPath has no file
            // system provider, then it probably has a custom protocol scheme, and most likely represents a
            // remote resource. We should never get here since this protocol should have been claimed
            // by some codec's claimURI implementation. Attempting to get an input stream directly from such
            // an IOPath will fail.
            throw new IllegalArgumentException(
                    String.format("The resource (%s) specifies a custom protocol (%s) for which no NIO file system provider is registered",
                            bundleResource,
                            inputPath.getURI().getScheme()));
        }
        return codec.canDecodeSignature(
                bundleResource.getSignatureProbingStream(streamPrefixSize),
                inputPath.getRawInputString());
    }

    private List<C> filterByVersion(final List<C> candidateCodecs, final HtsVersion htsVersion) {
        ValidationUtils.nonNull(htsVersion, "htsVersion");
        if (candidateCodecs.isEmpty()) {
            return candidateCodecs;
        }
        if (htsVersion.equals(HtsVersion.NEWEST)) {
            // find the newest codec version in the list of candidates, and return all the codecs for that
            // version (since there still can be more than one)
            final HtsVersion newestVersion = candidateCodecs.stream()
                    .map(c -> c.getVersion())
                    .reduce(candidateCodecs.get(0).getVersion(),
                            (HtsVersion a, HtsVersion b) -> a.compareTo(b) > 0 ? a : b);
            return candidateCodecs.stream().filter(
                    c -> c.getVersion().equals(newestVersion)).collect(Collectors.toList());
        } else {
            return candidateCodecs.stream()
                    .filter(c -> c.getVersion().equals(htsVersion))
                    .collect(Collectors.toList());
        }
    }

    // Get our initial candidate codec list, either filtered by content subtype if one is present,
    // or otherwise all registered codecs for this codec format.
    private List<C> resolveFormat(final Optional<F> optFormat) {
        final List<C> candidateCodecs =
                optFormat.isPresent() ?
                        resolveFormat(optFormat.get()) :
                        getCodecs();
        return candidateCodecs;
    }

    private List<C> resolveForDecodingIOPath(final BundleResource bundleResource, final List<C> candidateCodecs) {
        ValidationUtils.validateArg(bundleResource.getIOPath().isPresent(), "an IOPath resource is required");
        final IOPath ioPath = bundleResource.getIOPath().get();

        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((c) -> c.claimURI(ioPath))
                .collect(Collectors.toList());
        final boolean isCustomURI = !uriHandlers.isEmpty();

        if (isCustomURI) {
            // If at least one codec claims this resource's URI, prune the candidates to only those
            // codecs that claim it, and short circuit stream signature probing, since likely there
            // is no NIO provider for it, and attempts to get a stream will throw. There really should
            // only ever be one codec that claims this URI (though in theory its possible to have more
            // more than one such that the ambiguity will be resolved via canDecodeURI).
            return uriHandlers.stream()
                    .filter(c -> c.canDecodeURI(ioPath))
                    .collect(Collectors.toList());
        } else {
            // get the largest signature probing stream size across all the remaining candidate codecs,
            // and let the codecs probe the stream for a signature
            final int signatureProbeStreamSize = getSignatureProbeStreamSize(candidateCodecs);
            return candidateCodecs.stream()
                    .filter(c -> canDecodeIOPathSignature(c, bundleResource, ioPath, signatureProbeStreamSize))
                    .collect(Collectors.toList());
        }
    }

    private List<C> resolveForDecodingStream(final BundleResource bundleResource, final List<C> candidateCodecs) {
        if (bundleResource.hasSeekableStream()) {
            // stream is already seekable so no need to wrap it
            throw new HtsjdkPluginException("SeekableStreamResource input resolution is not yet implemented");
        } else {
            final int streamPrefixSize = getSignatureProbeStreamSize(candidateCodecs);
            final SignatureProbingInputStream signatureProbingStream =
                    bundleResource.getSignatureProbingStream(streamPrefixSize);
            return candidateCodecs.stream()
                    .filter(c -> canDecodeInputStreamSignature(
                            c,
                            streamPrefixSize,
                            bundleResource.getDisplayName(),
                            signatureProbingStream))
                    .collect(Collectors.toList());
        }
    }

    private List<C> resolveForEncodingIOPath(final IOPath ioPath, final List<C> candidateCodecs) {
        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((c) -> c.claimURI(ioPath))
                .collect(Collectors.toList());

        // If at least one codec claimed this resource based on a custom URI, prune the candidates to only
        // codecs that also claim it, and don't try to call getInputStream on the resource to check the signature.
        // Instead just let the codecs that claim the URI further process it.
        final List<C> filteredCodecs = uriHandlers.isEmpty() ? candidateCodecs : uriHandlers;

        // reduce our candidates based on uri and IOPath
        return filteredCodecs.stream()
                .filter(c -> c.canDecodeURI(ioPath))
                .collect(Collectors.toList());
    }

    private int getSignatureProbeStreamSize(final List<C> candidateCodecs) {
        return candidateCodecs.stream()
                .map(c -> c.getSignatureProbeStreamSize())
                .reduce(0, (a, b) -> Integer.max(a, b));
    }

    private final BundleResource getPrimaryResource(final Bundle bundle, final boolean forEncoding) {
        final BundleResource bundleResource = bundle.getPrimaryResource();

        // make sure the primary resource matches the content type required for this resolver's codec type
        final String bundlePrimaryContentType = bundle.getPrimaryContentType();
        if (!requiredContentType.equals(bundlePrimaryContentType)) {
            throw new IllegalArgumentException(String.format(
                    "The primary content type (%s) for the resource does not match the requested content type (%s).",
                    bundlePrimaryContentType,
                    requiredContentType));
        }

        // Make sure the resource type is appropriate for encoding or decoding, as requested by the caller
        if (forEncoding && !bundleResource.isInput()) {
            throw new IllegalArgumentException(
                    String.format("The %s resource found (%s) cannot be used as an input resource",
                            requiredContentType,
                            bundleResource));
        } else if (!forEncoding && !bundleResource.isOutput()) { // for decoding
            throw new IllegalArgumentException(
                    String.format("The %s resource found (%s) cannot be used as an output resource",
                            requiredContentType,
                            bundleResource));
        }

        return bundleResource;
    }

    private Optional<F> getFormatForContentSubType(final BundleResource bundleResource) {
        final Optional<String> optContentSubType = bundleResource.getContentSubType();
        final Optional<F> optFormat = optContentSubType.flatMap(
                contentSubType -> contentSubTypeToFormat.apply(contentSubType));
        if (optContentSubType.isPresent() && !optFormat.isPresent()) {
            // throw if the resource contentSubType is present, but doesn't map to any format supported by the content
            throw new IllegalArgumentException(
                    String.format("The sub-content type (%s) found in the bundle resource (%s) does not correspond to any known subtype for content type (%s)",
                            optContentSubType.get(),
                            bundleResource,
                            requiredContentType));
        }
        return optFormat;
    }

    @SuppressWarnings("rawtypes")
    private static<T extends HtsCodec<?, ?, ?>> boolean canDecodeInputStreamSignature(
            final T codec,
            final int streamPrefixSize,
            final String displayName,
            final SignatureProbingInputStream signatureProbingInputStream) {
        signatureProbingInputStream.mark(streamPrefixSize);
        final boolean canDecode = codec.canDecodeSignature(signatureProbingInputStream, displayName);
        signatureProbingInputStream.reset();
        return canDecode;
    }

    //VisibleForTesting
    static <C extends HtsCodec<?, ?, ?>> C getOneOrThrow(
            final List<C> resolvedCodecs,
            final Supplier<String> contextMessage) {
        if (resolvedCodecs.size() == 0) {
            throw new HtsjdkException(String.format(
                    "%s %s",
                    NO_SUPPORTING_CODEC_ERROR,
                    contextMessage.get()));
        } else if (resolvedCodecs.size() > 1) {
            final String multipleCodecsMessage = String.format(
                    "%s (%s)\n%s\nThis indicates an internal error in one or more of the codecs:",
                    MULTIPLE_SUPPORTING_CODECS_ERROR,
                    contextMessage.get(),
                    resolvedCodecs.stream().map(c -> c.getDisplayName()).collect(Collectors.joining("\n")));
            throw new HtsjdkPluginException(multipleCodecsMessage);
        } else {
            return resolvedCodecs.get(0);
        }
    }

}
