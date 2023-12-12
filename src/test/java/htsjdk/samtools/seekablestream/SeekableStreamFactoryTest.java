package htsjdk.samtools.seekablestream;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SeekableStreamFactoryTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    @DataProvider
    public Object[][] getSpecialCasePaths(){
        return new Object[][]{
                {"x", true},
                {"", true},
                {"http://broadinstitute.org", false},
                {"https://broadinstitute.org", false},
                {"ftp://broadinstitute.org", false},
                {"ftp://broadinstitute.org/file%20with%20spaces", false}
        };
    }

    @Test(dataProvider = "getSpecialCasePaths")
    @Deprecated
    public void testIsFilePath(String path, boolean expected) {
        Assert.assertEquals(SeekableStreamFactory.isFilePath(path), expected);
    }


    // this test isn't particularly useful since we're not testing the meaninful case of having the http-nio provider
    // installed
    @Test(dataProvider = "getSpecialCasePaths")
    public void testIsBeingHandledByLegacyUrlSupport(String path, boolean notExpected) {
        Assert.assertEquals(SeekableStreamFactory.isBeingHandledByLegacyUrlSupport(path), !notExpected);
    }

    @Test(dataProvider = "getSpecialCasePaths")
    public void testCanBeHandledByLegacyUrlSupport(String path, boolean notExpected){
        Assert.assertEquals(SeekableStreamFactory.canBeHandledByLegacyUrlSupport(path), !notExpected);
    }

    @DataProvider(name="getStreamForData")
    public Object[][] getStreamForData() throws MalformedURLException {
        return new Object[][] {
                { new File(TEST_DATA_DIR, "BAMFileIndexTest/index_test.bam").getAbsolutePath(),
                        new File(TEST_DATA_DIR, "BAMFileIndexTest/index_test.bam").getAbsolutePath() },
                { new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath(),
                        new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath() },
                { new URL("file://" + new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath()).toExternalForm(),
                        new File(TEST_DATA_DIR, "cram_with_bai_index.cram").getAbsolutePath() },
                { new URL(TestUtil.BASE_URL_FOR_HTTP_TESTS + "index_test.bam").toExternalForm(),
                        new URL(TestUtil.BASE_URL_FOR_HTTP_TESTS + "index_test.bam").toExternalForm() },
                { new URL(TestUtil.BASE_URL_FOR_HTTP_TESTS + "index_test.bam.bai").toExternalForm(),
                       new URL(TestUtil.BASE_URL_FOR_HTTP_TESTS + "index_test.bam.bai").toExternalForm() },
        };
    }

    @Test(dataProvider = "getStreamForData")
    public void testGetStreamFor(final String path, final String expectedPath) throws IOException {
        Assert.assertEquals(SeekableStreamFactory.getInstance().getStreamFor(path).getSource(), expectedPath);
    }


    @Test
    public void testPathWithEmbeddedSpace() throws IOException {
        final File testBam =  new File(TEST_DATA_DIR, "BAMFileIndexTest/index_test.bam");

        //create a temp dir with a space in the name and copy the test file there
        final File tempDir = IOUtil.createTempDir("test spaces").toFile();
        Assert.assertTrue(tempDir.getAbsolutePath().contains(" "));
        tempDir.deleteOnExit();
        final File inputBam = new File(tempDir, "index_test.bam");
        inputBam.deleteOnExit();
        IOUtil.copyFile(testBam, inputBam);

        // make sure the input string we use is URL-encoded
        final String inputString = Paths.get(inputBam.getAbsolutePath()).toUri().toString();
        Assert.assertFalse(inputString.contains(" "));
        Assert.assertTrue(inputString.contains("%20"));

        try (final SeekableStream seekableStream =
                     SeekableStreamFactory.getInstance().getStreamFor(inputString)) {
            final int BYTES_TO_READ = 10;
            Assert.assertEquals(seekableStream.read(new byte[BYTES_TO_READ], 0,BYTES_TO_READ), BYTES_TO_READ);
        }
    }

    @Test
    public void testGetSeekableStreamWorksOnJimfs() throws IOException {
        try(final FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path file = fs.getPath("/file");
            Files.writeString(file,"hello");
            try(final SeekableStream stream = SeekableStreamFactory.getInstance().getStreamFor(file.toUri().toString())){
                final byte[] bytes = stream.readAllBytes();
                Assert.assertEquals(new String(bytes, StandardCharsets.UTF_8), "hello");
            }
        }
    }
}
