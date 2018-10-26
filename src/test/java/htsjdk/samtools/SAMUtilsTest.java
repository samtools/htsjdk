/*
 * The MIT License
 *
 * Copyright (c) 2013 The Broad Institute
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
import java.util.List;

public class SAMUtilsTest extends HtsjdkTest {
    @Test
    public void testCompareMapqs() {
        Assert.assertEquals(SAMUtils.compareMapqs(0, 0), 0);
        Assert.assertEquals(SAMUtils.compareMapqs(255, 255), 0);
        Assert.assertEquals(SAMUtils.compareMapqs(1, 1), 0);
        Assert.assertTrue(SAMUtils.compareMapqs(0, 255) < 0);
        Assert.assertTrue(SAMUtils.compareMapqs(0, 1) < 0);
        Assert.assertTrue(SAMUtils.compareMapqs(255, 1) < 0);
        Assert.assertTrue(SAMUtils.compareMapqs(1, 2) < 0);

        Assert.assertTrue(SAMUtils.compareMapqs(255, 0) > 0);
        Assert.assertTrue(SAMUtils.compareMapqs(1, 0) > 0);
        Assert.assertTrue(SAMUtils.compareMapqs(1, 255) > 0);
        Assert.assertTrue(SAMUtils.compareMapqs(2, 1) > 0);
    }

    @Test
    public void testSimpleClippingOfRecord() {
        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setCigar(TextCigarCodec.decode("10M"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(6); // should overlap 5M
        record.setReadBases("AAAAAAAAAA".getBytes());

        final int numToClip = SAMUtils.getNumOverlappingAlignedBasesToClip(record);
        Assert.assertEquals(numToClip, 5);

        SAMUtils.clipOverlappingAlignedBases(record, numToClip, false); // Side-effects are OK

        Assert.assertTrue(record.getCigar().equals(TextCigarCodec.decode("5M5S")));
    }

    @Test
    public void testClippingOfRecordWithSoftClipBasesAtTheEnd() {
        /**
         * Tests that if we need to clip a read with soft-clipping at the end, it does the right thing.
         */

        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setCigar(TextCigarCodec.decode("5M5S"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(5); // should overlap 1M5S
        record.setReadBases("AAAAAAAAAA".getBytes());

        final int numToClip = SAMUtils.getNumOverlappingAlignedBasesToClip(record);
        Assert.assertEquals(numToClip, 1);

        SAMUtils.clipOverlappingAlignedBases(record, numToClip, false); // Side-effects are OK

        Assert.assertTrue(record.getCigar().equals(TextCigarCodec.decode("4M6S")));
    }

    @Test
    public void testClippingOfRecordWithInsertion() {
        /**
         * Tests that if we need to clip a read with an insertion that overlaps
         */

        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setCigar(TextCigarCodec.decode("5M1I5M"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(5); // should overlap the 1M1I5M
        record.setReadBases("AAAAAAAAAAA".getBytes());


        final int numToClip = SAMUtils.getNumOverlappingAlignedBasesToClip(record);
        Assert.assertEquals(numToClip, 7);

        SAMUtils.clipOverlappingAlignedBases(record, numToClip, false); // Side-effects are OK

        Assert.assertTrue(record.getCigar().equals(TextCigarCodec.decode("4M7S")));

    }

    @Test
    public void testClippingOfRecordWithDeletion() {
        /**
         * Tests that if we need to clip a read with an deletion that overlaps
         */

        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setCigar(TextCigarCodec.decode("5M1D5M"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(5); // should overlap the 1M1D5M
        record.setReadBases("AAAAAAAAAA".getBytes());

        final int numToClip = SAMUtils.getNumOverlappingAlignedBasesToClip(record);
        Assert.assertEquals(numToClip, 6);

        SAMUtils.clipOverlappingAlignedBases(record, numToClip, false); // Side-effects are OK
        Assert.assertTrue(record.getCigar().equals(TextCigarCodec.decode("4M6S")));

    }

    @Test
    public void testClippingOfRecordWithMateAtSamePosition() {
        /**
         * Tests that we clip the first end of a pair if we have perfect overlap of a pair
         */

        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setFirstOfPairFlag(true);
        record.setCigar(TextCigarCodec.decode("10M"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(1);
        record.setReadBases("AAAAAAAAAA".getBytes());

        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), 0);

        // now make it the second end
        record.setFirstOfPairFlag(false);
        record.setSecondOfPairFlag(true);
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), 10);
    }

    @Test
    public void testOtherCanonicalAlignments() {
        // setup the record
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("1", 1000));
        header.addSequence(new SAMSequenceRecord("2", 1000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadPairedFlag(true);
        record.setFirstOfPairFlag(true);
        record.setCigar(TextCigarCodec.decode("10M"));
        record.setReferenceIndex(0);
        record.setAlignmentStart(1);
        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(1);
        record.setReadPairedFlag(true);
        record.setSupplementaryAlignmentFlag(true);//spec says first 'SA' record will be the primary record

        record.setMateReferenceIndex(0);
        record.setMateAlignmentStart(100);
        record.setInferredInsertSize(99);

        record.setReadBases("AAAAAAAAAA".getBytes());
        record.setBaseQualities("##########".getBytes());
        // check no alignments if no SA tag */
        Assert.assertEquals(SAMUtils.getOtherCanonicalAlignments(record).size(),0);


        record.setAttribute(SAMTagUtil.SA,
                "2,500,+,3S2=1X2=2S,60,1;" +
                "1,191,-,8M2S,60,*;");

        // extract suppl alignments
        final List<SAMRecord> suppl = SAMUtils.getOtherCanonicalAlignments(record);
        Assert.assertNotNull(suppl);
        Assert.assertEquals(suppl.size(), 2);

        for(final SAMRecord other: suppl) {
            Assert.assertFalse(other.getReadUnmappedFlag());
            Assert.assertTrue(other.getReadPairedFlag());
            Assert.assertFalse(other.getMateUnmappedFlag());
            Assert.assertEquals(other.getMateAlignmentStart(),record.getMateAlignmentStart());
            Assert.assertEquals(other.getMateReferenceName(),record.getMateReferenceName());

            Assert.assertEquals(other.getReadName(),record.getReadName());
            if( other.getReadNegativeStrandFlag()==record.getReadNegativeStrandFlag()) {
                Assert.assertEquals(other.getReadString(),record.getReadString());
                Assert.assertEquals(other.getBaseQualityString(),record.getBaseQualityString());
                }
        }

        SAMRecord other = suppl.get(0);
        Assert.assertFalse(other.getSupplementaryAlignmentFlag());//1st of suppl and 'record' is supplementary
        Assert.assertEquals(other.getReferenceName(),"2");
        Assert.assertEquals(other.getAlignmentStart(),500);
        Assert.assertFalse(other.getReadNegativeStrandFlag());
        Assert.assertEquals(other.getMappingQuality(), 60);
        Assert.assertEquals(other.getAttribute(SAMTagUtil.NM),1);
        Assert.assertEquals(other.getCigarString(),"3S2=1X2=2S");
        Assert.assertEquals(other.getInferredInsertSize(),0);


        other = suppl.get(1);
        Assert.assertTrue(other.getSupplementaryAlignmentFlag());
        Assert.assertEquals(other.getReferenceName(),"1");
        Assert.assertEquals(other.getAlignmentStart(),191);
        Assert.assertTrue(other.getReadNegativeStrandFlag());
        Assert.assertEquals(other.getMappingQuality(), 60);
        Assert.assertEquals(other.getAttribute(SAMTagUtil.NM),null);
        Assert.assertEquals(other.getCigarString(),"8M2S");
        Assert.assertEquals(other.getInferredInsertSize(),-91);//100(mate) - 191(other)
    }

    @Test()
    public void testBytesToCompressedBases() {
        final byte[] bases = new byte[]{'=', 'a', 'A', 'c', 'C', 'g', 'G', 't', 'T', 'n', 'N', '.', 'M', 'm',
                'R', 'r', 'S', 's', 'V', 'v', 'W', 'w', 'Y', 'y', 'H', 'h', 'K', 'k', 'D', 'd', 'B', 'b'};
        final byte[] compressedBases = SAMUtils.bytesToCompressedBases(bases);
        String expectedCompressedBases = "[1, 18, 36, 72, -113, -1, 51, 85, 102, 119, -103, -86, -69, -52, -35, -18]";
        Assert.assertEquals(Arrays.toString(compressedBases), expectedCompressedBases);
    }

    @DataProvider
    public Object[][] testBadBase() {
        return new Object[][]{
                {new byte[]{'>', 'A'}, '>'},
                {new byte[]{'A', '>'} , '>'}
        };
    }

    @Test(dataProvider = "testBadBase", expectedExceptions = IllegalArgumentException.class)
    public void testBytesToCompressedBasesException(final byte[] bases, final char failingBase) {
        try {
            SAMUtils.bytesToCompressedBases(bases);
        } catch ( final IllegalArgumentException ex ) {
            Assert.assertTrue(ex.getMessage().contains(Character.toString(failingBase)));
            throw ex;
        }
    }

    @Test
    public void testCompressedBasesToBytes() {
        final byte[] compressedBases = new byte[]{1, 18, 36, 72, -113, -1, 51, 85, 102, 119, -103, -86, -69, -52, -35, -18};
        final byte[] bytes = SAMUtils.compressedBasesToBytes(2*compressedBases.length, compressedBases, 0);
        final byte[] expectedBases = new byte[]{'=', 'A', 'A', 'C', 'C', 'G', 'G', 'T', 'T', 'N', 'N', 'N', 'M', 'M',
                'R', 'R', 'S', 'S', 'V', 'V', 'W', 'W', 'Y', 'Y', 'H', 'H', 'K', 'K', 'D', 'D', 'B', 'B'};
        Assert.assertEquals(new String(bytes), new String(expectedBases));
    }
}
