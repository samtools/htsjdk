package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.HtsCodec;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleResource;
import htsjdk.exception.HtsjdkIOException;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

//TODO: unify/clarify exception types
//TODO: add a "getRequired" on bundle that throws if no present
//TODO: find a way to better align the ReadsFormat enum with content subtype strings
// if we used Strings for codec format type instead of a locked down enum, it would
// not only align better with the bundle content subtype concept, but it would make it
// possible to dynamically extend this registry to additional content subtypes without
// changing HTSJDK...
//TODO: content type should be mapped to F type param via types
//TODO: encryption/decryption key files, etc.
//TODO: need a way to allow the caller to provide custom codec resolution
//TODO: display the bundle resource and the params of the winning codecs when there is more than one
//TODO: need to support codecs that need to see the stream (can't deterministically tell from the extension)
//TODO: support/test stdin/stdout
//TODO: add an entry point that takes codec/version pair to resolve directly

/**
 * Class used by the registry to track all codec formats and versions for a single codec type.
 * @param <F> enum representing the formats for this codec type
 * @param <C> the HtsCodec type
 */
final class HtsCodecsByFormat<F extends Enum<F>, C extends HtsCodec<F, ?, ?>> {
    private static final Log LOG = Log.getInstance(HtsCodecsByFormat.class);

    private final Map<F, Map<HtsCodecVersion, List<C>>> codecs = new HashMap<>();
    //TODO: this is unused. unnecessary ?
    private final Map<F, HtsCodecVersion> newestVersion = new HashMap<>();

    /**
     * Register a codec of type {@link C} for file format {@link F}.
     *
     * @param codec a codec of type {@link C} for file format {@link F}
     */
    public void register(final C codec) {
        final F codecFormatType = codec.getFileFormat();
        codecs.compute(
                codecFormatType,
                (final F key, final Map<HtsCodecVersion, List<C>> v) -> {
                    final Map<HtsCodecVersion, List<C>> versionMap = v == null ? new HashMap<>() : v;
                    versionMap.compute(
                            codec.getVersion(),
                            (final HtsCodecVersion version, final List<C> oldCodecList) -> {
                                final List<C> newCodecList = oldCodecList == null ? new ArrayList<>() : oldCodecList;
                                newCodecList.add(codec);
                                return newCodecList;
                            });
                    return versionMap;
                });
        // keep track of the newest version for each sub format
        updateNewestVersion(codecFormatType, codec.getVersion());
    }

    /**
     * Find codecs that can read the contentType required for this bundle, if its present.
     */
    public C resolveCodecForInput(
            final Bundle bundle,
            final String requiredContentType,
            final Function<String, F> mapContentSubTypeToFormat) {

        //TODO: throw if the primary resource isn't the requiredContentType
        final BundleResource bundleResource = getRequiredBundleResource(bundle, requiredContentType,true);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource, mapContentSubTypeToFormat, requiredContentType);
        final List<C> candidatesForFormat = getCodecsForOptionalFormat(optFormat);

        // If the resource is an IOPath resource, look to see if any codec(s) claim ownership of the URI,
        // specifically the protocol scheme.
        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> resolvedCodecs = ioPath.isPresent() ?
                getCodecsForInputIOPath(candidatesForFormat, bundleResource) :
                getCodecsForInputStream(candidatesForFormat, bundleResource);

