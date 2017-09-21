package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SAMRecordTest {

    @Test
    public void testRecordsArePairIfTheyLinkEachOtherInMateFields() {
        final SAMRecord first = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));
        first.setAlignmentStart(42);
        first.setReferenceName("chrm1");
        final SAMRecord second = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));
        second.setAlignmentStart(142);
        second.setReferenceName("chrm2");

        first.setMateAlignmentStart(second.getAlignmentStart());
        first.setMateReferenceName(second.getReferenceName());
        second.setMateAlignmentStart(first.getAlignmentStart());
        second.setMateReferenceName(first.getReferenceName());

        Assert.assertTrue(first.isPair(second));
        Assert.assertTrue(second.isPair(first));
    }

    @Test
    public void testRecordsArePairIfTheyHaveNoMateFields() {
        final SAMRecord first = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));
        first.setAlignmentStart(42);
        first.setReferenceName("chrm1");
        final SAMRecord second = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));
        second.setAlignmentStart(142);
        second.setReferenceName("chrm2");

        Assert.assertFalse(first.isPair(second));
        Assert.assertFalse(second.isPair(first));
    }

    @Test
    public void testRecordsArePairIdentificationDoesNotThrowNpe() {
        final SAMRecord first = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));

        Assert.assertFalse(first.isPair(null));
    }

    @Test
    public void testRecordsArePairIdentificationDoesNotThrowNpeIfFieldsAreUndefined() {
        final SAMRecord first = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));
        final SAMRecord second = new SAMRecord(new SAMFileHeader(new SAMSequenceDictionary()));

        Assert.assertFalse(first.isPair(second));
        Assert.assertFalse(second.isPair(first));
    }
}
