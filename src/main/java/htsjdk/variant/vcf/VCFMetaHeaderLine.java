package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

import java.util.Map;

/**
 * A class representing META fields in the VCF header.
 */
public class VCFMetaHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    public VCFMetaHeaderLine(final String line, final VCFHeaderVersion version) {
        // We need to use the V4 parser directly, since the V3 parser requires ALL permissible/expected
        // tags to be supplied, which is inconsistent with modern structured header lines that allow
        // other tags. So let validateForVersion detect any version incompatibility, ie., if this is ever
        // called with a V3 version.
        super(VCFConstants.META_HEADER_KEY, new VCF4Parser().parseLine(line, expectedTagOrder));
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
