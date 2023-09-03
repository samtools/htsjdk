package htsjdk.variant.vcf;

import java.util.Map;
import java.util.Optional;

/**
 * A class representing PEDIGREE fields in the VCF header. Applicable starting with version VCFv4.3.
 *
 * ##PEDIGREE=<ID=TumourSample,Original=GermlineID>
 * ##PEDIGREE=<ID=SomaticNonTumour,Original=GermlineID>
 * ##PEDIGREE=<ID=ChildID,Father=FatherID,Mother=MotherID>
 * ##PEDIGREE=<ID=SampleID,Name_1=Ancestor_1,...,Name_N=Ancestor_N>
 */
public class VCFPedigreeHeaderLine extends VCFSimpleHeaderLine {

    private static final long serialVersionUID = 1L;

    public VCFPedigreeHeaderLine(String line, VCFHeaderVersion version) {
        // We need to use the V4 parser directly, since the V3 parser requires ALL permissible/expected
        // tags to be supplied, which is inconsistent with modern structured header lines that allow
        // other tags. So let validateForVersion detect any version incompatibility, ie., if this is ever
        // called with a V3 version.
        super(VCFConstants.PEDIGREE_HEADER_KEY, new VCF4Parser().parseLine(line, expectedTagOrder));
        validateForVersionOrThrow(version);
    }

    public VCFPedigreeHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.PEDIGREE_HEADER_KEY, mapping);
    }

    @Override
    public Optional<VCFValidationFailure<VCFHeaderLine>> validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
            // previous to VCFv4.3, the PEDIGREE line did not have an ID. Such lines are not modeled by this
            // class (since it is derived from VCFSimpleHeaderLine). Therefore instances of this class always
            // represent VCFv4.3 or higher. So throw if the requested version is less than 4.3.
            final String message = String.format("%s header lines are not allowed in VCF version %s headers",
                getKey(),
                vcfTargetVersion
            );
            if (VCFUtils.isStrictVCFVersionValidation()) {
                return Optional.of(new VCFValidationFailure<>(vcfTargetVersion, this, message));
            } else {
                logger.warn(message);
            }
        }

        return super.validateForVersion(vcfTargetVersion);
    }

}
