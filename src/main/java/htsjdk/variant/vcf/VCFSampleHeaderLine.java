package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;
import htsjdk.utils.Utils;

import java.util.Collections;
import java.util.Map;

/**
 * TODO: these are not well defined in the VCF 4.3 spec; they appear to require IDs,
 * TODO: and have lots of possible attributes
 */
public class VCFSampleHeaderLine extends VCFStructuredHeaderLine {

    private static final long serialVersionUID = 1L;

    public VCFSampleHeaderLine(String line, VCFHeaderVersion version) {
        // We need to call the V4 parser directly since the V3 parser requires expected tags; validateForVersion
        // will detect the version incompatibility if we're called on behalf of V3
        super(VCFConstants.SAMPLE_HEADER_KEY, new VCF4Parser().parseLine(line, null));
        validateForVersion(version);
    }

    public VCFSampleHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.SAMPLE_HEADER_KEY, mapping);
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        super.validateForVersion(vcfTargetVersion);
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)) {
            final String message = String.format("%s header lines are not allowed in VCF version %s headers",
                    getKey(),
                    vcfTargetVersion.toString());
            if (VCFUtils.getStrictVCFVersionValidation()) {
                throw new TribbleException.InvalidHeader(message);
            } else {
                logger.warn(message);
            }
        }
    }

}
