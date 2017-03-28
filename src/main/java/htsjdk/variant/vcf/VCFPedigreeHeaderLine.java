package htsjdk.variant.vcf;

import htsjdk.tribble.TribbleException;

import java.util.Collections;
import java.util.Map;

/**
 * A class representing PEDIGREE fields in the VCF header
 *
 * ##PEDIGREE=<ID=TumourSample,Original=GermlineID>
 * ##PEDIGREE=<ID=SomaticNonTumour,Original=GermlineID>
 * ##PEDIGREE=<ID=ChildID,Father=FatherID,Mother=MotherID>
 * ##PEDIGREE=<ID=SampleID,Name_1=Ancestor_1,...,Name_N=Ancestor_N>
 * or a link to a database: ##pedigreeDB=URL
 */
public class VCFPedigreeHeaderLine extends VCFStructuredHeaderLine {

    private static final long serialVersionUID = 1L;

    public VCFPedigreeHeaderLine(String line, VCFHeaderVersion version) {

        // TODO: There are quite a few variants for expected tags (See above comment). Should we try to validate these?
        // TODO: IF not, we don't really need to model PEDIGREE as a separate class ?
        // We need to call the V4 parser directly since the V3 parser requires expected tags; validateForVersion
        // will detect the version incompatibility if we're called on behalf of V3
        super(VCFConstants.PEDIGREE_HEADER_KEY, new VCF4Parser().parseLine(line, null));
        validateForVersion(version);
    }

    public VCFPedigreeHeaderLine(final Map<String, String> mapping) {
        super(VCFConstants.PEDIGREE_HEADER_KEY, mapping);
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        super.validateForVersion(vcfTargetVersion);
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_3)) {
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
