package htsjdk.samtools.seekablestream;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

public class SeekableStreamFactoryTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @Test
    public void testIsFilePath() throws Exception {
        Assert.assertEquals(SeekableStreamFactory.isFilePath("x"), true);
        Assert.assertEquals(SeekableStreamFactory.isFilePath(""), true);
        Assert.assertEquals(SeekableStreamFactory.isFilePath("http://broadinstitute.org"), false);
        Assert.assertEquals(SeekableStreamFactory.isFilePath("https://broadinstitute.org"), false);
        Assert.assertEquals(SeekableStreamFactory.isFilePath("ftp://broadinstitute.org"), false);
    }

    @DataProvider(name="getStreamForData")
    public Object[][] getStreamForData() throws Exception {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "BAMFileIndexTest/index_test.bam").getAbsolutePath(),
                        new File(TEST_DATA_DIR, "BAMFileIndexTest/index_test.bam").getAbsolutePath() },
                { new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath(),
                        new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath() },
                { new URL("file://" + new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath()).toExternalForm(),
                        new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath() },
                { new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam").toExternalForm(),
                        new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam").toExternalForm() },
                { new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam.bai").toExternalForm(),
                       new URL("http://www.broadinstitute.org/~picard/testdata/index_test.bam.bai").toExternalForm() }
        };
    }

    @Test(dataProvider = "getStreamForData")
    public void testGetStreamFor(final String path, final String expectedPath) throws IOException {
        Assert.assertEquals(SeekableStreamFactory.getInstance().getStreamFor(path).getSource(), expectedPath);
    }

}
