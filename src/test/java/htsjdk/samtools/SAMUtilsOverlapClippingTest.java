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
import java.util.ArrayList;
import java.util.List;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * Tests for {@link SAMUtils#getNumOverlappingAlignedBasesToClip(SAMRecord)} and
 * {@link SAMUtils#clipOverlappingAlignedBases(SAMRecord, int, boolean)}.
 */
public class SAMUtilsOverlapClippingTest extends HtsjdkTest {
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

    /**
     * Builds a read aligned at 1001 on chr1 with the given cigar and a mate starting at {@code mateStart}.
     * The read is second of pair so that it is the one clipped when both mates start at the same position.
     */
    private SAMRecord record(final String cigar, final int mateStart) {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("chr1", 10000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("overlap");
        record.setReadPairedFlag(true);
        record.setSecondOfPairFlag(true);
        record.setReferenceIndex(0);
        record.setMateReferenceIndex(0);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(mateStart);
        record.setCigarString(cigar);
        record.setReadString("A".repeat(record.getCigar().getReadLength()));
        return record;
    }

    @DataProvider(name = "overlapCounts")
    public Object[][] overlapCounts() {
        final List<Object[]> cases = new ArrayList<>();
        // A single 150-base element with the mate starting at each boundary and an interior position.
        for (final String operator : new String[] {"M", "=", "X"}) {
            cases.add(new Object[] {"150" + operator, 1001, 150}); // same start: clip the whole read
            cases.add(new Object[] {"150" + operator, 1002, 149}); // one base in
            cases.add(new Object[] {"150" + operator, 1051, 100}); // in the middle
            cases.add(new Object[] {"150" + operator, 1150, 1}); // last aligned base
            cases.add(new Object[] {"150" + operator, 1151, 0}); // just past the read: no overlap
        }
        // The mate start falls inside an element other than the first one.
        cases.add(new Object[] {"40=20X90=", 1051, 100}); // 10 of the X plus all 90 of the trailing =
        // Indels inside the overlap: insertions are clipped in full, deletions contribute nothing.
        cases.add(new Object[] {"60=5I90=", 1051, 105}); // 10 of the = plus 5I plus 90
        cases.add(new Object[] {"60=5D90=", 1051, 100}); // 10 of the = plus 90
        // Indels before the overlap: the deletion shifts the last element right, the insertion does not.
        cases.add(new Object[] {"40=5I110=", 1051, 100}); // 110= covers 1041-1150
        cases.add(new Object[] {"40=5D110=", 1051, 105}); // 110= covers 1046-1155
        // A skipped region containing the mate start contributes nothing; the element after it is clipped in full.
        cases.add(new Object[] {"40=10N110=", 1046, 110});
        // Soft and hard clips on either end are ignored when counting.
        cases.add(new Object[] {"5H10S150=10S5H", 1051, 100});
        return cases.toArray(new Object[0][]);
    }

    @Test(dataProvider = "overlapCounts")
    public void testOverlapCount(final String cigar, final int mateStart, final int expected) {
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record(cigar, mateStart)), expected);
    }

    @DataProvider(name = "clippedCigars")
    public Object[][] clippedCigars() {
        // The mate always starts at 1051, so the last 100 aligned bases are clipped in every case.
        // 5H10S150=10S5H is deliberately absent: a hard clip after a trailing soft clip is not handled by
        // clipOverlappingAlignedBases (see the FIXME there).
        return new Object[][] {
            {"150M", "50M100S"},
            {"150=", "50=100S"},
            {"150X", "50X100S"},
            {"40=20X90=", "40=10X100S"},
            {"60=5I90=", "50=105S"}, // insertion inside the overlap is clipped with it
            {"60=5D90=", "50=100S"}, // deletion inside the overlap disappears
            {"40=5I110=", "40=5I10=100S"}, // insertion before the overlap survives
            {"40=5D110=", "40=5D5=105S"}, // deletion before the overlap survives
            {"10S150=10S", "10S50=110S"}, // existing trailing soft clip merges into the new one
            {"5H150=", "5H50=100S"}
        };
    }

    @Test(dataProvider = "clippedCigars")
    public void testClippedCigar(final String cigar, final String expected) {
        final SAMRecord original = record(cigar, 1051);
        final SAMRecord clipped = SAMUtils.clipOverlappingAlignedBases(original, true);
        Assert.assertEquals(clipped.getCigarString(), expected);
        Assert.assertEquals(original.getCigarString(), cigar); // noSideEffects=true must leave the input alone
        Assert.assertEquals(clipped.getAlignmentStart(), original.getAlignmentStart());
        Assert.assertEquals(clipped.getReadLength(), original.getReadLength());
        Assert.assertFalse(clipped.getReadUnmappedFlag());
    }

    @DataProvider(name = "references")
    public Object[][] references() {
        // Columns: reference index, mate reference index, mate start, first of pair, expected bases to clip.
        // The read is 150M at 1001 throughout.
        return new Object[][] {
            // Same reference: existing behaviour, as a control.
            {0, 0, 1051, false, 100},
            {0, 0, 1001, false, 150},
            {0, 0, 1001, true, 0}, // same start: the first of pair is the one left unclipped
            // Different references: these would overlap by coordinate alone and must not be clipped.
            {0, 1, 1051, false, 0},
            {1, 0, 1051, false, 0},
            {0, 1, 1001, false, 0},
        };
    }

    /** Builds a 150M read at 1001 on the given reference with a mate on {@code mateReference} at {@code mateStart}. */
    private static SAMRecord pairedRead(
            final int reference, final int mateReference, final int mateStart, final boolean firstOfPair) {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord("chr1", 10000));
        header.addSequence(new SAMSequenceRecord("chr2", 10000));
        final SAMRecord record = new SAMRecord(header);
        record.setReadName("reference-identity");
        record.setReadPairedFlag(true);
        record.setFirstOfPairFlag(firstOfPair);
        record.setSecondOfPairFlag(!firstOfPair);
        record.setReferenceIndex(reference);
        record.setMateReferenceIndex(mateReference);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(mateStart);
        record.setCigarString("150M");
        record.setReadString("A".repeat(150));
        return record;
    }

    @Test(dataProvider = "references")
    public void testOverlapCountAcrossReferences(
            final int reference,
            final int mateReference,
            final int mateStart,
            final boolean firstOfPair,
            final int expected) {
        final SAMRecord record = pairedRead(reference, mateReference, mateStart, firstOfPair);
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), expected);
    }

    @Test
    public void testClippingLeavesCrossReferenceRecordUntouched() {
        final SAMRecord record = pairedRead(0, 1, 1051, false);
        final SAMRecord clipped = SAMUtils.clipOverlappingAlignedBases(record, true);
        Assert.assertSame(clipped, record);
        Assert.assertEquals(clipped.getCigarString(), "150M");
        Assert.assertFalse(clipped.getReadUnmappedFlag());
    }

    @DataProvider(name = "headerlessReferences")
    public Object[][] headerlessReferences() {
        return new Object[][] {{"chr1", 100}, {"chr2", 0}};
    }

    /**
     * A record with no header cannot resolve reference indices at all, so the comparison has to be by name.
     * This is the case an index-based comparison could not handle.
     */
    @Test(dataProvider = "headerlessReferences")
    public void testHeaderlessRecords(final String mateReference, final int expected) {
        final SAMRecord record = new SAMRecord(null);
        record.setReadPairedFlag(true);
        record.setSecondOfPairFlag(true);
        record.setReferenceName("chr1");
        record.setMateReferenceName(mateReference);
        record.setAlignmentStart(1001);
        record.setMateAlignmentStart(1051);
        record.setCigarString("150M");
        Assert.assertEquals(SAMUtils.getNumOverlappingAlignedBasesToClip(record), expected);
    }
}
