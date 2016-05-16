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

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.Arrays;

public class SAMUtilsTest {
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
}
