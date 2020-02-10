package htsjdk.variant.vcf;

/**
 * A class representing SAMPLE fields in the VCF header
 */
public class VCFSampleHeaderLine extends VCFSimpleHeaderLine {
    private static final long serialVersionUID = 1L;

    public VCFSampleHeaderLine(String line, VCFHeaderVersion version) {
        super(VCFConstants.SAMPLE_HEADER_KEY, VCFHeaderLineTranslator.parseLine(version, line, null));
    }

}
