package htsjdk.variant.vcf;


import htsjdk.tribble.TribbleException;

public class VCFPedigreeHeaderLine extends VCFSimpleHeaderLine implements VCFIDHeaderLine{

    public VCFPedigreeHeaderLine(String line, VCFHeaderVersion version) {
        super(line, version, "PEDIGREE", null);
        if (getID() == null) {throw new TribbleException.InvalidHeader("Invalid ##PEDIGREE line, requires an 'ID' field");}
    }

    public String getID() {
        return this.name;
    }
}
