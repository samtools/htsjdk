package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.reads.ReadsBundle;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

/**
 * Class with methods for resolving inputs and outputs to haploid reference encoders and decoders.
 *
 * Provides typesafe access layer over the {@link HtsCodecResolver} thats used by
 * the {@link HtsCodecRegistry} to manage hapolid reference codecs (see
 * {@link HtsCodecRegistry#getHapRefCodecResolver()}).
 * It exposes methods that accept common types, such as IOPath, with automatic conversion to types
 * appropriate for haploid references, and argument and return types that conform to
 * those used by {@link htsjdk.beta.plugin.hapref.HaploidReferenceCodec}s, such as
 * {@link htsjdk.beta.plugin.hapref.HaploidReferenceDecoder} and
 * {@link htsjdk.beta.plugin.hapref.HaploidReferenceEncoder}.
 */
public class HtsHaploidReferenceCodecs {

    @SuppressWarnings("unchecked")
    public static HaploidReferenceDecoder getHapRefDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "inputPath");
        final Bundle referenceBundle = new BundleBuilder().addPrimary(
                new IOPathResource(inputPath, BundleResourceType.HAPLOID_REFERENCE)).build();

        final HaploidReferenceCodec haploidReferenceCodec = HtsCodecRegistry.getHapRefCodecResolver()
                .resolveForDecoding(referenceBundle);

        return (HaploidReferenceDecoder) haploidReferenceCodec.getDecoder(referenceBundle, null);
    }
}
