package htsjdk.beta.plugin.registry;

import htsjdk.beta.exception.HtsjdkException;
import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

/**
 * Class with methods for resolving inputs and outputs to haploid reference encoders and decoders.
 * <p>
 * Provides a typesafe layer over the {@link HtsCodecResolver} used by an {@link HtsCodecRegistry}
 * to manage {@link HaploidReferenceCodec}s (see {@link HtsCodecRegistry#getHaploidReferenceResolver()}).
 * <p>
 * Provides typesafe conversion of argument and return types to types that conform to those used by
 * {@link HaploidReferenceCodec}s, such as {@link htsjdk.beta.plugin.hapref.HaploidReferenceDecoder} and
 * {@link htsjdk.beta.plugin.hapref.HaploidReferenceEncoder}.
 */
public class HaploidReferenceResolver extends HtsCodecResolver<HaploidReferenceCodec> {

    /**
     * Create a new HaploidReferenceResolver.
     */
    public HaploidReferenceResolver() {
        super(BundleResourceType.HAPLOID_REFERENCE);
    }

    /**
     * Gat a {@link HaploidReferenceDecoder} for the given inputPath.
     *
     * @param inputPath the path to the resource to be decoded
     * @return a HaploidReferenceDecoder for the given inputPath
     * @throws HtsjdkException if no registered codecs can handle the resource
     * @throws HtsjdkPluginException if more than one codec claims to handle the resource. this usually indicates
     * that the registry contains an incorrectly written codec.
     */
    @SuppressWarnings("unchecked")
    public HaploidReferenceDecoder getHapRefDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "inputPath");
        final Bundle referenceBundle = new BundleBuilder().addPrimary(
                new IOPathResource(inputPath, BundleResourceType.HAPLOID_REFERENCE)).build();
        final HaploidReferenceCodec haploidReferenceCodec = resolveForDecoding(referenceBundle);
        return (HaploidReferenceDecoder) haploidReferenceCodec.getDecoder(referenceBundle, null);
    }
}
