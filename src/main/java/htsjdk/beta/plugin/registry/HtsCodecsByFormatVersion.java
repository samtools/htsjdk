package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
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
 * Class used by the registry to track all codec formats and versions for a single codec type.
 * @param <F> enum representing the formats for this codec type
 * @param <C> the HtsCodec type
 */
final class HtsCodecsByFormatVersion<F extends Enum<F>, C extends HtsCodec<F, ?, ?>> {
    private static final Log LOG = Log.getInstance(HtsCodecsByFormatVersion.class);

    final static String NO_SUPPORTING_CODEC_ERROR = "No registered codec accepts the provided resource";
    final static String MULTIPLE_SUPPORTING_CODECS_ERROR = "Multiple codecs accept the provided resource";

    private final Map<F, Map<HtsCodecVersion, C>> codecs = new HashMap<>();
    private final Function<String, F> formatFromContentSubType;


    public HtsCodecsByFormatVersion(final Function<String, F> formatFromContentSubType) {
        this.formatFromContentSubType = formatFromContentSubType;
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
        final Map<HtsCodecVersion, C> versionMap = codecs.get(fileFormat);
        if (versionMap == null) {
            // first codec for this format
            final Map<HtsCodecVersion, C> newMap = new HashMap<>();
            newMap.put(codec.getVersion(), codec);
            codecs.put(fileFormat, newMap);
            return null;
        } else {
            // update the version map for this codec
            final C oldCodec = versionMap.get(codec.getVersion());
            versionMap.put(codec.getVersion(), codec);
            if (oldCodec != null) {
                LOG.warn(String.format("A previously registered HTS codec %s was replaced with the %s codec ",
                        oldCodec.getDisplayName(),
                        codec.getDisplayName()));
            }
            return oldCodec;
        }
    }

    /**
     * Find codecs that can read the contentType required for this bundle, if its present.
     */
    public C resolveCodecForDecoding(final Bundle bundle, final String requiredContentType) {

        final BundleResource bundleResource = getRequiredBundleResource(bundle, requiredContentType,true);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource, formatFromContentSubType, requiredContentType);
        final List<C> candidatesForFormat = getCodecsForOptionalFormat(optFormat);

        // If the resource is an IOPath resource, look to see if any codec(s) claim ownership of the URI,
        // specifically the protocol scheme.
        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> resolvedCodecs = ioPath.isPresent() ?
                getCodecsForInputIOPath(candidatesForFormat, bundleResource) :
                getCodecsForInputStream(candidatesForFormat, bundleResource);

