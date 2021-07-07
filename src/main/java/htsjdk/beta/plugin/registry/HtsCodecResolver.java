package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsFormat;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.SignatureStream;
import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.Log;
import htsjdk.utils.PrivateAPI;
import htsjdk.utils.ValidationUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Class with methods used to resolve an input or output resource to a codec that can supply an encoder
 * or decoder for that resource. Each resolver manages a single {@link HtsCodec} type, defined by the type
 * parameters used to instantiate the resolver.
 * <p>
 * Methods in this class accept a bundle, and/or additional arguments, and return one or more matching
 * codecs. The resolution methods use a series of probes to inspect resource structure and format
 * to determine the file format and version for the target resource, in order to find codecs that
 * claim to be able to process the resource.
 *</p>
 * @param <F> enum representing the possible formats for this codec type, implements HtsFormat<F>
 * @param <C> the HtsCodec type managed by this resolver
 */
public class HtsCodecResolver<F extends Enum<F> & HtsFormat<F>, C extends HtsCodec<F, ?, ?>> {
    private static final Log LOG = Log.getInstance(HtsCodecResolver.class);

    final static String NO_SUPPORTING_CODEC_ERROR = "No registered codec accepts the provided resource";
    final static String MULTIPLE_SUPPORTING_CODECS_ERROR = "Multiple codecs accept the provided resource";

    private final String requiredContentType;
    private final F htsFormatEnumInstance;
    private final Map<F, Map<HtsVersion, C>> codecs = new HashMap<>();

    /**
     * Create a resolver for a given {@link HtsCodec} type, defined by the type parameters {@code F} and {@code C}.
     *
     * @param requiredContentType the primary content type this resolver will use to interrogate a bundle
     *                            to locate the primary resource when attempting to resolve the bundle to a codec
     * @param htsFormatEnumInstance any instance of the {@code F} enum. the actual instance passed is not
     *                             significant; any instance of the enum {@code F} will work. The value is
     *                             used to access the {@link HtsFormat#formatStringToEnum(String)} method of
     *                             {@link HtsFormat}.
     */
    public HtsCodecResolver(final String requiredContentType, F htsFormatEnumInstance) {
        this.requiredContentType = requiredContentType;
        this.htsFormatEnumInstance = htsFormatEnumInstance;
    }

