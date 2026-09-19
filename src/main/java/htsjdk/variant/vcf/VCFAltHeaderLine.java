package htsjdk.variant.vcf;

import java.util.*;

/**
 * A class representing ALT fields in the VCF header
 */
public class VCFAltHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    private static final List<String> EXPECTED_TAGS = List.of(ID_ATTRIBUTE, DESCRIPTION_ATTRIBUTE);

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version) {
        // ALT lines came in with VCF 4.0 and have only ever been written in its syntax, so a file that declares an
        // earlier version but carries one is read that way too
        super(
                VCFConstants.ALT_HEADER_KEY,
                parsedWithId(
                        VCFConstants.ALT_HEADER_KEY,
                        line,
                        VCFHeaderLineTranslator.parseLine(VCFHeaderVersion.VCF4_0, line, EXPECTED_TAGS)));
    }
}
