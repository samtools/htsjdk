package htsjdk.variant.vcf;

import java.util.*;

/**
 * A class representing ALT fields in the VCF header
 */
public class VCFAltHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    private static List<String> expectedTags = Collections.unmodifiableList(
            new ArrayList<String>(2) {{
                add(ID_ATTRIBUTE);
                add(DESCRIPTION_ATTRIBUTE);
            }}
    );

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version) {
        super(VCFConstants.ALT_HEADER_KEY, new VCF4Parser().parseLine(line, expectedTags));
    }

}
