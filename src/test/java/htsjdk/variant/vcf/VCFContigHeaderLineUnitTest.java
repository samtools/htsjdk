package htsjdk.variant.vcf;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceRecord;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class VCFContigHeaderLineUnitTest extends HtsjdkTest {

    @Test
    public void aContigLineFromASequenceRecordCarriesMd5UrlAndSpecies() {
        final SAMSequenceRecord record = new SAMSequenceRecord("chr1", 248956422);
        record.setMd5("6aef897c3d6ff0c78aff06ac189178dd");
        record.setAttribute(SAMSequenceRecord.URI_TAG, "https://example.com/GRCh38.fa");
        record.setSpecies("Homo sapiens");

        final VCFContigHeaderLine line = new VCFContigHeaderLine(record, "GRCh38");

        Assert.assertEquals(
                line.toString(),
                "contig=<ID=chr1,length=248956422,assembly=GRCh38,md5=6aef897c3d6ff0c78aff06ac189178dd,"
                        + "species=\"Homo sapiens\",URL=https://example.com/GRCh38.fa>");
    }

    @Test
    public void aContigLineFromASequenceRecordLeavesOutAbsentFields() {
        final VCFContigHeaderLine line = new VCFContigHeaderLine(new SAMSequenceRecord("chr1", 1000), null);

        Assert.assertEquals(line.toString(), "contig=<ID=chr1,length=1000>");
    }

    @Test
    public void getSAMSequenceRecordMapsMd5UrlAndSpeciesBack() {
        final VCFContigHeaderLine line = new VCFContigHeaderLine(
                "<ID=20,length=62435964,assembly=B36,md5=f126cdf8a6e0c7f379d618ff66beb2da,species=\"Homo sapiens\","
                        + "URL=ftp://x/y.fa>",
                VCFHeaderVersion.VCF4_2,
                VCFHeader.CONTIG_KEY,
                0);

        final SAMSequenceRecord record = line.getSAMSequenceRecord();

        Assert.assertEquals(record.getSequenceName(), "20");
        Assert.assertEquals(record.getSequenceLength(), 62435964);
        Assert.assertEquals(record.getAssembly(), "B36");
        Assert.assertEquals(record.getMd5(), "f126cdf8a6e0c7f379d618ff66beb2da");
        Assert.assertEquals(record.getAttribute(SAMSequenceRecord.URI_TAG), "ftp://x/y.fa");
        Assert.assertEquals(record.getSpecies(), "Homo sapiens");
    }

    @Test
    public void contigLinesAtTheSameIndexWithDifferentIDsCompareByID() {
        final VCFContigHeaderLine x = new VCFContigHeaderLine(Map.of("ID", "x"), 5);
        final VCFContigHeaderLine y = new VCFContigHeaderLine(Map.of("ID", "y"), 5);

        Assert.assertTrue(x.compareTo(y) < 0);
        Assert.assertTrue(y.compareTo(x) > 0);
    }

    @Test
    public void contigLinesCompareByIndexBeforeID() {
        final VCFContigHeaderLine b = new VCFContigHeaderLine(Map.of("ID", "b"), 0);
        final VCFContigHeaderLine a = new VCFContigHeaderLine(Map.of("ID", "a"), 1);

        Assert.assertTrue(b.compareTo(a) < 0);
        Assert.assertTrue(a.compareTo(b) > 0);
    }
}
