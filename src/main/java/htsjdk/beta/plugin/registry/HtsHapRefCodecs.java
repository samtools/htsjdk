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

public class HtsHapRefCodecs {
    private static HtsCodecsByFormat<HaploidReferenceFormat, HaploidReferenceCodec> haprefCodecs = HtsCodecRegistry.getHapRefCodecs();

    //TODO: validate stream signature
    @SuppressWarnings("unchecked")
    public static HaploidReferenceDecoder getHapRefDecoder(final IOPath inputPath) {
        ValidationUtils.nonNull(inputPath, "inputPath");
        final Bundle referenceBundle = BundleBuilder.start().addPrimary(
                new IOPathResource(inputPath, BundleResourceType.REFERENCE)).getBundle();

        final HaploidReferenceCodec haploidReferenceCodec = haprefCodecs.resolveCodecForInput(
                referenceBundle,
                BundleResourceType.REFERENCE,
                HaploidReferenceFormat::mapContentSubTypeToFormat);

        return (HaploidReferenceDecoder) haploidReferenceCodec.getDecoder(referenceBundle, null);
    }
}
