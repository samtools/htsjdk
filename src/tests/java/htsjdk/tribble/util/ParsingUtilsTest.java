package htsjdk.tribble.util;


import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


/**
 * Parsing utils tests
 */
public class ParsingUtilsTest {

    static final String AVAILABLE_FTP_URL = "ftp://ftp.broadinstitute.org/pub/igv/TEST/test.txt";
    static final String UNAVAILABLE_FTP_URL = "ftp://www.example.com/file.txt";

    static final String AVAILABLE_HTTP_URL = "https://www.google.com";
    static final String UNAVAILABLE_HTTP_URL = "http://www.unknownhostwhichshouldntexist.com";
    
    @Test
    public void testVectorSplitContainers() {
    	List<String> token_vector = new Vector<String>(10);
    	List<String> token_arraylist = new ArrayList<String>(10);
    	String splitStr = "a\tb\tc";
    	int nTokens = ParsingUtils.split(splitStr, token_vector, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertEquals(token_vector.get(0),"a");
        Assert.assertEquals(token_vector.get(1),"b");
        Assert.assertEquals(token_vector.get(2),"c");
        
        nTokens = ParsingUtils.split(splitStr, token_arraylist, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertEquals(token_arraylist.get(0),"a");
        Assert.assertEquals(token_arraylist.get(1),"b");
        Assert.assertEquals(token_arraylist.get(2),"c");
    }
    
    @Test
    public void testVectorSplitTypical() {
    	List<String> tokens = new ArrayList<String>(10);
    	String splitStr = "a\tb\tc";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertEquals(tokens.get(0),"a");
        Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"c");    		
    }

    @Test
    public void testVectorSplitExpand() {
    	Vector<String> tokens = new Vector<String>(2);
    	String splitStr = "a\tb\tc";
    	
    	// make sure the capacity is less than the # of tokens above
    	Assert.assertTrue(tokens.capacity() < 3);
    	
    	int nTokens = ParsingUtils.split(splitStr, (List<String>) tokens, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertTrue(tokens.capacity() > 2);
    	Assert.assertEquals(tokens.get(0),"a");
        Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"c");
    }
    
    @Test
    public void testVectorSplitNonemptyVector() {
    	List<String> tokens = new ArrayList<String>(10);
        tokens.add("old_data");
        
        // Make sure our vector is non-empty
        Assert.assertTrue(tokens.size() > 0);
        Assert.assertEquals(tokens.get(0), "old_data");

    	String splitStr = "a\tb\tc";
    	
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertEquals(tokens.get(0),"a");
        Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"c");
    }
    
    @Test
    public void testVectorSplitEmptyCell() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "a\tb\t\td";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 4);
    	Assert.assertEquals(tokens.get(0),"a");
        Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"");
        Assert.assertEquals(tokens.get(3),"d");
    	
    }
    
    @Test
    public void testVectorSplitEndingToken() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "a\tb\t\td\t";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 5);
    	Assert.assertEquals(tokens.get(0),"a");
        Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"");
        Assert.assertEquals(tokens.get(3),"d");
        Assert.assertEquals(tokens.get(4),"");
    }
    
    @Test
    public void testVectorSplitEmpty() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 1);
    	Assert.assertEquals(tokens.get(0),"");
    }
    
    @Test
    public void testVectorSplitOnlyToken() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "\t";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 2);
    	Assert.assertEquals(tokens.get(0),"");
    	Assert.assertEquals(tokens.get(1),"");
    }
    
    @Test
    public void testVectorSplitStartToken() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "\tb\td";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 3);
    	Assert.assertEquals(tokens.get(0),"");
    	Assert.assertEquals(tokens.get(1),"b");
        Assert.assertEquals(tokens.get(2),"d");
    }
    
    @Test
    public void testVectorSplitStartMultiToken() {
    	List<String> tokens = new ArrayList<String>(10);
        String splitStr = "\t\tb\td";
    	int nTokens = ParsingUtils.split(splitStr, tokens, '\t');
    	Assert.assertEquals(nTokens, 4);
    	Assert.assertEquals(tokens.get(0),"");
    	Assert.assertEquals(tokens.get(1),"");
    	Assert.assertEquals(tokens.get(2),"b");
        Assert.assertEquals(tokens.get(3),"d");
    }
    
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
