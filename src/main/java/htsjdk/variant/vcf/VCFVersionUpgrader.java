package htsjdk.variant.vcf;

import htsjdk.variant.variantcontext.writer.VCFVersionUpgradePolicy;

import java.util.Collection;

final class VCFVersionUpgrader {
    public static void getOutputVersion(final VCFHeader header, final VCFVersionUpgradePolicy policy) {
        // Guaranteed to not be null
        final VCFHeaderVersion currentVersion = header.getVCFHeaderVersion();
        switch (policy) {
            case ONLY_INFALLIBLE_UPGRADE:
                // 4.3+ lines are output as the latest version, pre-4.3 lines are output as 4.2
                final VCFHeaderVersion newVersion = currentVersion.isAtLeastAsRecentAs(VCFHeader.DEFAULT_VCF_VERSION)
                    ? VCFHeader.DEFAULT_VCF_VERSION
                    : VCFHeaderVersion.VCF4_2;
                header.addMetaDataLine(VCFHeader.makeHeaderVersionLine(newVersion));
            case UPGRADE_OR_FALLBACK:
                final Collection<VCFValidationFailure<VCFHeaderLine>> failures = header.getValidationErrors(VCFHeader.DEFAULT_VCF_VERSION);
                if (failures.isEmpty()) {
                    header.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
                }
                break;
            case UPGRADE_OR_FAIL:
                // If validation fails, simply pass the exception through
                header.addMetaDataLine(VCFHeader.makeHeaderVersionLine(VCFHeader.DEFAULT_VCF_VERSION));
                break;
        }
    }
}
