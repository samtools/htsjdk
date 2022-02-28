package htsjdk.variant.vcf;

import java.util.Map;
import java.util.Optional;

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
        validateForVersionOrThrow(version);
    }

    public VCFMetaHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.META_HEADER_KEY, mapping);
    }

    @Override
    public Optional<VCFValidationFailure<VCFHeaderLine>> validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            return Optional.of(
                    new VCFValidationFailure<>(
                            vcfTargetVersion,
                            this,
                        String.format("%s header lines are not allowed in VCF version %s headers",
                        getKey(),
                        vcfTargetVersion
                )));
        }

        return super.validateForVersion(vcfTargetVersion);
    }

}
