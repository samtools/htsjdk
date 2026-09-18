package htsjdk.variant.vcf;

import java.util.*;

/**
 * A class representing ALT fields in the VCF header
 */
public class VCFAltHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    private static final List<String> EXPECTED_TAGS = List.of(ID_ATTRIBUTE, DESCRIPTION_ATTRIBUTE);

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version) {
        super(VCFConstants.ALT_HEADER_KEY, VCFHeaderLineTranslator.parseLine(version, line, EXPECTED_TAGS));
    }
}
