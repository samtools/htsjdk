package htsjdk.variant.vcf;

/**
 * A class representing META fields in the VCF header
 */
public class VCFMetaHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    public VCFMetaHeaderLine(final String line, final VCFHeaderVersion version) {
        super(VCFConstants.META_HEADER_KEY, VCFHeaderLineTranslator.parseLine(version, line, null));
    }

}
