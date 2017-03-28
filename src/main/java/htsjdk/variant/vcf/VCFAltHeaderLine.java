package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;
import htsjdk.tribble.TribbleException;

import java.util.*;

//TODO: Should we validate these alt allele types ?
// Structural Variants
//   In symbolic alternate alleles for imprecise structural variants, the ID field indicates the type of structural variant,
//   and can be a colon-separated list of types and subtypes. ID values are case sensitive strings and must not contain
//   whitespace or angle brackets. The first level type must be one of the following:
//        DEL Deletion relative to the reference
//        INS Insertion of novel sequence relative to the reference
//        DUP Region of elevated copy number relative to the reference
//        INV Inversion of reference sequence
//        CNV Copy number variable region (may be both deletion and duplication)
//   The CNV category should not be used when a more specific category can be applied. Reserved subtypes include:
//        DUP:TANDEM Tandem duplication
//        DEL:ME Deletion of mobile element relative to the reference
//        INS:ME Insertion of a mobile element relative to the reference
//   IUPAC ambiguity codes
//   Symbolic alleles can be used also to represent genuinely ambiguous data in VCF, for example:
//        ##ALT=<ID=R,Description="IUPAC code R = A/G">
//        ##ALT=<ID=M,Description="IUPAC code M = A/C">

/**
 * A class representing ALT fields in the VCF header
 */
public class VCFAltHeaderLine extends VCFStructuredHeaderLine {
    private static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFHeader.class);

    private static List<String> expectedTags = Collections.unmodifiableList(
            new ArrayList<String>(2) {{
            add(ID_ATTRIBUTE);
            add(DESCRIPTION_ATTRIBUTE);
        }}
    );

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version) {
        // We need to call the V4 parser directly since the V3 parser requires expected tags; validateForVersion
        // will detect the version incompatibility if we're called on behalf of V3
        super(VCFConstants.ALT_HEADER_KEY, new VCF4Parser().parseLine(line, expectedTags));
        validateForVersion(version);
    }

    public VCFAltHeaderLine(final String id, final String description) {
        super(VCFConstants.ALT_HEADER_KEY,
            new LinkedHashMap<String, String>() {{
                put(ID_ATTRIBUTE, id);
                put(DESCRIPTION_ATTRIBUTE, description);
            }}
        );
    }

    /**
     * Validate that this header line conforms to the target version.
     */
    @Override
    public void validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        super.validateForVersion(vcfTargetVersion);
        //TODO: NOTE: should we have this V4.0 threshold ?
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
