package htsjdk.variant.vcf;

import htsjdk.samtools.util.Log;

import java.util.*;

/**
 * A class representing ALT fields in the VCF header
 */
public class VCFAltHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;
    protected final static Log logger = Log.getInstance(VCFHeader.class);

    private static List<String> expectedTags = Collections.unmodifiableList(
            new ArrayList<String>(2) {{
            add(ID_ATTRIBUTE);
            add(DESCRIPTION_ATTRIBUTE);
        }}
    );

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version) {
        // Honor the requested version to choose the parser, and let validateForVersion figure out
        // whether that version is valid for this line (for example, if this is called with a pre-4.0 version)
        super(VCFConstants.ALT_HEADER_KEY, VCFHeaderLineTranslator.parseLine(version, line, expectedTags));
        validateForVersionOrThrow(version);
    }

    public VCFAltHeaderLine(final String id, final String description) {
        super(VCFConstants.ALT_HEADER_KEY,
            new LinkedHashMap<String, String>() {{
                put(ID_ATTRIBUTE, id);
                put(DESCRIPTION_ATTRIBUTE, description);
            }}
        );
    }

    @Override
    public Optional<VCFValidationFailure<VCFHeaderLine>> validateForVersion(final VCFHeaderVersion vcfTargetVersion) {
        //TODO: Should we validate/constrain these to match the 4.3 spec constraints ?
        if (!vcfTargetVersion.isAtLeastAsRecentAs(VCFHeaderVersion.VCF4_0)) {
            final VCFValidationFailure<VCFHeaderLine> validationFailure = new VCFValidationFailure<>(
                    vcfTargetVersion,
                    this,
                    String.format("%s header lines are not allowed in VCF version %s headers", getKey(), vcfTargetVersion));
            if (VCFUtils.isStrictVCFVersionValidation()) {
                return Optional.of(validationFailure);
            } else {
                logger.warn(validationFailure.getFailureMessage());
            }
        }

        return super.validateForVersion(vcfTargetVersion);
    }
}
