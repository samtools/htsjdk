package htsjdk.variant.utils;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.vcf.VCFHeader;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

public class VCFHeaderReaderTest extends HtsjdkTest {
    @DataProvider(name = "files")
    Object[][] pathsData() {

        final String TEST_DATA_DIR = "src/test/resources/htsjdk/variant/";
        return new Object[][]{
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.bcf"},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf"},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.bgz"},
                {TEST_DATA_DIR + "VcfThatLacksAnIndex.vcf.gz"},
        };
    }

    @Test(dataProvider = "files")
    public void testReadHeaderFrom(final String file) throws IOException {
        VCFHeader vcfHeader = VCFHeaderReader.readHeaderFrom(new SeekableFileStream(new File(file)));
        Assert.assertNotNull(vcfHeader);
    }

    @DataProvider
    public Object[][] invalidFiles(){
        return new Object[][] {
                { new File("src/test/resources/htsjdk/samtools/empty.bam")},
                {new File("src/test/resources/htsjdk/variant/corrupt_file_that_starts_with_#.vcf")}
        };
    }

    @Test(dataProvider = "invalidFiles", expectedExceptions = TribbleException.InvalidHeader.class)
    public void testReadHeaderForInvalidFile(File file) throws IOException {
        VCFHeaderReader.readHeaderFrom(new SeekableFileStream(file));
    }
}
