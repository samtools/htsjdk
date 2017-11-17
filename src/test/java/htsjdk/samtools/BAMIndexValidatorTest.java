package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class BAMIndexValidatorTest extends HtsjdkTest {

    private static final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static final File BAI_FILE = new File(BAM_FILE.getPath() + ".bai");
    private static final File CSI_FILE = new File(BAM_FILE.getPath() + ".csi");

    @Test
    public void exhaustivelyTestIndexTest () throws IOException {

        BAMFileReader bamFileReader1 = new BAMFileReader(BAM_FILE, BAI_FILE, true, false, ValidationStringency.DEFAULT_STRINGENCY, new DefaultSAMRecordFactory());
        bamFileReader1.enableIndexCaching(true);
        BAMFileReader bamFileReader2 = new BAMFileReader(BAM_FILE, CSI_FILE, true, false, ValidationStringency.DEFAULT_STRINGENCY, new DefaultSAMRecordFactory());

        final SamReader samFileReader1 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader1, null);
        final SamReader samFileReader2 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader2, null);

        int baiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader1);
        int csiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader2);

        Assert.assertEquals(baiCount, 5031);
        Assert.assertEquals(csiCount, 5013);
    }

}
