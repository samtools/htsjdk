package htsjdk.beta.plugin.bundle;

import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable collection of related resources (a primary resource, such as "reads", "variants",
 * "features", or "reference"), plus zero or more related companion resources ("index", "dictionary",
 * "MD5", etc.).
 *
 * Each resource in a {@link Bundle} is represented by a {@link BundleResource}, which in turn describes
 * a binding mechanism for that resource (such as an {@link IOPath} in the case of a URI, Path or file
 * name; or an input or output stream), and a "content type" string that is unique within that
 * {@link Bundle}. Any string can be used as a content type. Predefined content type strings are defined
 * in {@link BundleResourceType}.
 *
 * A {@link Bundle} must have one resource that is designated as the "primary" resource, specified
 * by a content type string. A resource with "primary content type" is is guaranteed to be present in
 * the {@link Bundle}.
 *
 * Since each resource in a {@link Bundle} has a content type that is unique within that {@link Bundle},
 * a Bundle can not be used to represent a list of similar items where each item is equivalent to
 * each other item (i.e., a list of shards, where each shard in the list is equivalent to each other
 * shard). Rather {@link Bundle}s are used to represent related resources where each resource has a unique
 * character or role  relative to the other resources (i.e., a "reads" resource and a corresponding "index"
 * resource).
 *
 * Bundles that contain only serializable ({@link IOPathResource}) resources may be serialized to, and
 * deserialized from JSON.
 */
public class Bundle implements Iterable<BundleResource>, Serializable {
    private static final long serialVersionUID = 1L;

    private final Map<String, BundleResource> resources = new LinkedHashMap<>();
    private final String primaryContentType;

    /**
     * @param primaryContentType the content type of the primary resource in this bundle. may not be null.
     *                           a resource with this content type must be included in resources
     * @param resources resources to include in this bundle, may not be null or empty
     */
    public Bundle(final String primaryContentType, final Collection<BundleResource> resources) {
        ValidationUtils.nonNull(primaryContentType, "primary content type");
        ValidationUtils.validateArg(primaryContentType.length() > 0,
                "A non-zero length primary resource content type must be provided");
        ValidationUtils.nonNull(resources, "resource collection");
        ValidationUtils.nonEmpty(resources,"resource collection");

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
                    String.format("Primary resource content type %s is not present in the resource list",
                            primaryContentType));
        }
    }

    /**
     * Return the BundleResource for the provided targetContentType string.
     *
     * @param targetContentType the content type to be retrieved from the bundle
     * @return an Optional<BundleResource> that contains the targetContent type
     */
    public Optional<BundleResource> get(final String targetContentType) {
        ValidationUtils.nonNull(targetContentType, "target content string");
        return Optional.ofNullable(resources.get(targetContentType));
    }

    /**
     * Return the BundleResource for the provided targetContentType string, or throw if
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
                                        requiredContentType,
                                        this)));
    }

    /**
     * Return the primary content type for this bundle.
     * @return the primary content type for this bundle
     */
    public String getPrimaryContentType() { return primaryContentType; }

    /**
     * Return the primary {@link BundleResource} for this bundle.
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
     * Obtain an iterator of BundleResources for this bundle.
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
}
