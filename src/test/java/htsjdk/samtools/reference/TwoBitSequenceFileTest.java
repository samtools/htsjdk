package htsjdk.samtools.reference;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMSequenceDictionary;

public class TwoBitSequenceFileTest  extends HtsjdkTest{
    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools/reference");
    private static final Path SEQUENCE_FILE_MASKED_2BIT = TEST_DATA_DIR.resolve("Homo_sapiens_assembly18.trimmed.masked.2bit");
    private static final Path SEQUENCE_FILE_UNMASKED_2BIT = TEST_DATA_DIR.resolve("Homo_sapiens_assembly18.trimmed.nomask.2bit");

    @DataProvider(name="homosapiens")
    public Object[][] provideSequenceFile() throws FileNotFoundException {
        return new Object[][] { new Object[]
                { SEQUENCE_FILE_MASKED_2BIT},
                { SEQUENCE_FILE_UNMASKED_2BIT},
                };
    }

    @Test(dataProvider="homosapiens")
    public void testOpenFile(final Path sequenceFile) throws IOException {
        TwoBitSequenceFile tbf = new TwoBitSequenceFile(sequenceFile);
        final SAMSequenceDictionary dict = tbf.getSequenceDictionary();
        Assert.assertNotNull(dict);
        Assert.assertEquals(dict.size(), 2);
        Assert.assertEquals(dict.getSequence(0).getSequenceName(), "chrM");
        Assert.assertEquals(dict.getSequence(0).getSequenceLength(), 16_571);
        Assert.assertEquals(dict.getSequence(1).getSequenceName(), "chr20");
        Assert.assertEquals(dict.getSequence(0).getSequenceLength(), 1_000_000);
        
        Assert.assertNull(tbf.getSequence("do_not_exists"));
        ReferenceSequence seq =tbf.getSequence("chrM");
        Assert.assertNotNull(seq);
        Assert.assertEquals(seq.getName(), "chrM");
        Assert.assertEquals(seq.length(), 16_571);
        tbf.getSequence("chr20");
        Assert.assertNotNull(seq);
        Assert.assertEquals(seq.getName(), "chr20");
        Assert.assertEquals(seq.length(), 1_000_000);

        final String chrM_100_120="GGAGCCGGAGCACCCTATGTC";
        for(int i=0;i<=20;i++) {
            seq =tbf.getSubsequenceAt("chrM", 100, 100+i);
            Assert.assertEquals(seq.getName(), "chrM");
            Assert.assertEquals(seq.length(), i+1);
            Assert.assertEquals(seq.getContigIndex() , 99);
            Assert.assertEquals(seq.getBaseString() , chrM_100_120.substring(0, i));
        }
        
        tbf.close();
    }
    
}
