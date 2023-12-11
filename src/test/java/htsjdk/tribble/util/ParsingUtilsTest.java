package htsjdk.tribble.util;


import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;


/**
 * Parsing utils tests
 */
public class ParsingUtilsTest extends HtsjdkTest {

    static final String AVAILABLE_FTP_URL = "ftp://ftp.broadinstitute.org/pub/igv/TEST/test.txt";
    static final String UNAVAILABLE_FTP_URL = "ftp://www.example.com/file.txt";

    static final String AVAILABLE_HTTP_URL = "https://www.google.com";
    static final String UNAVAILABLE_HTTP_URL = "http://www.unknownhostwhichshouldntexist.com";

    @Test
    public void testSplit1() {
        String[] tokens = new String[10];
        String blankColumnLine = "a\tb\t\td";
        int nTokens = ParsingUtils.split(blankColumnLine, tokens, '\t');
        Assert.assertEquals(nTokens,4);
        Assert.assertEquals(tokens[0],"a");
        Assert.assertEquals(tokens[1],"b");
        Assert.assertEquals(tokens[2],"");
        Assert.assertEquals(tokens[3],"d");
    }

    @Test
    public void testSplit2() {
        String[] tokens = new String[10];
        String blankColumnLine = "a\tb\t\td\t";
        int nTokens = ParsingUtils.split(blankColumnLine, tokens, '\t');
        Assert.assertEquals(nTokens,5);
        Assert.assertEquals(tokens[0],"a");
        Assert.assertEquals(tokens[1],"b");
        Assert.assertEquals(tokens[2],"");
        Assert.assertEquals(tokens[3],"d");
        Assert.assertEquals(tokens[4],"");
    }

    @Test
    public void testSplitWhitespace1() {
        String[] tokens = new String[10];
        String blankColumnLine = "a b\t\td";
        int nTokens = ParsingUtils.splitWhitespace(blankColumnLine, tokens);
        Assert.assertEquals(nTokens,4);
        Assert.assertEquals(tokens[0],"a");
        Assert.assertEquals(tokens[1],"b");
        Assert.assertEquals(tokens[2],"");
        Assert.assertEquals(tokens[3],"d");
    }

    @Test
    public void testSplitWhitespace2() {
        String[] tokens = new String[10];
        String blankColumnLine = "a b\t\td\t";
        int nTokens = ParsingUtils.splitWhitespace(blankColumnLine, tokens);
        Assert.assertEquals(nTokens,5);
        Assert.assertEquals(tokens[0],"a");
        Assert.assertEquals(tokens[1],"b");
        Assert.assertEquals(tokens[2],"");
        Assert.assertEquals(tokens[3],"d");
    }

    /**
     * Tests that the string "joined", when split by "delim" using ParsingUtils.split(String, char),
     * <ol>
     * <li>Ends up with the expected number of items</li>
     * <li>Ends up with the expected items</li>
     * <li>Ends up with the same items as when the split is performed using String.split</li>
     * <li>When re-joined (using ParsingUtils.join(String, Collection&gt;String&lt;) ) results in
     *    the original string</li>
     * </ol>
     *
     * @param joined
     * @param delim
     * @param expectedItems
     */
    private void testSplitJoinRoundtrip(String joined, char delim, List<String> expectedItems) {
        List<String> split = ParsingUtils.split(joined, delim);
        Assert.assertEquals(split.size(), expectedItems.size());
        Assert.assertEquals(joined.split(Character.toString(delim), -1), split.toArray());
        Assert.assertEquals(joined, ParsingUtils.join(Character.toString(delim), split));
    }

    @Test
    public void testSplitJoinEmptyItem() {
        testSplitJoinRoundtrip("a\tb\t\td", '\t', Arrays.asList("a", "b", "", "d"));
    }

    @Test
    public void testSplitJoinEmptyAtEnd() {
        testSplitJoinRoundtrip("a\tb\t\td\t", '\t', Arrays.asList("a", "b", "", "d", ""));
    }

    @Test
    public void testSplitJoinEmpty() {
        testSplitJoinRoundtrip("", '\t', Arrays.asList(""));
    }

    @Test
    public void testSplitJoinSingleItem() {
        testSplitJoinRoundtrip("a", '\t', Arrays.asList("a"));
    }

    @Test
    public void testSplitJoinEmptyFirst() {
        testSplitJoinRoundtrip("\ta\tb", '\t', Arrays.asList("", "a", "b"));
    }

    @Test
    public void testFileDoesExist() throws IOException{
        File tempFile = File.createTempFile(getClass().getSimpleName(), ".tmp");
        tempFile.deleteOnExit();
        testExists(tempFile.getAbsolutePath(), true);
        testExists(tempFile.toURI().toString(), true);;
    }

    @Test
    public void testFileDoesNotExist() throws IOException{
        File tempFile = File.createTempFile(getClass().getSimpleName(), ".tmp");
        tempFile.delete();
        testExists(tempFile.getAbsolutePath(), false);
        testExists(tempFile.toURI().toString(), false);
    }

    @Test
    public void testInMemoryNioFileDoesExist() throws IOException{
        try(FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path file = fs.getPath("/file");
            Files.createFile(file);
            testExists(file.toUri().toString(), true);
        }
    }

    @Test
    public void testInMemoryNioFileDoesNotExist() throws IOException{
        try(FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path file = fs.getPath("/file");
            testExists(file.toUri().toString(), false);
        }
    }

    @Test(groups = "ftp")
    public void testFTPDoesExist() throws IOException{
        testExists(AVAILABLE_FTP_URL, true);
    }

    @Test(groups = "ftp")
    public void testFTPNotExist() throws IOException{
        testExists(UNAVAILABLE_FTP_URL, false);
    }

    @Test
    public void testHTTPDoesExist() throws IOException{
        testExists(AVAILABLE_HTTP_URL, true);
    }

    @Test
    public void testHTTPNotExist() throws IOException{
        testExists(UNAVAILABLE_HTTP_URL, false);
    }


    private static void testExists(String path, boolean expectExists) throws IOException{
        Assert.assertEquals(ParsingUtils.resourceExists(path), expectExists);
    }

    @Test
    public void testFileOpenInputStream() throws IOException{
        File tempFile = File.createTempFile(getClass().getSimpleName(), ".tmp");
        tempFile.deleteOnExit();
        try(Writer writer = new BufferedWriter(new OutputStreamWriter(IOUtil.openFileForWriting(tempFile)))) {
            writer.write("hello");
        }
        testStream(tempFile.getAbsolutePath());
        testStream(tempFile.toURI().toString());
    }

    @Test
    public void testInMemoryNioFileOpenInputStream() throws IOException{
        try(FileSystem fs = Jimfs.newFileSystem(Configuration.unix())) {
            Path file = fs.getPath("/file");
            Files.write(file, "hello".getBytes(StandardCharsets.UTF_8));
            testStream(file.toUri().toString());
        }
    }

    @Test(groups = "ftp")
    public void testFTPOpenInputStream() throws IOException{
        testStream(AVAILABLE_FTP_URL);
    }

    @Test
    public void testHTTPOpenInputStream() throws IOException{
        testStream(AVAILABLE_HTTP_URL);
    }

    private static void testStream(String path) throws IOException{
        try(InputStream is = ParsingUtils.openInputStream(path)) {
            Assert.assertNotNull(is, "InputStream is null for " + path);
            int b = is.read();
            Assert.assertNotSame(b, -1);
        }
    }


}
