package htsjdk.beta.plugin.variants;

import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.*;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SamFiles;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.Tuple;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * A {@link Bundle} for variants and variants-related resources that are backed by on disk files. A {@link
 * htsjdk.beta.plugin.variants.VariantsBundle} has a primary resource with content type {@link
 * BundleResourceType#VARIANT_CONTEXTS}; and an optional index resource. A VariantsBundle can also contain
 * additional resources.
 *
 * Note that this class is merely a convenience class for the case where the variants are backed by files on disk.
 * A bundle that contains variants and related resources can be created manually using the {@link Bundle} class.
 * This class provides convenient constructors, and validation for JSON interconversions. To create a VariantsBundle
 * for variants sources that are backed by streams or otherv{@link BundleResource} types, the {@link Bundle} and
 * {@link BundleBuilder} classes can be used to construct such bundles directly.
 *
 * @param <T> The type to use when creating a new IOPathResource for a {@link htsjdk.beta.plugin.variants.VariantsBundle}.
 *          Note that any resources that are manually inserted into a {@link htsjdk.beta.plugin.variants.VariantsBundle}
 *          using the {@link #VariantsBundle(Collection)} constructor may have IOPathResources that do not conform to
 *          this type.
 */
public class VariantsBundle<T extends IOPath> extends Bundle implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Log LOG = Log.getInstance(htsjdk.beta.plugin.variants.VariantsBundle.class);

    /**
     * Create a {@link htsjdk.beta.plugin.variants.VariantsBundle} containing only a variants resource.
     *
     * @param vcfPath An {@link IOPath}-derived object that represents a source of variants.
     */
    public VariantsBundle(final T vcfPath) {
        this(Arrays.asList(toInputResource(
                BundleResourceType.VARIANT_CONTEXTS,
                ValidationUtils.nonNull(vcfPath, BundleResourceType.VARIANTS_VCF))));
    }

    /**
     * Create a {@link htsjdk.beta.plugin.variants.VariantsBundle} containing only variants and an index.
     *
     * @param vcfPath An {@link IOPath}-derived object that represents a source of variants.
     */
    public VariantsBundle(final T vcfPath, final T indexPath) {
        this(Arrays.asList(
                toInputResource(BundleResourceType.VARIANT_CONTEXTS,
                                ValidationUtils.nonNull(vcfPath, BundleResourceType.VARIANTS_VCF)),
                toInputResource(
                        BundleResourceType.VARIANTS_INDEX,
                        ValidationUtils.nonNull(indexPath, BundleResourceType.VARIANTS_INDEX))));
    }

    /**
     * Create a {@link htsjdk.beta.plugin.variants.VariantsBundle} using the resources in an existing bundle. A
     * resource with content type {@link BundleResourceType#VARIANTS_VCF} must be present in the resources, or
     * this constructor will throw.
     *
     * Note that this constructor allows existing {@link IOPathResource}s that do not conform to the type
     * {@link T} to be included in the resulting {@link htsjdk.beta.plugin.variants.VariantsBundle}.
     *
     * @param resources collection of {@link BundleResource}. the collection must include a resource with
     *                 content type {@link BundleResourceType#VARIANTS_VCF}.
     * @throws IllegalArgumentException if no resource with content type {@link BundleResourceType#VARIANTS_VCF} is
     * included in the input {@link BundleResource} collection
     */
    protected VariantsBundle(final Collection<BundleResource> resources) {
        super(BundleResourceType.VARIANT_CONTEXTS, resources);
    }

    /**
     * return the {@link BundleResourceType#VARIANTS_VCF} {@link BundleResource} for this {@link htsjdk.beta.plugin.variants.VariantsBundle}
     *
     * @return the {@link BundleResourceType#VARIANTS_VCF} {@link BundleResource} for this {@link htsjdk.beta.plugin.variants.VariantsBundle}
     */
    public BundleResource getVariants() {
        return getOrThrow(BundleResourceType.VARIANT_CONTEXTS);
    }

    /**
     * Get the optional {@link BundleResourceType#VARIANTS_INDEX} resource for this {@link htsjdk.beta.plugin.variants.VariantsBundle}.
     *
     * @return the optional {@link BundleResourceType#VARIANTS_INDEX} resrouce for this {@link htsjdk.beta.plugin.variants.VariantsBundle},
     * or Optional.empty if no index resource is present in the bundle.
     */
    public Optional<BundleResource> getIndex() {
        return get(BundleResourceType.VARIANTS_INDEX);
    }

    /**
     * Create a {@link VariantsBundle} from a JSON string contained in jsonPath.
     *
     * @param jsonPath the path to a file that contains a {@link Bundle} serialized to JSON. The bundle
     *                 must contain a resource with content type VARIANT_CONTEXTS.
     * @return a {@link VariantsBundle} created from jsonPath
     */
    public static VariantsBundle<IOPath> getVariantsBundleFromPath(final IOPath jsonPath) {
        return getVariantsBundleFromString(IOPathUtils.getStringFromPath(jsonPath));
    }

    /**
     * Create a {@link htsjdk.beta.plugin.variants.VariantsBundle} from a JSON string.
     *
     * @param jsonString the jsonString to use to create the {@link htsjdk.beta.plugin.variants.VariantsBundle}
     * @return a {@link htsjdk.beta.plugin.variants.VariantsBundle}
     */
    public static VariantsBundle<IOPath> getVariantsBundleFromString(final String jsonString) {
        return getVariantsBundleFromString(jsonString, HtsPath::new);
    }

    /**
     * Create a {@link htsjdk.beta.plugin.variants.VariantsBundle} from a JSON string with all IOPathResources using
     * an IOPath-derived class of type {@code T}.
     *
     * @param jsonString the string to use to create the {@link htsjdk.beta.plugin.variants.VariantsBundle}
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type {@code T}
     * @param <T> the type of
     * @return a newly created {@link htsjdk.beta.plugin.variants.VariantsBundle}
     */
    public static <T extends IOPath> VariantsBundle<T> getVariantsBundleFromString(
            final String jsonString,
            final Function<String, T> ioPathConstructor) {
        return new VariantsBundle<>(BundleJSON.toBundle(jsonString, ioPathConstructor).getResources());
    }

    /**
     * Find the companion index for a variants source, and create a new {@link htsjdk.beta.plugin.variants.VariantsBundle}
     * containing the variants and the companion index, if one can be found.
     *
     * @param variants the variants source to use
     * @return a {@link htsjdk.beta.plugin.variants.VariantsBundle} containing variants and companion index, if it can
     * be found.
     */
    public static VariantsBundle<IOPath> resolveIndex(final IOPath variants) {
        return resolveIndex(variants, HtsPath::new);
    }

    /**
     * Find the companion index for a variants source, and create a new {@link htsjdk.beta.plugin.variants.VariantsBundle} containing the
     * variants and the companion index, if one can be found.
     *
     * An index can only be resolved for an IOPath that represents on a file system for which an NIO
     * provider is installed. Remote paths that use a protocol scheme for which no NIO file system is
     * available will (silently) not be resolved.
     *
     * @param variants the variants source to use
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @param <T> the IOPath-derived type of the IOPathResources in the new bundle
     * @return a {@link htsjdk.beta.plugin.variants.VariantsBundle} containing variants and companion index, if it can be found
     */
    public static <T extends IOPath> VariantsBundle<T> resolveIndex(
            final T variants,
            final Function<String, T> ioPathConstructor) {
        if (variants.hasFileSystemProvider()) {
            final Path index = SamFiles.findIndex(variants.toPath());
            if (index == null) {
                return new VariantsBundle<>(variants);
            } else {
                return new VariantsBundle<T>(variants, ioPathConstructor.apply(index.toUri().toString()));
            }
        }
        return new VariantsBundle<>(variants);
    }

    public static boolean looksLikeAVariantsBundle(final IOPath rawVCFPath) {
        return rawVCFPath.getURI().getPath().endsWith(BundleJSON.BUNDLE_EXTENSION);
    }

    private static <T extends IOPath> IOPathResource toInputResource(final String providedContentType, final T ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        final Optional<Tuple<String, String>> typePair = getInferredContentTypes(ioPath);
        if (typePair.isPresent()) {
            if (providedContentType != null && !typePair.get().a.equals(providedContentType)) {
                LOG.warn(String.format(
                        "Provided content type \"%s\" for \"%s\" doesn't match derived content type \"%s\"",
                        providedContentType,
                        ioPath.getRawInputString(),
                        typePair.get().a));
            }
        }
        return new IOPathResource(ioPath, providedContentType);
    }

    // Try to infer the contentType/format, i.e., variants from an IOPath. Currently this
    // exists purely to check for logical inconsistencies. It can detect cases that are illogical
    // (an IOPath that has format CRAM, but file extension BAM), but it can't determinstically
    // and correctly infer the types in all cases without reproducing all the logic embedded in all the
    // codecs (i.e., an htsget IOPath ends in ".bam", but has format HTSGET_BAM, not BAM - detecting
    // that here would require parsing the entire IOPath structure, which is best left to the codecs
    // themselves). So for now its advisory, but maybe it should be abandoned altogether.
    private static <T extends IOPath> Optional<Tuple<String, String>> getInferredContentTypes(final T ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        final Optional<String> extension = ioPath.getExtension();
        if (extension.isPresent()) {
            final String ext = extension.get();
            if (ext.equals(FileExtensions.VCF)) {
                return Optional.of(new Tuple<>(BundleResourceType.VARIANT_CONTEXTS, BundleResourceType.VARIANTS_VCF));
            } else if (ext.equals(FileExtensions.COMPRESSED_VCF)) {
                return Optional.of(new Tuple<>(BundleResourceType.VARIANT_CONTEXTS, BundleResourceType.VARIANTS_VCF));
            } //TODO...finish this
        }
        return Optional.empty();
    }

}
