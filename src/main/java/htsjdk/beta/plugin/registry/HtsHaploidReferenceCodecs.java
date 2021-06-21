package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

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
