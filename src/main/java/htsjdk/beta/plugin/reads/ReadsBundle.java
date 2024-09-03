package htsjdk.beta.plugin.reads;

import htsjdk.beta.io.IOPathUtils;
import htsjdk.beta.io.bundle.BundleJSON;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleBuilder;
import htsjdk.beta.io.bundle.IOPathResource;
import htsjdk.beta.io.bundle.BundleResource;
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
 * A class for creating a {@link Bundle} for reads and reads-related resources. A {@link ReadsBundle} has a
 * primary resource with content type {@link BundleResourceType#CT_ALIGNED_READS}; and an optional index
 * resource. ReadsBundles can also contain other resources.
 *
 * {@link ReadsBundle} is primarily a convenience layer for the common case where a {@link Bundle}
 * contains reads and related resources backed by {@link IOPathResource}s. It provides convenient
 * constructors, and validation for JSON interconversions. For reads sources that are backed by streams or
 * other {@link BundleResource} types, the {@link Bundle} and {@link BundleBuilder} classes can be used
 * directly.
 *
 * @param <T> The type to use when creating a {@link ReadsBundle} new IOPathResources for a {@link ReadsBundle}.
 *           Note that resources that are put into a {@link ReadsBundle} using the {{@link #ReadsBundle(Collection)}}
 *           constructor may have tIOPathResources that do not conform to this type.
 */
public class ReadsBundle<T extends IOPath> extends Bundle implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final Log LOG = Log.getInstance(ReadsBundle.class);

    /**
     * Create a {@link ReadsBundle} containing only a reads resource.
     *
     * @param reads An {@link IOPath}-derived object that represents a source of reads.
     */
    public ReadsBundle(final T reads) {
        this(Arrays.asList(toInputResource(BundleResourceType.CT_ALIGNED_READS, reads)));
    }

    /**
     * Create a {@link ReadsBundle} containing only reads and an index.
     *
     * @param reads An {@link IOPath}-derived object that represents a source of reads.
     */
    public ReadsBundle(final T reads, final T index) {
        this(Arrays.asList(
                toInputResource(BundleResourceType.CT_ALIGNED_READS, reads),
                toInputResource(BundleResourceType.CT_READS_INDEX, index)));
    }

    /**
     * Create a {@link ReadsBundle} using the resources in an existing bundle. A resource with content type
     * {@link BundleResourceType#CT_ALIGNED_READS} must be present in the resources, or this constructor will throw.
     *
     * Note that this constructor allows existing {@link IOPathResource}s that do not conform to the type
     * {@link T} to be included in the resulting {@link ReadsBundle}.
     *
     * @param resources collection of {@link BundleResource}. the collection must include a resource with
     *                 content type {@link BundleResourceType#CT_ALIGNED_READS}.
     * @throws IllegalArgumentException if no resource with content type {@link BundleResourceType#CT_ALIGNED_READS} is
     * included in the input {@link BundleResource} collection
     */
    protected ReadsBundle(final Collection<BundleResource> resources) {
        super(BundleResourceType.CT_ALIGNED_READS, resources);
    }

   /**
    * return the {@link BundleResourceType#CT_ALIGNED_READS} {@link BundleResource} for this {@link ReadsBundle}
    *
    * @return the {@link BundleResourceType#CT_ALIGNED_READS} {@link BundleResource} for this {@link ReadsBundle}
    */
    public BundleResource getReads() {
        return getOrThrow(BundleResourceType.CT_ALIGNED_READS);
    }

    /**
     * Get the optional {@link BundleResourceType#CT_READS_INDEX} resource for this {@link ReadsBundle}.
     *
     * @return the optional {@link BundleResourceType#CT_READS_INDEX} resrouce for this {@link ReadsBundle},
     * or Optional.empty if no index resource is present in the bundle.
     */
    public Optional<BundleResource> getIndex() {
        return get(BundleResourceType.CT_READS_INDEX);
    }

    /**
     * Create a {@link ReadsBundle} from a JSON string contained in jsonPath.
     *
     * @param jsonPath the path to a file that contains {@link Bundle} serialized to JSON. The bundle
     *                 must contain a resource with content type READS.
     * @return a {@link ReadsBundle} created from jsonPath
     */
    public static ReadsBundle<IOPath> getReadsBundleFromPath(final IOPath jsonPath) {
        return getReadsBundleFromString(IOPathUtils.getStringFromPath(jsonPath));
    }

    /**
     * Create a {@link ReadsBundle} from a JSON string.
     *
     * @param jsonString the jsonString to use to create the {@link ReadsBundle}
     * @return a {@link ReadsBundle}
     */
    public static ReadsBundle<IOPath> getReadsBundleFromString(final String jsonString) {
        return getReadsBundleFromString(jsonString, HtsPath::new);
    }

    /**
     * Create a {@link ReadsBundle} from a JSON string with all IOPathResources using an IOPath-derived
     * class of type {@code T}.
     *
     * @param jsonString the string to use to create the {@link ReadsBundle}
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type {@code T}
     * @param <T> the type of
     * @return a newly created {@link ReadsBundle}
     */
    public static <T extends IOPath> ReadsBundle<T> getReadsBundleFromString(
            final String jsonString,
            final Function<String, T> ioPathConstructor) {
        return new ReadsBundle<>(BundleJSON.toBundle(jsonString, ioPathConstructor).getResources());
    }

    /**
     * Find the companion index for a reads source, and create a new {@link ReadsBundle} containing the
     * reads and the companion index, if one can be found.
     *
     * @param reads the reads source to use
     * @return a {@link ReadsBundle} containing reads and companion index, if it can be found
     */
    public static ReadsBundle<IOPath> resolveIndex(final IOPath reads) {
        return resolveIndex(reads, HtsPath::new);
    }

    /**
     * Find the companion index for a reads source, and create a new {@link ReadsBundle} containing the
     * reads and the companion index, if one can be found.
     *
     * An index can only be resolved for an IOPath that represents on a file system for which an NIO
     * provider is installed. Remote paths that use a protocol scheme for which no NIO file system is
     * available will (silently) not be resolved.
     *
     * @param reads the reads source to use
     * @param ioPathConstructor a function that takes a string and returns an IOPath-derived class of type <T>
     * @param <T> the IOPath-derived type of the IOPathResources in the new bundle
     * @return a {@link ReadsBundle} containing reads and companion index, if it can be found
     */
    public static <T extends IOPath> ReadsBundle<T> resolveIndex(
            final T reads,
            final Function<String, T> ioPathConstructor) {
        if (reads.hasFileSystemProvider()) {
            final Path index = SamFiles.findIndex(reads.toPath());
            if (index == null) {
                return new ReadsBundle<>(reads);
            } else {
                return new ReadsBundle<T>(reads, ioPathConstructor.apply(index.toUri().toString()));
            }
        }
        return new ReadsBundle<>(reads);
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

    // Try to infer the contentType/format, i.e., READS/BAM from an IOPath. Currently this
    // exists purely to check for logical inconsistencies. It can detect cases that are illogical
    // (an IOPath that has format CRAM, but file extension BAM), but it can't deterministically
    // and correctly infer the types in all cases without reproducing all the logic embedded in all the
    // codecs (i.e., an htsget IOPath ends in ".bam", but has format HTSGET_BAM, not BAM - detecting
    // that here would require parsing the entire IOPath structure, which is best left to the codecs
    // themselves). So for now its advisory, but maybe it should be abandoned altogether.
    private static <T extends IOPath> Optional<Tuple<String, String>> getInferredContentTypes(final T ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        final Optional<String> extension = ioPath.getExtension();
        if (extension.isPresent()) {
            final String ext = extension.get();
            if (ext.equals(FileExtensions.BAM)) {
                return Optional.of(new Tuple<>(BundleResourceType.CT_ALIGNED_READS, BundleResourceType.FMT_READS_BAM));
            } else if (ext.equals(FileExtensions.CRAM)) {
                return Optional.of(new Tuple<>(BundleResourceType.CT_ALIGNED_READS, BundleResourceType.FMT_READS_CRAM));
            } else if (ext.equals((FileExtensions.SAM))) {
                return Optional.of(new Tuple<>(BundleResourceType.CT_ALIGNED_READS, BundleResourceType.FMT_READS_SAM));
            }
            //TODO: finish this, else SRA, htsget,...
        }
        return Optional.empty();
    }

}
