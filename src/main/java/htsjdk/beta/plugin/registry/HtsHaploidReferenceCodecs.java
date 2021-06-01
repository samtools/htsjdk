package htsjdk.beta.plugin.registry;

import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.bundle.BundleBuilder;
import htsjdk.beta.plugin.bundle.BundleResourceType;
import htsjdk.beta.plugin.bundle.IOPathResource;
import htsjdk.beta.plugin.hapref.HapRefDecoderOptions;
import htsjdk.beta.plugin.hapref.HaploidReferenceCodec;
import htsjdk.beta.plugin.hapref.HaploidReferenceDecoder;
import htsjdk.beta.plugin.hapref.HaploidReferenceFormat;
import htsjdk.io.IOPath;
import htsjdk.utils.ValidationUtils;

import java.util.Optional;

public class HtsHaploidReferenceCodecs {
    private static HtsCodecsByFormat<HaploidReferenceFormat, HaploidReferenceCodec> haprefCodecs = HtsCodecRegistry.getHapRefCodecs();

    @SuppressWarnings("unchecked")
    public static HaploidReferenceDecoder getHapRefDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "inputPath");
        final Bundle referenceBundle = new BundleBuilder().addPrimary(
                new IOPathResource(inputPath, BundleResourceType.HAPLOID_REFERENCE)).build();

        final HaploidReferenceCodec haploidReferenceCodec = haprefCodecs.resolveCodecForInput(
                referenceBundle,
                BundleResourceType.HAPLOID_REFERENCE,
                HaploidReferenceFormat::mapContentSubTypeToFormat);

        return (HaploidReferenceDecoder) haploidReferenceCodec.getDecoder(referenceBundle, null);
    }
}
