package htsjdk.variant.vcf;

import java.util.List;

public class VCFAltHeaderLine extends VCFSimpleHeaderLine implements VCFIDHeaderLine {

    public VCFAltHeaderLine(final String line, final VCFHeaderVersion version, final String key, final List<String> expectedTagOrdering) {
        super(key, VCFHeaderLineTranslator.parseLine(version, line, expectedTagOrdering));
    }

    public String getID() {return name;}
}
