package htsjdk.beta.codecs.variants.vcf;

import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.utils.PrivateAPI;

/**
 * Utilities for VCF codec implementations.
 */
@PrivateAPI
public class VariantsCodecUtils {

    /**
     * @PrivateAPI
     *
     * Return true if the input {@link Bundle} contains a variants index resource
     *
     * @param inputBundle input {@link Bundle} to inspect
     * @return true if input {@link Bundle} contains a variants index resource
     */
    @PrivateAPI
    public static boolean bundleContainsIndex(final Bundle inputBundle) {
        return inputBundle.get(BundleResourceType.VARIANTS_INDEX).isPresent();
    }

    /**
     * @PrivateAPI
     *
     * The stated contract for decoders is that the index must be included in the bundle in order to use
     * index queries, but some codecs use readers that *always* tries to resolve the index, which would
     * violate that and allow some cases to work that shouldn't, so enforce the contract manually so that
     * someday when we use a different implementation, no backward compatibility issue will be introduced.
     *
     * @param inputBundle input {@link Bundle} to inspect
     */
    @PrivateAPI
    public static void assertBundleContainsIndex(final Bundle inputBundle) {
        if (!bundleContainsIndex(inputBundle)) {
            throw new IllegalArgumentException(String.format(
                    "To make index queries, an index resource must be provided in the resource bundle: %s",
                    inputBundle
            ));
        }
    }
}