    /**
     * Register a codec of type {@code C} for file format {@code F}. If a codec for the same
     * format and version is already registered with this resolver, the resolver is updated
     * with the new codec, and the previously registered codec is returned.
     *
     * @param codec a codec of type {@code C} for file format {@code F}
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
     * <p>
     *     The resolution process starts with a list of candidate codecs consisting of all registered codecs
     *     that match this resolver's type. The bundle is then inspected to determine whether the primary resource
     *     is an IOPath or a stream, and the list of candidate codecs is reduced as follows:
     * <p>
     *     If the primary resource is an IOPath:
     *     <ol>
     *         <li>
     *             The IOPath is passed to each candidate codec's {@link HtsCodec#ownsURI} method. If any codec
     *              returns true:
     *              <ol>
     *                  <li>
     *                      The candidate list is first reduced to only those codecs that return true from
     *                      {@link HtsCodec#ownsURI}
     *                  <li>
     *                      The candidate list is then further reduced to only those codecs that return true
     *                      from {@link HtsCodec#canDecodeURI(IOPath)}.
     *                  </li>
     *              </ol>
     *          <li>
     *              Otherwise:
     *                  <ol>
     *                      <li>
     *                          The candidate list is first reduced to those codecs that return true from
     *                          {@link HtsCodec#canDecodeURI(IOPath)}
     *                      <li>
     *                          The candidate list is then further reduced to only those codecs that return
     *                          true from {@link HtsCodec#canDecodeSignature(SignatureStream, String)}
     *                      </li>
     *                  </ol>
     *           </li>
     *  </ol>
     * <p>
     *      If the primary resource is a stream:
     *      <ol>
     *          <li>
     *              The candidate list is reduced to those codecs that return true from
     *              {@link HtsCodec#canDecodeSignature(SignatureStream, String)}
     *          </li>
     *     </ol>
     * <p>
     *     If a single codec remains in the candidate list after the resolution process described above,
     *     that codec is returned. It is an error if more than one codec is remaining in the candidate list
     *     after codec resolution. This usually indicates that the registry contains an ill-behaved codec
     *     implementation.
     * <p>
     *     Note: {@link HtsCodec#canDecodeSignature(SignatureStream, String)} will never be
     *     called by the framework on a resource if any codec returns true from {@link HtsCodec#ownsURI} for
     *     that resource.
     * </p>
     *
     * @param bundle the bundle to resolve to a codec
     * @return a codec that can decode the bundle resource
     *
     * @throws RuntimeException if the input resolves to more than one codec. this usually indicates that the
     * registry contains a poorly behaved codec.
     */
    public C resolveForDecoding(final Bundle bundle) {
        ValidationUtils.nonNull(bundle, "bundle");

        final BundleResource bundleResource = getPrimaryResource(bundle, true);
        final Optional<F> optFormat = getFormatForFormatString(bundleResource);
        final List<C> candidatesCodecs = resolveFormat(optFormat);

        final List<C> resolvedCodecs = bundleResource.getIOPath().isPresent() ?
                resolveForDecodingIOPath(bundleResource, candidatesCodecs) :
                resolveForDecodingStream(bundleResource, candidatesCodecs);

        return getOneOrThrow(
                resolvedCodecs,
                () -> String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "(NONE)",
                        bundleResource));
    }

    /**
     * Inspect a bundle and find a codec that can encode to the primary resource. For bundles with a
     * primary resource that is an {@code IOPath}, the structure of the IOPath (protocol scheme,
     * file extension, and query parameters) are used to determine the file format to be used.
     * <p>
     * For resources that are ambiguous (i.e., a stream, which has no file extension that can be used to
     * infer a format), the bundle resource must specify a format (see {@link BundleResource#getFormat()})
     * that corresponds to one of the formats for the content type used by this codec type. The newest
     * available version of that file format will be used.
     * <p>
     * To request a specific version, see {@link #resolveForEncoding(Bundle, HtsVersion)}. To request a
     * specific format and version, use {@link #resolveFormatAndVersion(Enum, HtsVersion)}.
     *
     * @param bundle the bundle to resolve to a codec for encoding
     * @return a codec that can decode the input bundle
     *
     * @throws RuntimeException if more than one codec matches. this usually indicates a registered codec
     * that is poorly behaved.
     */
    public C resolveForEncoding(final Bundle bundle) { return resolveForEncoding(bundle, HtsVersion.NEWEST_VERSION); }

    /**
     * Inspect a bundle and find a codec that can encode to the primary resource using the format version
     * requested. For bundles with a primary resource that is an {@code IOPath}, the structure of the IOPath
     * (protocol scheme, file extension, and query parameters) are used to determine the file format used.
     * <p>
     * For resources that are ambiguous (i.e., a stream which has no file extension that can be used to infer
     * a file format), the bundle resource must specify a format (see {@link BundleResource#getFormat()}) that
     * corresponds to one of the formats for the content type used by this codec type.
     *
     * @param bundle the bundle to use for encoding
     * @param htsVersion the version being requested (use HtsVersion.NEWEST_VERSION to use the newest
     *                   version codec registered)
     * @return A codec that can provide an encoder for the given inputs.
     */
    public C resolveForEncoding(final Bundle bundle, final HtsVersion htsVersion) {
        ValidationUtils.nonNull(bundle, "bundle");
        ValidationUtils.nonNull(htsVersion, "htsVersion");

        final BundleResource bundleResource = getPrimaryResource(bundle, false);
        final Optional<F> optFormat = getFormatForFormatString(bundleResource);
        final List<C> candidateCodecs = resolveFormat(optFormat);

        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> filteredCodecs = bundleResource.getIOPath().isPresent() ?
                resolveForEncodingIOPath(ioPath.get(), candidateCodecs) :
                candidateCodecs; // there isn't anything else to probe when the output is to a stream
        final List<C> resolvedCodecs = filterByVersion(filteredCodecs, htsVersion);

        return getOneOrThrow(
                resolvedCodecs,
                () ->  String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "(NONE)",
                        bundleResource));
    }

    /**
     * Obtain a list of codecs that claim to support file format {@code format} of type {@code F}
     *
     * @param format the input format of type {@code F}
     * @return The list of registered codecs that claim to support some version of file format {@code format}
     */
    public List<C> resolveFormat(final F format) {
        final Map<HtsVersion, C> allCodecsForFormat = codecs.get(format);
        if (allCodecsForFormat != null) {
            return allCodecsForFormat.values().stream().collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    /**
     * Obtain a list of codecs that claim to support version {@code formatVersion} of file format
     * {@code format} of type {@code F}.
     *
     * @param format the input format of type {@code F}
     * @param formatVersion the version of {@code format} requested
     * @return The list of registered codecs that claim to support version {@code formatVersion} of file
     * format {@code format}
     */
    public C resolveFormatAndVersion(final F format, final HtsVersion formatVersion) {
        final List<C> matchingCodecs = resolveFormat(format)
                .stream()
                .filter(codec -> codec.getFileFormat().equals(format) && codec.getVersion().equals(formatVersion))
                .collect(Collectors.toList());
        return getOneOrThrow(matchingCodecs, () -> String.format("%s/%s", format, formatVersion));
    }

    /**
     * Return a list of all codecs of the type {@code C} managed by this resolver
     * @return a list of all codecs of the type {@code C} managed by this resolver
     */
    public List<C> getCodecs() {
        // flatten out the codecs into a single list
        final List<C> codecList = codecs
                .values()
                .stream()
                .flatMap(map -> map.values().stream())
                .collect(Collectors.toList());
        return codecList;
    }

    private List<C> resolveForDecodingIOPath(final BundleResource bundleResource, final List<C> candidateCodecs) {
        ValidationUtils.validateArg(bundleResource.getIOPath().isPresent(), "an IOPath resource is required");
        final IOPath ioPath = bundleResource.getIOPath().get();

        // if at least one codec claims to own this resource's URI, only return the owners,
        // otherwise let each codec probe for a signature
        if (candidateCodecs.size() > 0) {
            final List<C> uriOwners = getURIOwners(candidateCodecs, ioPath);
            if (!uriOwners.isEmpty()) {
                return uriOwners;
            } else {
                final int maxSignatureProbeLength = getMaxSignatureProbeLength(candidateCodecs);
                final SignatureStream probingStream =
                        getIOPathSignatureProbingStream(bundleResource, maxSignatureProbeLength);
                return candidateCodecs.stream()
                        .filter(codec -> codec.canDecodeURI(ioPath))
                        .filter(codec -> canDecodeInputStreamSignature(
                                codec,
                                probingStream, maxSignatureProbeLength,
                                bundleResource.getDisplayName()
                        ))
                        .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private List<C> resolveForDecodingStream(final BundleResource bundleResource, final List<C> candidateCodecs) {
        final int streamPrefixSize = getMaxSignatureProbeLength(candidateCodecs);
        final SignatureStream signatureStream =
                bundleResource.getSignatureProbeStream(streamPrefixSize);
        return candidateCodecs.stream()
                .filter(codec -> canDecodeInputStreamSignature(
                        codec,
                        signatureStream, streamPrefixSize,
                        bundleResource.getDisplayName()
                ))
                .collect(Collectors.toList());
    }

    private List<C> resolveForEncodingIOPath(final IOPath ioPath, final List<C> candidateCodecs) {
        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((codec) -> codec.ownsURI(ioPath))
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

    private int getMaxSignatureProbeLength(final List<C> candidateCodecs) {
        // find the longest signature probe length of any candidate
        return candidateCodecs.stream()
                .map(codec -> codec.getSignatureProbeLength())
                .reduce(0, (a, b) -> Integer.max(a, b));
    }

    private SignatureStream getIOPathSignatureProbingStream(
            final BundleResource bundleResource,
            final int streamPrefixSize) {
        ValidationUtils.validateArg(bundleResource.getIOPath().isPresent(), "an IOPath resource is required");
        final IOPath inputPath = bundleResource.getIOPath().get();
        if (!inputPath.hasFileSystemProvider()) {
            // We're about to query the input stream to probe for a signature, but there is no
            // installed file system provider for the IOPath's protocol scheme. Attempting to get an
            // input stream directly from such an IOPath will fail. If the IOPath was legitimate, we
            // should never get here, since it would have been claimed by one of the installed codec's
            // "claimURI" implementations, or else it would be a known protocol such as "gs://" for
            // which the user expected a file system to be present. It likely represents user error
            // (a user entered "hdf://" instead of "hdfs://"), and it will fail anyway, so throw.
            throw new IllegalArgumentException(
                    String.format("The resource (%s) specifies a custom protocol (%s) " +
                                    "which no registered codec claims, and for which no NIO file system provider is available",
                            bundleResource,
                            inputPath.getURI().getScheme()));
        }
        return bundleResource.getSignatureProbeStream(streamPrefixSize);
    }

    private static<T extends HtsCodec<?, ?, ?>> boolean canDecodeInputStreamSignature(
            final T codec,
            final SignatureStream signatureStream,
            final int signatureProbeLength,
            final String displayName) {
        signatureStream.mark(signatureProbeLength);
        final boolean canDecode = codec.canDecodeSignature(signatureStream, displayName);
        signatureStream.reset();
        return canDecode;
    }

    private List<C> filterByVersion(final List<C> candidateCodecs, final HtsVersion htsVersion) {
        ValidationUtils.nonNull(htsVersion, "htsVersion");
        if (candidateCodecs.isEmpty()) {
            return candidateCodecs;
        }
        if (htsVersion.equals(HtsVersion.NEWEST_VERSION)) {
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

    // Get our initial candidate codec list, either filtered by format if one is present,
    // or otherwise all registered codecs for this codec format.
    private List<C> resolveFormat(final Optional<F> optFormat) {
        final List<C> candidateCodecs =
                optFormat.isPresent() ?
                        resolveFormat(optFormat.get()) :
                        getCodecs();
        return candidateCodecs;
    }

    private List<C> getURIOwners(final List<C> candidateCodecs, final IOPath ioPath) {
        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((codec) -> codec.ownsURI(ioPath))
                .collect(Collectors.toList());
        final boolean isCustomURI = !uriHandlers.isEmpty();

        if (isCustomURI) {
            // ensure that all codecs that claim to own this URI honor the contract that says if canDecodeURI
            // returns true, ownsURI must also return true for the same IOPath
            uriHandlers.stream().forEach(
                    codec -> {
                        if (!codec.canDecodeURI(ioPath)) {
                            throw new HtsjdkPluginException(
                                    String.format("The %s codec returned true for ownsURI but false for canDecodeURI for path: %s",
                                            codec,
                                            ioPath.getURI()));
                        }});
        }
        return uriHandlers;
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
        if (forEncoding && !bundleResource.hasInputType()) {
            throw new IllegalArgumentException(
                    String.format("The %s resource found (%s) cannot be used as an input resource",
                            requiredContentType,
                            bundleResource));
        } else if (!forEncoding && !bundleResource.hasOutputType()) { // for decoding
            throw new IllegalArgumentException(
                    String.format("The %s resource found (%s) cannot be used as an output resource",
                            requiredContentType,
                            bundleResource));
        }

        return bundleResource;
    }

    private Optional<F> getFormatForFormatString(final BundleResource bundleResource) {
        final Optional<String> optResourceFormat = bundleResource.getFormat();
        final Optional<F> optFormatEnum = optResourceFormat.flatMap(
                resourceFormat -> htsFormatEnumInstance.formatStringToEnum(resourceFormat));
        if (optResourceFormat.isPresent() && !optFormatEnum.isPresent()) {
            // throw if the resource format is present, but doesn't map to any format supported by the content
            throw new IllegalArgumentException(
                    String.format("The format (%s) specified in the bundle resource (%s) does not correspond to any known format for content type (%s)",
                            optResourceFormat.get(),
                            bundleResource,
                            requiredContentType));
        }
        return optFormatEnum;
    }

    @PrivateAPI
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
