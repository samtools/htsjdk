package htsjdk.beta.io.bundle;

import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.*;

/**
 * An immutable collection of related resources, including a (single, required) primary resource, such as "reads",
 * "variants", "features", or "reference", plus zero or more related secondary resources ("index", "dictionary",
 * "MD5", etc.).
 * <p>
 * Each resource in a {@link Bundle} is represented by a {@link BundleResource}, which in turn describes
 * a binding mechanism for that resource (such as an {@link IOPath} in the case of a URI, Path or file
 * name; or an input or output stream), and a "content type" string that is unique within that
 * {@link Bundle}. Any string can be used as a content type. Predefined content type strings are defined
 * in {@link BundleResourceType}.
 * <p>
 * A {@link Bundle} must have one resource that is designated as the "primary" resource, specified
 * by a content type string. A resource with "primary content type" is guaranteed to be present in
 * the {@link Bundle}.
 * <p>
 * Since each resource in a {@link Bundle} has a content type that is unique within that {@link Bundle},
 * a Bundle can not be used to represent a list of similar items where each item is equivalent to
 * each other item (i.e., a list of shards, where each shard in the list is equivalent to each other
 * shard). Rather {@link Bundle}s are used to represent related resources where each resource has a unique
 * character or role relative to the other resources (i.e., a "reads" resource and a corresponding "index"
 * resource).
 * <p>
 * Bundles that contain only serializable ({@link IOPathResource}) resources may be serialized to, and
 * deserialized from JSON.
 *
 * Note that the order of resources in a bundle is not significant, and is not guaranteed to be preserved.
 */
public class Bundle implements Iterable<BundleResource>, Serializable {
    private static final long serialVersionUID = 1L;

    // Note that this uses LinkedHashMap to preserve the input order of the resources,
    // but that order is not preserved when round tripping through JSON.
    private final Map<String, BundleResource> resources = new LinkedHashMap<>(); // content type -> resource
    private final String primaryContentType;

    /**
     * Create a new bundle from an existing resource collection.
     *
     * @param primaryContentType the content type of the primary resource in this bundle. may not be null.
     *                           a resource with this content type must be included in resources
     * @param resources resources to include in this bundle, may not be null or empty
     */
    public Bundle(final String primaryContentType, final Collection<BundleResource> resources) {
        ValidationUtils.nonNull(primaryContentType, "primary content type");
        ValidationUtils.validateArg(primaryContentType.length() > 0,
                "A non-zero length primary resource content type must be provided");
        ValidationUtils.nonNull(resources, "resource collection");
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("A bundle must contain at least one resource");
        }

        resources.forEach(r -> {
            if (null != this.resources.putIfAbsent(r.getContentType(), r)) {
                throw new IllegalArgumentException(
                        String.format("Attempt to add a duplicate resource for bundle key: %s", r.getContentType()));
            }
        });
        this.primaryContentType = primaryContentType;

        // validate that the primary resource actually exists in the resources
        if (!this.resources.containsKey(primaryContentType)) {
            throw new IllegalArgumentException(
                    String.format("Primary resource content type %s is not present in the bundle's resources",
                            primaryContentType));
        }
    }

    /**
     * Get the BundleResource for the provided targetContentType string.
     *
     * @param targetContentType the content type to be retrieved from the bundle
     * @return an Optional<BundleResource> that contains the targetContent type
     */
    public Optional<BundleResource> get(final String targetContentType) {
        ValidationUtils.nonNull(targetContentType, "target content string");
        return Optional.ofNullable(resources.get(targetContentType));
    }

    /**
     * Get the BundleResource for the provided targetContentType string, or throw if
     * no such resource exists.
     *
     * @param requiredContentType the content type to be retrieved from the bundle
     * @return a BundleResource of type targetContentType
     * @throws IllegalArgumentException if the targetContentType resource isn't present in the bundle
     */
    public BundleResource getOrThrow(final String requiredContentType) {
        ValidationUtils.nonNull(requiredContentType, "target content string");
        return get(requiredContentType).orElseThrow(
                        () -> new IllegalArgumentException(
                                String.format("No resource found in bundle %s with content type %s",
                                        this,
                                        requiredContentType
                                        )));
    }

    /**
     * Get the primary content type for this bundle.
     *
     * @return the primary content type for this bundle
     */
    public String getPrimaryContentType() { return primaryContentType; }

    /**
     * Get the primary {@link BundleResource} for this bundle.
     *
     * @return the primary {@link BundleResource} for this bundle.
     */
    public BundleResource getPrimaryResource() {
        return resources.get(primaryContentType);
    }

    /**
     * Get the collection of resources from this {@link Bundle}.
     */
    public Collection<BundleResource> getResources() {
        return resources.values();
    }

    /**
     * Get an iterator of BundleResources for this bundle.
     *
     * @return iterator of BundleResources for this bundle.
     */
    @Override
    public Iterator<BundleResource> iterator() { return resources.values().iterator(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Bundle that = (Bundle) o;

        if (!resources.equals(that.resources)) return false;
        return primaryContentType.equals(that.primaryContentType);
    }

    @Override
    public int hashCode() {
        int result = resources.hashCode();
        result = 31 * result + primaryContentType.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return String.format("%s/%d resource(s)", primaryContentType, resources.size());
    }

    // compare two bundles for equality without regard for resource order
    public static boolean equalsIgnoreOrder(final Bundle bundle1, final Bundle bundle2) {
        if (bundle1 == null || bundle2 == null) {
            return false;
        } else if (!bundle1.getPrimaryContentType().equals(bundle2.getPrimaryContentType())) {
            return false;
        } else if (bundle1.getResources().size() != bundle2.getResources().size()) {
            return false;
        }
        final HashSet<BundleResource> bundle1Set = new HashSet<>(bundle1.getResources());
        final HashSet<BundleResource> bundle2Set = new HashSet<>(bundle2.getResources());
        return bundle1Set.equals(bundle2Set);
    }
}
