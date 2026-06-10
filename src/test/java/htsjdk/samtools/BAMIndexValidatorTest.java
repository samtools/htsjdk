package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import java.io.IOException;
import java.nio.file.Path;
import org.testng.Assert;
import org.testng.annotations.Test;

public class BAMIndexValidatorTest extends HtsjdkTest {

    private static final Path BAM_FILE = Path.of("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private static final Path BAI_FILE = Path.of(BAM_FILE + ".bai");
    private static final Path CSI_FILE = Path.of(BAM_FILE + ".csi");

    @Test
    public void exhaustivelyTestIndexTest() throws IOException {

        BAMFileReader bamFileReader1 = new BAMFileReader(
                BAM_FILE,
                BAI_FILE,
                true,
                false,
                ValidationStringency.DEFAULT_STRINGENCY,
                new DefaultSAMRecordFactory());
        bamFileReader1.enableIndexCaching(true);
        BAMFileReader bamFileReader2 = new BAMFileReader(
                BAM_FILE,
                CSI_FILE,
                true,
                false,
                ValidationStringency.DEFAULT_STRINGENCY,
                new DefaultSAMRecordFactory());

        final SamReader samFileReader1 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader1, null);
        final SamReader samFileReader2 = new SamReader.PrimitiveSamReaderToSamReaderAdapter(bamFileReader2, null);

        int baiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader1);
        int csiCount = BamIndexValidator.exhaustivelyTestIndex(samFileReader2);

        Assert.assertEquals(baiCount, csiCount);
    }
}