        return getOneOrThrow(resolvedCodecs,
                () -> String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "NONE",
                        bundleResource));
    }

    /**
     * Find codecs that can read the contentType required for this bundle, if its present.
     *
     * NOTE: If an output stream resources is provided as the target output, the bundle resource must include
     * a file format, otherwise multiple codecs will accept the bundle, and an exception will be thrown.
     */
    public C resolveCodecForEncoding(
            final Bundle bundle,
            final String requiredContentType,
            final Optional<HtsCodecVersion> optHtsVersion) {

        final BundleResource bundleResource = getRequiredBundleResource(bundle, requiredContentType,false);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource, formatFromContentSubType, requiredContentType);
        final List<C> candidateCodecs = getCodecsForOptionalFormat(optFormat);

        // If the resource is an IOPath resource, see if any codec(s) claim ownership of the URI,
        // specifically the protocol scheme.
        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> filteredCodecs = ioPath.isPresent() ?
                getCodecsForOutputIOPath(candidateCodecs, ioPath.get()) :
                candidateCodecs; // there isn't anything else to go on when the output is to a stream

        final List<C> resolvedCodecs = filterByVersion(filteredCodecs, optHtsVersion);
        return getOneOrThrow(resolvedCodecs,
                () ->  String.format("%s/%s",
                        optFormat.isPresent () ? optFormat.get() : "NONE",
                        bundleResource));
    }

    public List<C> getAllCodecs() {
        // flatten out the codecs into a single list
        final List<C> cList = codecs
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .collect(Collectors.toList());
        return cList;
    }

    public List<C> getAllCodecsForFormat(final F rf) {
        final Map<HtsCodecVersion, C> allCodecsForFormat = codecs.get(rf);
        if (allCodecsForFormat != null) {
            return allCodecsForFormat.values().stream().collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    public C getCodecForFormatVersion(final F format, HtsCodecVersion formatVersion) {
        final List<C> matchingCodecs = getAllCodecsForFormat(format)
                .stream()
                .filter(codec -> codec.getFileFormat().equals(format) && codec.getVersion().equals(formatVersion))
                .collect(Collectors.toList());
        return getOneOrThrow(matchingCodecs, () -> String.format("%s/%s", format, formatVersion));
    }

    @SuppressWarnings("rawtypes")
    private static<T extends HtsCodec<?, ?, ?>> boolean canDecodeIOPathSignature(
            final T codec,
            final BundleResource bundleResource,
            final IOPath inputPath,
            final int streamPrefixSize) {
        // If the input IOPath has no file system provider, then it probably has a custom protocol scheme
        // and represents a remote resource. Attempting to get an input stream directly from such an IOPath
        // will likely fail, so fall back to just checking the URI, and let the winning codec access
        // the remote resource once codec resolution is complete.
        if (!inputPath.hasFileSystemProvider()) {
            return codec.canDecodeURI(inputPath);
        } else {
            return codec.canDecodeSignature(
                    bundleResource.getSignatureProbingStream(streamPrefixSize),
                    inputPath.getRawInputString());
        }
    }

    private List<C> filterByVersion(final List<C> candidateCodecs, final Optional<HtsCodecVersion> optHtsVersion) {
        if (candidateCodecs.isEmpty()) {
            return candidateCodecs;
        }
        if (optHtsVersion.isPresent()) {
            return candidateCodecs.stream()
                    .filter(c -> c.getVersion().equals(optHtsVersion.get())).collect(Collectors.toList());
        }
        // find the newest codec version in the list of candidates, and return all the codecs for that
        // version (since there still can be more than one)
        final HtsCodecVersion newestCodecVersion = candidateCodecs.stream()
                .map(c -> c.getVersion())
                .reduce(
                        candidateCodecs.get(0).getVersion(),
                        (HtsCodecVersion a, HtsCodecVersion b) -> a.compareTo(b) > 0 ? a : b);
        return candidateCodecs.stream().filter(c -> c.getVersion().equals(newestCodecVersion)).collect(Collectors.toList());
    }

    // Get our initial candidate codec list, either filtered by content subtype if one is present,
    // or otherwise all registered codecs for this codec format.
    private List<C> getCodecsForOptionalFormat(final Optional<F> optFormat) {
        final List<C> candidateCodecs =
                optFormat.isPresent() ?
                        getAllCodecsForFormat(optFormat.get()) :
                        getAllCodecs();
        return candidateCodecs;
    }

    private List<C> getCodecsForInputIOPath(final List<C> candidateCodecs, final BundleResource bundleResource) {
        ValidationUtils.validateArg(bundleResource.getIOPath().isPresent(), "an IOPath resource is required");
        final IOPath ioPath = bundleResource.getIOPath().get();

        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((c) -> c.claimURI(ioPath))
                .collect(Collectors.toList());

        // If at least one codec claims this as a custom URI, prune the candidates to only codecs
        // that also claim it, and don't try to call getInputStream on the resource to check the signature.
        // Instead just let the codecs that claim the URI further process it.
        final List<C> filteredCodecs = uriHandlers.isEmpty() ? candidateCodecs : uriHandlers;

        // get our signature probing size across all candidate codecs
        final int streamPrefixSize = getSignatureProbeStreamSize(filteredCodecs);

        // reduce our candidates based on uri and IOPath. we can't create the signature probing stream
        // yet because we still can't be sure that we can obtain a stream on the input
        return filteredCodecs.stream()
                .filter(c -> c.canDecodeURI(ioPath))
                .filter(c -> canDecodeIOPathSignature(c, bundleResource, ioPath, streamPrefixSize))
                .collect(Collectors.toList());
    }

    private List<C> getCodecsForOutputIOPath(final List<C> candidateCodecs, final IOPath ioPath) {
        final List<C> uriHandlers = candidateCodecs.stream()
                .filter((c) -> c.claimURI(ioPath))
                .collect(Collectors.toList());

        // If at least one codec claims this as a custom URI, prune the candidates to only codecs
        // that also claim it, and don't try to call getInputStream on the resource to check the signature.
        // Instead just let the codecs that claim the URI further process it.
        final List<C> filteredCodecs = uriHandlers.isEmpty() ? candidateCodecs : uriHandlers;

        // reduce our candidates based on uri and IOPath
        return filteredCodecs.stream()
                .filter(c -> c.canDecodeURI(ioPath))
                .collect(Collectors.toList());
    }

    private List<C> getCodecsForInputStream(final List<C> candidateCodecs, final BundleResource bundleResource) {
        if (bundleResource.hasSeekableStream()) {
            // stream is already seekable so no need to wrap it
            throw new IllegalArgumentException("SeekableStreamResource input resolution is not yet implemented");
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

    private int getSignatureProbeStreamSize(final List<C> candidateCodecs) {
        return candidateCodecs.stream()
                .map(c -> c.getSignatureProbeStreamSize())
                .reduce(0, (a, b) -> Integer.max(a, b));
    }

    private final BundleResource getRequiredBundleResource(
            final Bundle bundle,
            final String requiredContentType,
            final boolean forInput) {
        // Throw if the bundle contains no resource of the content type we need
        final BundleResource bundleResource = bundle.getOrThrow(requiredContentType);

        // Get the resource of the required content type, and throw if its not the primary resource
        final String bundlePrimaryContentType = bundle.getPrimaryContentType();
        if (!requiredContentType.equals(bundlePrimaryContentType)) {
            throw new IllegalArgumentException(String.format(
                    "Checking bundle with primary content type %s for resource with requested content type %s.",
                    bundlePrimaryContentType,
                    requiredContentType));
        }

        // Since we're going to use the resource as input, make sure the resource we found is an INPUT resource,
        // not an output stream or something.
        if (forInput) {
            if (!bundleResource.isInput()) {
                throw new IllegalArgumentException(
                        String.format("The %s resource found (%s) cannot be used as an input resource",
                                requiredContentType,
                                bundleResource));
            }
        } else {
            if (!bundleResource.isOutput()) {
                throw new IllegalArgumentException(
                        String.format("The %s resource found (%s) cannot be used as an output resource",
                                requiredContentType,
                                bundleResource));
            }
        }

        return bundleResource;
    }

    private Optional<F> getFormatForContentSubType(
            final BundleResource bundleResource,
            final Function<String, F> formatFromContentSubType,
            final String requiredContentType) {
         final Optional<String> optContentSubType = bundleResource.getContentSubType();
        if (!optContentSubType.isPresent()) {
            return Optional.empty();
        }
        final String contentSubType = optContentSubType.get();
        final F format = formatFromContentSubType.apply(contentSubType);
        if (format == null) {
            // if the bundle contentSubType is present, but it doesn't map to any contentSubType
            // supported by the content type being requested, throw
            throw new IllegalArgumentException(
                    String.format("The sub-content type (%s) found in the bundle resource (%s) does not correspond to any known subtype for content type (%s)",
                            contentSubType,
                            bundleResource,
                            requiredContentType));
        }
        return Optional.of(format);
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
            throw new RuntimeException(String.format(
                    "%s %s",
                    NO_SUPPORTING_CODEC_ERROR,
                    contextMessage.get()));
        } else if (resolvedCodecs.size() > 1) {
            final String multipleCodecsMessage = String.format(
                    "%s (%s)\n%s\nThis indicates an internal error in one or more of the codecs:",
                    MULTIPLE_SUPPORTING_CODECS_ERROR,
                    contextMessage.get(),
                    resolvedCodecs.stream().map(c -> c.getDisplayName()).collect(Collectors.joining("\n")));
            throw new RuntimeException(multipleCodecsMessage);
        } else {
            return resolvedCodecs.get(0);
        }
    }

}
