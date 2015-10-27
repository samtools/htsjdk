package htsjdk.samtools;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Map;

public class DuplicateSetIteratorTest {
    protected final static int DEFAULT_BASE_QUALITY = 10;

    private SAMRecordSetBuilder getSAMRecordSetBuilder() {
        return new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.unsorted);
    }

    @Test
    public void testSupplementalReads() {
        final SAMRecordSetBuilder records = getSAMRecordSetBuilder();

        records.addFrag("READ0", 1, 1, false);
        records.addFrag("READ1", 1, 1, false);

        //secondary alignment
        records.addFrag("SECN0", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, true);
        records.addFrag("SECN1", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, true);

        //unmapped
        records.addFrag("UNMP0", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY);
        records.addFrag("UNMP1", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY);

        //supplemental
        records.addFrag("SUPP0", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, false, true);
        records.addFrag("SUPP1", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, false, true);

        //supplemental secondary
        records.addFrag("SUSE0", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, true, true);
        records.addFrag("SUSE1", 1, 1, false, false, "50M", null, DEFAULT_BASE_QUALITY, true, true);

        //unmapped secondary
        records.addFrag("UNSE0", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, true);
        records.addFrag("UNSE1", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, true);

        //unmapped supplemental
        records.addFrag("UNSU0", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, false, true);
        records.addFrag("UNSU1", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, false, true);

        //unmapped supplemental secondary
        records.addFrag("UNSS0", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, true, true);
        records.addFrag("UNSS1", 1, 1, false, true, "50M", null, DEFAULT_BASE_QUALITY, true, true);

        Map<String, DuplicateSet> allSets = new HashMap<String, DuplicateSet>();

        DuplicateSetIterator duplicateSetIterator = new DuplicateSetIterator(records.iterator(), getSAMRecordSetBuilder().getHeader(), false);
        while (duplicateSetIterator.hasNext()) {
            DuplicateSet set = duplicateSetIterator.next();
            allSets.put(set.getRepresentative().getReadName(), set);
        }

        //we expect 15 duplicate sets one for the initial two reads and one for each of the additional 14 reads.
        Assert.assertEquals(allSets.size(), 15, "Wrong number of duplicate sets.");
        Assert.assertEquals(allSets.get("READ0").size(), 2, "Should be two reads in the READ0 duplicate set, but there are not.");
    }
}
