package htsjdk.samtools.filter;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordSetBuilder;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Iterator;
import java.util.List;

public class AlignedFilterTest {
    private static final int READ_LENGTH = 20;

    @Test(dataProvider = "singleRecord")
    public void testFilterSingleRead(final String name, final boolean isPaired, final boolean isUnmapped, final boolean mateUnmapped) {
        final SAMRecordSetBuilder singleRecordBuilder = new SAMRecordSetBuilder();
        singleRecordBuilder.setReadLength(READ_LENGTH);
        final AlignedFilter isAlignedFilter = new AlignedFilter(true, false);
        final AlignedFilter isUnalignedFilter = new AlignedFilter(false, false);

        SAMRecord rec = singleRecordBuilder.addFrag(name, 1, 0, false, isUnmapped, "", "", 30);
        if (isPaired) {
            rec.setMateUnmappedFlag(mateUnmapped);
        }
        Assert.assertEquals(isAlignedFilter.filterOut(rec), isUnmapped);
        Assert.assertNotEquals(isUnalignedFilter.filterOut(rec), isUnmapped);
    }

    @Test(dataProvider = "singleRecord")
    public void testFilterSingleReadMatesMatch(final String name, final boolean isPaired, final boolean isUnmapped, final boolean mateUnmapped) {
        final SAMRecordSetBuilder singleRecordBuilder = new SAMRecordSetBuilder();
        singleRecordBuilder.setReadLength(READ_LENGTH);
        final AlignedFilter isAlignedFilter = new AlignedFilter(true, false);
        final AlignedFilter isUnalignedFilter = new AlignedFilter(false, false);

        SAMRecord rec = singleRecordBuilder.addFrag(name, 1, 0, false, isUnmapped, "", "", 30);
        if (isPaired) {
            rec.setMateUnmappedFlag(mateUnmapped);
        }

        boolean recordUnmapped = isPaired ? (isUnmapped || mateUnmapped) : isUnmapped;
        Assert.assertEquals(isAlignedFilter.filterOut(rec), recordUnmapped);
        Assert.assertNotEquals(isUnalignedFilter.filterOut(rec), recordUnmapped);
    }

    @DataProvider(name = "singleRecord")
    private Object[][] singleReadData() {
        return new Object[][]{
                {"alignedFragment", false, false, false},
                {"unalignedFragment", false, true, false},
                {"alignedMateMapped", true, false, false},
                {"alignedMateUnmapped", true, false, true},
                {"unalignedMateMapped", true, true, false},
                {"unalignedMateUnmapped", true, true, true}
        };
    }

    @Test(dataProvider = "pairedRecords")
    public void testPairedRecords(final String recordName,
                                  final boolean record1isUnmapped,
                                  final boolean record2isUnmapped) {
        final SAMRecordSetBuilder doubleRecordBuilder = new SAMRecordSetBuilder();
        doubleRecordBuilder.setReadLength(READ_LENGTH);
        final AlignedFilter isAlignedFilter = new AlignedFilter(true, false);
        final AlignedFilter isUnalignedFilter = new AlignedFilter(false, false);

        Iterator<SAMRecord> records = doubleRecordBuilder.addPair(recordName, 1, 0, 0,record1isUnmapped, record2isUnmapped, "", "", false, false, 30).iterator();
        SAMRecord rec1 = records.next();
        SAMRecord rec2 = records.next();

        Assert.assertEquals(isAlignedFilter.filterOut(rec1, rec2), (record1isUnmapped || record2isUnmapped) );
        Assert.assertNotEquals(isUnalignedFilter.filterOut(rec1, rec2), (record1isUnmapped || record2isUnmapped));
    }

    @DataProvider(name = "pairedRecords")
    private Object[][] pairedRecordsData() {
        return new Object[][]{
                {"alignedMateAligned", false, false},
                {"alignedMateUnAligned", false, true},
                {"unAlignedMateAligned", true, false},
                {"unAlignedMateUnAligned", true, true}
        };
    }

    @Test(dataProvider = "separateRecords")
    public void testTwoSeparateEnds(final String record1Name,
                                    final boolean record1isUnmapped,
                                    final String record2Name,
                                    final boolean record2isPaired,
                                    final boolean record2isUnmapped,
                                    final boolean record2mateUnmapped) {
        final SAMRecordSetBuilder doubleRecordBuilder = new SAMRecordSetBuilder();
        doubleRecordBuilder.setReadLength(READ_LENGTH);
        final AlignedFilter isAlignedFilter = new AlignedFilter(true, false);
        final AlignedFilter isUnalignedFilter = new AlignedFilter(false, false);

        SAMRecord rec1 = doubleRecordBuilder.addFrag(record1Name, 1, 0, false, record1isUnmapped, "", "", 30);
        SAMRecord rec2;
        if (record2isPaired) {
            rec2 = doubleRecordBuilder.addPair(record2Name, 1, 0, 0,record2isUnmapped, record2mateUnmapped, "", "", false, false, 30).iterator().next();
        } else {
            rec2 = doubleRecordBuilder.addFrag(record2Name, 1, 0, false, record2isUnmapped, "", "", 30);
        }
        Assert.assertEquals(isAlignedFilter.filterOut(rec1, rec2), (record1isUnmapped || record2isUnmapped) );
        Assert.assertNotEquals(isUnalignedFilter.filterOut(rec1, rec2), (record1isUnmapped || record2isUnmapped));
    }

    @DataProvider(name = "separateRecords")
    private Object[][] separateRecordsData() {
        return new Object[][]{
                {"alignedFragment1", false, "alignedFragment2", false, false, false},
                {"alignedFragment1", false, "unalignedFragment2", false, true, false},
                {"alignedFragment1", false, "alignedMateAligned",true, false, false},
                {"alignedFragment1", false, "alignedMateUnAligned",true, false, true},
                {"alignedFragment1", false, "unAlignedMateAligned",true, true, false},
                {"alignedFragment1", false, "unAlignedMateUnAligned",true, true, true}
        };
    }


}