        return getOneOrThrow(resolvedCodecs,
                String.format("Format: %s Input: %s",
                        optFormat.isPresent () ? optFormat.get() : "NONE",
                        bundleResource));
    }

    /**
     * Find codecs that can read the contentType required for this bundle, if its present.
     */
    public C resolveCodecForOutput(
            final Bundle bundle,
            final String requiredContentType,
            final Optional<HtsCodecVersion> optHtsVersion,
            final Function<String, F> contentSubTypeToFormat) {

        final BundleResource bundleResource = getRequiredBundleResource(bundle, requiredContentType,false);
        final Optional<F> optFormat = getFormatForContentSubType(bundleResource, contentSubTypeToFormat, requiredContentType);
        final List<C> candidateCodecs = getCodecsForOptionalFormat(optFormat);

        // If the resource is an IOPath resource, see if any codec(s) claim ownership of the URI,
        // specifically the protocol scheme.
        final Optional<IOPath> ioPath = bundleResource.getIOPath();
        final List<C> filteredCodecs = ioPath.isPresent() ?
                getCodecsForOutputIOPath(candidateCodecs, ioPath.get()) :
                candidateCodecs; // there isn't anything else to go on when the output is to a stream

        final List<C> resolvedCodecs = filterByVersion(filteredCodecs, optHtsVersion);
        return getOneOrThrow(resolvedCodecs,
                String.format("Format: %s Output: %s",
                        optFormat.isPresent () ? optFormat.get() : "NONE",
                        bundleResource));
    }

    final List<C> filterByVersion(final List<C> candidateCodecs, final Optional<HtsCodecVersion> optHtsVersion) {
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

    final List<C> getCodecsForInputIOPath(final List<C> candidateCodecs, final BundleResource bundleResource) {
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

    final List<C> getCodecsForOutputIOPath(final List<C> candidateCodecs, final IOPath ioPath) {
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
        if (bundleResource.isRandomAccess()) {
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
        final BundleResource bundleResource =
                bundle.get(requiredContentType).orElseThrow(
                        () -> new IllegalArgumentException(
                                String.format("No resource found in bundle with content type %s", requiredContentType)));

        // Get the resource of the required content type, and warn if its not the primary resource in the
        // bundle. Its not a requirement that it be the primary, only that the bundle have some resource
        // with the required content type, but it could indicate that the wrong bundle was provided, and
        // knowing that might be helpful for the user if codec resolution subsequently fails downstream.
        final String bundlePrimaryContentType = bundle.getPrimaryContentType();
        if (!requiredContentType.equals(bundlePrimaryContentType)) {
            LOG.warn(String.format(
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

    public List<C> getAllCodecs() {
        // flatten out the codecs into a single list
        final List<C> cList = codecs
                .values()
                .stream()
                .flatMap(m -> m.values().stream())
                .flatMap(l -> l.stream())
                .collect(Collectors.toList());
        return cList;
    }

    public List<C> getAllCodecsForFormat(final F rf) {
        final Map<HtsCodecVersion, List<C>> allCodecsForFormat = codecs.get(rf);
        if (allCodecsForFormat != null) {
            return allCodecsForFormat.values().stream().flatMap(l -> l.stream()).collect(Collectors.toList());
        }
        return Collections.EMPTY_LIST;
    }

    private Optional<F> getFormatForContentSubType(
            final BundleResource bundleResource,
            final Function<String, F> mapContentSubTypeToFormat,
            final String requiredContentType) {
         final Optional<String> optContentSubType = bundleResource.getContentSubType();
        if (!optContentSubType.isPresent()) {
            return Optional.empty();
        }
        final String contentSubType = optContentSubType.get();
        final F format = mapContentSubTypeToFormat.apply(contentSubType);
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

    public C getCodecForFormatAndVersion(final F formatType, HtsCodecVersion codecVersion) {
         final List<C> matchingCodecs = getAllCodecsForFormat(formatType).stream()
                .filter(codec -> codec.getFileFormat().equals(formatType) && codec.getVersion().equals(codecVersion))
                 .collect(Collectors.toList());
         return getOneOrThrow(matchingCodecs, String.format("Format: %s Version %s", formatType, codecVersion));
    }

    @SuppressWarnings("rawtypes")
    public static<T extends HtsCodec> boolean canDecodeIOPathSignature(
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

    @SuppressWarnings("rawtypes")
    private static<T extends HtsCodec> boolean canDecodeInputStreamSignature(
            final T codec,
            final int streamPrefixSize,
            final String displayName,
            final SignatureProbingInputStream signatureProbingInputStream) {
        signatureProbingInputStream.mark(streamPrefixSize);
        final boolean canDecode = codec.canDecodeSignature(signatureProbingInputStream, displayName);
        signatureProbingInputStream.reset();
        return canDecode;
    }

//    // get the newest version codec for the given format
//    public Optional<C> getNewestCodecForFormat(final F format) {
//        ValidationUtils.nonNull(format, "format must not be null");
//        final Optional<HtsCodecVersion> newestFormatVersion = getNewestVersion(format);
//        if (!newestFormatVersion.isPresent()) {
//            throw new IllegalArgumentException(String.format("No codecs found for format %s", format));
//        }
//        final Optional<C> codec = getCodecForFormatAndVersion(format, newestFormatVersion.get());
//        return codec;
//    }
//
//    public Optional<HtsCodecVersion> getNewestVersion(final F format) {
//        return Optional.of(newestVersion.get(format));
//    }

    //TODO: do we still need to track newest version, not that we discover it by .reduce ?
    private void updateNewestVersion(final F codecFormat, final HtsCodecVersion newVersion) {
        ValidationUtils.nonNull(codecFormat);
        ValidationUtils.nonNull(newVersion);
        newestVersion.compute(
                codecFormat,
                (format, previousVersion) ->
                        previousVersion == null ?
                                newVersion :
                                previousVersion.compareTo(newVersion) > 0 ?
                                        previousVersion :
                                        newVersion);
    }

    private C getOneOrThrow(final List<C> resolvedCodecs, final String contextMessage) {
        if (resolvedCodecs.size() == 0) {
            throw new RuntimeException(String.format("No matching codec could be found for %s", contextMessage));
        } else if (resolvedCodecs.size() > 1) {
            throw new RuntimeException("Multiple codecs accepted the output bundle");
        } else {
            return resolvedCodecs.get(0);
        }
    }

}
