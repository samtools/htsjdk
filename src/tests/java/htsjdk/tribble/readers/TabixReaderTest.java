package htsjdk.tribble.readers;


import htsjdk.tribble.TestUtils;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.testng.AssertJUnit.assertTrue;


/**
 * Created by IntelliJ IDEA.
 * User: jrobinso
 * Date: Jul 6, 2010
 * Time: 8:57:40 PM
 * To change this template use File | Settings | File Templates.
 */
public class TabixReaderTest {

    static String tabixFile = TestUtils.DATA_DIR + "tabix/trioDup.vcf.gz";
    static TabixReader tabixReader;
    static List<String> sequenceNames;

    @BeforeClass
    public void setup() throws IOException {
        tabixReader = new TabixReader(tabixFile);
        sequenceNames = new ArrayList<String>(tabixReader.getChromosomes());
    }

    @AfterClass
    public void teardown() throws Exception {
        tabixReader.close();
    }

    @Test
    public void testSequenceNames() {
        String[] expectedSeqNames = new String[24];
        for (int i = 1; i < 24; i++) {
            expectedSeqNames[i - 1] = String.valueOf(i);
        }
        expectedSeqNames[22] = "X";
        expectedSeqNames[23] = "Y";
        Assert.assertEquals(expectedSeqNames.length, sequenceNames.size());

        for (String s : expectedSeqNames) {
            Assert.assertTrue(sequenceNames.contains(s));
        }


    }
    
    @Test
    public void testSequenceSet() {
        Set<String> chroms= tabixReader.getChromosomes();
        Assert.assertFalse(chroms.isEmpty());
        Assert.assertTrue(chroms.contains("1"));
        Assert.assertFalse(chroms.contains("MT"));
        
    }


    @Test
    public void testIterators() throws IOException {
        TabixReader.Iterator iter=tabixReader.query("1", 1, 400);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter.next());
        Assert.assertNull(iter.next());
        
        iter=tabixReader.query("UN", 1, 100);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
        
        iter=tabixReader.query("UN:1-100");
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
       
        
        iter=tabixReader.query("1:10-1");
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
 
        iter=tabixReader.query(999999,9,9);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
        
        iter=tabixReader.query("1",Integer.MAX_VALUE-1,Integer.MAX_VALUE);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());
        
        final int pos_snp_in_vcf_chr1=327;
        
        iter=tabixReader.query("1",pos_snp_in_vcf_chr1,pos_snp_in_vcf_chr1);
        Assert.assertNotNull(iter);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter=tabixReader.query("1",pos_snp_in_vcf_chr1-1,pos_snp_in_vcf_chr1-1);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

        iter=tabixReader.query("1",pos_snp_in_vcf_chr1+1,pos_snp_in_vcf_chr1+1);
        Assert.assertNotNull(iter);
        Assert.assertNull(iter.next());

    }
    
    
    
    /**
     * Test reading a local tabix file
     *
     * @throws java.io.IOException
     */
    @Test
    public void testLocalQuery() throws IOException {

         TabixIteratorLineReader lineReader = new TabixIteratorLineReader(
                tabixReader.query(tabixReader.chr2tid("4"), 320, 330));

        int nRecords = 0;
        String nextLine;
        while ((nextLine = lineReader.readLine()) != null) {
            assertTrue(nextLine.startsWith("4"));
            nRecords++;
        }
        assertTrue(nRecords > 0);


    }

    /**
     * Test reading a tabix file over http
     *
     * @throws java.io.IOException
     */
    @Test
    public void testRemoteQuery() throws IOException {
        String tabixFile = "http://www.broadinstitute.org/~picard/testdata/igvdata/tabix/trioDup.vcf.gz";

        TabixReader tabixReader = new TabixReader(tabixFile);

        TabixIteratorLineReader lineReader = new TabixIteratorLineReader(
                tabixReader.query(tabixReader.chr2tid("4"), 320, 330));

        int nRecords = 0;
        String nextLine;
        while ((nextLine = lineReader.readLine()) != null) {
            assertTrue(nextLine.startsWith("4"));
            nRecords++;
        }
        assertTrue(nRecords > 0);

    }
}
