package htsjdk.tribble.util;


import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Parsing utils tests
 */
public class ParsingUtilsTest {

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
    public void testFTPDoesExist() throws IOException{
        tstExists(AVAILABLE_FTP_URL, true);
    }

    @Test
    public void testFTPNotExist() throws IOException{
        tstExists(UNAVAILABLE_FTP_URL, false);
    }

    @Test
    public void testHTTPDoesExist() throws IOException{
        tstExists(AVAILABLE_HTTP_URL, true);
    }

    @Test
    public void testHTTPNotExist() throws IOException{
        tstExists(UNAVAILABLE_HTTP_URL, false);
    }

    private void tstExists(String path, boolean expectExists) throws IOException{
        boolean exists = ParsingUtils.resourceExists(path);
        Assert.assertEquals(exists, expectExists);
    }

    @Test
    public void testFTPOpenInputStream() throws IOException{
        tstStream(AVAILABLE_FTP_URL);
    }

    @Test
    public void testHTTPOpenInputStream() throws IOException{
        tstStream(AVAILABLE_HTTP_URL);
    }

    private void tstStream(String path) throws IOException{
        InputStream is = ParsingUtils.openInputStream(path);
        Assert.assertNotNull(is, "InputStream is null for " + path);
        int b = is.read();
        Assert.assertNotSame(b, -1);
    }


}
