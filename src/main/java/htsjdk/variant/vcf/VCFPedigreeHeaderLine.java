package htsjdk.variant.vcf;

/**
 * A class representing PEDIGREE fields in the VCF header
 */
public class VCFPedigreeHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    public VCFPedigreeHeaderLine(String line, VCFHeaderVersion version) {
        super(VCFConstants.PEDIGREE_HEADER_KEY, new VCF4Parser().parseLine(line, null));
    }

}
