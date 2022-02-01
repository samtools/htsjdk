package htsjdk.samtools.cram.structure.block;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.cram.structure.*;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class CRAMRecordReadFeaturesTest extends HtsjdkTest {

    @DataProvider(name = "cigarTest")
    public final Object[][] getBasesTests() {
        return new Object[][] {
                // ref bases, read bases, cigar string, expected cigar string
                {"aaaaa", "acgta", "5M", "5M"},
                {"aaaaa", "ttttt", "5X", "5M"}, // X -> M
                {"aaaaa", "aaaaa", "5=", "5M"}, // = -> M
        };
    }

    @Test(dataProvider = "cigarTest")
    public void testCigarFidelity(
            final String readBases,
            final String refBases,
            final String cigarString,
            final String expectedCigarString) {
        final SAMRecord samRecord = CRAMStructureTestHelper.createSAMRecordMapped(0, 1);
        samRecord.setReadBases(readBases.getBytes());
        samRecord.setCigarString(cigarString);

        final CRAMRecordReadFeatures rf = new CRAMRecordReadFeatures(samRecord, readBases.getBytes(), refBases.getBytes());
        final Cigar cigar = rf.getCigarForReadFeatures(readBases.length());
        Assert.assertEquals(cigar.toString(), expectedCigarString);
    }

    @DataProvider(name="readFeatureTestData")
    private Object[][] getReadFeatureTestData() {
        final String testDir = "src/test/resources/htsjdk/samtools/cram/";
        return new Object[][]{
                // cram, sam, reference (may be null)

                // test CRAM file taken from the CRAM test files in hts-specs, has reads with ReadBase ('B') and
                // Bases ('b') feature codes
                {testDir + "0503_mapped.cram", testDir + "0503_mapped.sam", testDir + "ce.fa"},

                // test CRAM file (provided as part of https://github.com/samtools/htsjdk/issues/1379) does not
                // use reference-based compression (requires no reference) and uses Bases ('b') and SoftClip ('S')
                // feature codes; with the sam file created from the cram via samtools
                {testDir + "referenceNotRequired.cram", testDir + "referenceNotRequired.sam", null},

                // test CRAM file taken from the CRAM test files in hts-specs, has Scores ('q') read feature
                // Note: the specs site compliance documentation for this test file says:
                // Quality absent, mapped with diff (1005_qual.cram) As 1004_qual.cram but using 'q' instead of a series
                // of 'Q' features. [ complex to generate! see CRAM.q.gen.patch ]
                {testDir + "1005_qual.cram", testDir + "1005_qual.sam", testDir + "ce.fa" }
        };
    }

    @Test(dataProvider = "readFeatureTestData")
    private void readFeatureTest(
            final String cramFileName,
            final String samFileName,
            final String referenceFileName
    ) throws IOException {
        // ensure these are handled correctly on read by comparing the SAMRecords created when reading the
        // CRAM with the SAMRecords from the corresponding truth SAM (see https://github.com/samtools/htsjdk/issues/1379)
        final File testCRAM = new File(cramFileName);
        final File testSAM = new File(samFileName);
        final File referenceFile = referenceFileName == null ? null : new File(referenceFileName);

        try (final SamReader cramReader = SamReaderFactory.make().referenceSequence(referenceFile).open(testCRAM);
             final SamReader samReader = SamReaderFactory.make().referenceSequence(referenceFile).open(testSAM)) {

            final SAMRecordIterator cramIterator = cramReader.iterator();
            final SAMRecordIterator samIterator = samReader.iterator();
            while (samIterator.hasNext() && cramIterator.hasNext()) {
                final SAMRecord samRecord = samIterator.next();
                final SAMRecord cramRecord = cramIterator.next();

                Assert.assertEquals(samRecord.getReadBases(), cramRecord.getReadBases());
                Assert.assertEquals(samRecord.getBaseQualities(), cramRecord.getBaseQualities());
                Assert.assertEquals(samRecord.getCigarString(), cramRecord.getCigarString());
            }
            Assert.assertEquals(samIterator.hasNext(), cramIterator.hasNext());
        }
    }

}
