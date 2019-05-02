/*
 * The MIT License
 *
 * Copyright (c) 2017 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;

/**
 * Test for SAMReadGroupRecordTest
 */
public class SAMSequenceRecordTest extends HtsjdkTest {

    @Test
    public void testGetSAMString() {
        final SAMSequenceRecord r = new SAMSequenceRecord("chr5_but_without_a_prefix", 271828);
        r.setSpecies("Psephophorus terrypratchetti");
        r.setAssembly("GRCt01");
        r.setMd5("7a6dd3d307de916b477e7bf304ac22bc");
        r.setTopology(SAMSequenceRecord.Topology.linear);
        Assert.assertEquals("@SQ\tSN:chr5_but_without_a_prefix\tLN:271828\tSP:Psephophorus terrypratchetti\tAS:GRCt01\tM5:7a6dd3d307de916b477e7bf304ac22bc\nTP:linear", r.getSAMString());
    }

    @DataProvider
    public Object[][] testIsSameSequenceData() {
        final SAMSequenceRecord rec1 = new SAMSequenceRecord("chr1", 100);
        final SAMSequenceRecord rec2 = new SAMSequenceRecord("chr2", 101);
        final SAMSequenceRecord rec3 = new SAMSequenceRecord("chr3", 0);
        final SAMSequenceRecord rec4 = new SAMSequenceRecord("chr1", 100);

        final String md5One = "1";
        final String md5Two = "2";
        final int index1 = 1;
        final int index2 = 2;

        return new Object[][]{
                new Object[]{rec1, rec1, md5One, md5One, index1, index1, true},
                new Object[]{rec1, null, md5One, md5One, index1, index1, false},
                new Object[]{rec1, rec4, md5One, md5One, index1, index1, true},
                new Object[]{rec1, rec4, md5One, md5One, index1, index2, false},
                new Object[]{rec1, rec3, md5One, md5Two, index1, index1, false},
                new Object[]{rec1, rec2, md5One, md5Two, index1, index1, false},
                new Object[]{rec1, rec4, md5One, null, index1, index1, true},
                new Object[]{rec1, rec4, null, md5One, index1, index1, true},
                new Object[]{rec1, rec4, md5One, md5One, index1, index2, false}
        };
    }

    @Test(dataProvider = "testIsSameSequenceData")
    public void testIsSameSequence(final SAMSequenceRecord rec1 , final SAMSequenceRecord rec2, final String md5One, final String md5Two,
                                   final int index1, final int index2, final boolean isSame) {
        if (rec2 != null) {
            rec2.setMd5(md5Two);
            rec2.setSequenceIndex(index2);
        }

        if (rec1 != null) {
            rec1.setMd5(md5One);
            rec1.setSequenceIndex(index1);
            Assert.assertEquals(rec1.isSameSequence(rec2), isSame);
        }
    }

    @Test
    public void testSetAndCheckDescription() {
        final SAMSequenceRecord record = new SAMSequenceRecord("Test", 1000);
        Assert.assertNull(record.getDescription());
        final String description = "A description.";
        record.setDescription(description);
        Assert.assertEquals(record.getDescription(), description);
    }

    @Test
    public void testSetAndCheckTopology() {
        final SAMSequenceRecord record = new SAMSequenceRecord("Test", 1000);
        Assert.assertNull(record.getTopology());
        final SAMSequenceRecord.Topology topology = SAMSequenceRecord.Topology.circular;
        record.setTopology(topology);
        Assert.assertEquals(record.getTopology(), topology);
    }

    @DataProvider
    public Object[][] illegalSequenceNames(){
        return new Object[][]{
                {"space "},
                {"comma,"},
                {"lbrace["},
                {"rbrace]"},
                {"slash\\"},
                {"smaller<"},
                {"bigger<"},
                {"lparen("},
                {"rparen)"},
                {"lbracket{"},
                {"rbracket}"}};
    }

    @Test(dataProvider = "illegalSequenceNames", expectedExceptions = SAMException.class)
    public void testIllegalSequenceNames(final String sequenceName){
        new SAMSequenceRecord(sequenceName,100);
    }
}
