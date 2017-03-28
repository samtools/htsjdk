package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

import java.util.Collections;
import java.util.Map;

/**
 * A class representing META fields in the VCF header
 */
public class VCFMetaHeaderLine extends VCFStructuredHeaderLine {
    private static final long serialVersionUID = 1L;

    public VCFMetaHeaderLine(final String line, final VCFHeaderVersion version) {
        // We need to call the V4 parser directly since the V3 parser requires expected tags; validateForVersion
        // will detect the version incompatibility if we're called on behalf of V3
        super(VCFConstants.META_HEADER_KEY, new VCF4Parser().parseLine(line, null));
        validateForVersion(version);
    }

    public VCFMetaHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.META_HEADER_KEY, mapping);
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            throw new TribbleException.InvalidHeader(
                    String.format("%s header lines are not allowed in VCF version %s headers",
                            getKey(),
                            vcfTargetVersion.toString())
            );
        }
        super.validateForVersion(vcfTargetVersion);
    }

}
