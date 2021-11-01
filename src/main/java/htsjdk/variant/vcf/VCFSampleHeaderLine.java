package htsjdk.variant.vcf;

import java.util.Map;
import java.util.Optional;

/**
 */
public class VCFSampleHeaderLine extends VCFSimpleHeaderLine {

    private static final long serialVersionUID = 1L;

    public VCFSampleHeaderLine(String line, VCFHeaderVersion version) {
        // We need to use the V4 parser directly, since the V3 parser requires ALL permissible/expected
        // tags to be supplied, which is inconsistent with modern structured header lines that allow
        // other tags. So let validateForVersion detect any version incompatibility, ie., if this is ever
        // called with a V3 version.
        super(VCFConstants.SAMPLE_HEADER_KEY, new VCF4Parser().parseLine(line, expectedTagOrder));
        validateForVersion(version);
    }

    public VCFSampleHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.SAMPLE_HEADER_KEY, mapping);
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public Optional<String> getValidationError(final VCFHeaderVersion vcfTargetVersion) {
        final Optional<String> superError = super.getValidationError(vcfTargetVersion);
        if (superError.isPresent()) {
            return superError;
        }

        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)) {
            final String message = String.format("%s header lines are not allowed in VCF version %s headers",
                getKey(),
                vcfTargetVersion
            );
            if (VCFUtils.isStrictVCFVersionValidation()) {
                return Optional.of(message);
            } else {
                logger.warn(message);
            }
        }

        return Optional.empty();
    }

}
