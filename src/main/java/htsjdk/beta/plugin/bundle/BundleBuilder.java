package htsjdk.beta.plugin.bundle;

import htsjdk.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * A builder class for {@link Bundle}s.
 */
public final class BundleBuilder {

    private final List<BundleResource> resources = new ArrayList<>();
    private String primaryResource;

    /**
     * Start a new bundle builder.
     */
    public BundleBuilder() { }

    /**
     * Add the primary resource to the bundle. The content type of resource will be the bundle's primary key.
     *
     * @param resource the resource which will be the primary resource for the bundle
     * @return this {@link BundleBuilder}
     */
    public BundleBuilder addPrimary(final BundleResource resource) {
        ValidationUtils.nonNull(resource, "resource");
        if (primaryResource != null) {
                throw new IllegalStateException(String.format(
                        "Can't add primary resource %s to a bundle that already has primary resource %s",
                        resource.getContentType(),
                        primaryResource));
        }
        primaryResource = resource.getContentType();
        addSecondary(resource);
        return this;
    }

    /**
     * Add a (non-primary) resource to the bundle.
     *
     * @param resource the resource to be added
     * @return this {@link BundleBuilder}
     */
    public BundleBuilder addSecondary(final BundleResource resource) {
        ValidationUtils.nonNull(resource, "resource");
        resources.add(resource);
        return this;
    }

    /**
     * Create a bundle from this builder's accumulated builder state, and reset the builder state. At
     * least one (primary) resource must have been previously added to create a valid bundle.
     *
     * @return a {@link Bundle}
     * @throws IllegalStateException if no primary resouuce has been added
     */
    public Bundle build() {
        if (primaryResource == null) {
            throw new IllegalStateException("A bundle must have a primary resource.");
        }
        final Bundle bundle = new Bundle(primaryResource, resources);
        primaryResource = null;
        resources.clear();
        return bundle;
    }
}


