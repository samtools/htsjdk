package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;

import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by vadim on 28/04/2015.
 */
public class CRAMComplianceTest {

    @FunctionalInterface
    public interface TriConsumer<T1, T2, T3> {
        void accept(T1 arg1, T2 arg2, T3 arg3);
    }

    /**
     * CRAM test data for verification of unmapped read mapping quality score
     * @return
     */
    @DataProvider(name = "unmappedVerification")
    public Object[][] getUnmappedVerificationData() {
        return new Object[][]{
                // unmapped reads with non-zero MAPQ value that is not restored
                // https://github.com/samtools/htsjdk/issues/714
                {"ce#unmap"}
        };
    }

    /**
     * Tests that mapping quality score for unmapped reads is reset in CRAM
     */
    @Test(dataProvider = "unmappedVerification")
    public void partialVerificationUnmappedTest(String name) throws IOException {
        doComplianceTest(name, (version, expected, actual) -> {
            expected.setMappingQuality(expected.getReadUnmappedFlag() ? SAMRecord.NO_MAPPING_QUALITY : expected.getMappingQuality());
            Assert.assertEquals(expected, actual, String.format("\n%s%s", expected.getSAMString(), actual.getSAMString()));
        });
    }

    /**
     * Odd cigar test cases
     * @return
     */
    @DataProvider(name = "nonStandardCigarVerification")
    public Object[][] getNonStandardCigarVerificationData() {
        return new Object[][]{
                // cigar string "5H0M5H" is restored as "10H"
                // https://github.com/samtools/htsjdk/issues/713
                {"xx#minimal"}
        };
    }

    /**
     * Test that tidied CIGAR survives CRAM round trip
     */
    @Test(dataProvider = "nonStandardCigarVerification")
    public void partialVerificationNonStandardCigarTest(String name) throws IOException {
        doComplianceTest(name, (version, expected, actual) -> {
            expected = expected.deepCopy();
            actual = actual.deepCopy();
            expected.setCigar(expected.getCigar().tidy());
            actual.setCigar(actual.getCigar().tidy());
            Assert.assertEquals(expected, actual, String.format("Mismatching records: \n%s%s", expected.getSAMString(), actual.getSAMString()));
        });
    }

    // Files that can be subjected to full SAMRecord equality after conversion
    @DataProvider(name = "fullVerification")
    public Object[][] getFullVerificationData() {
        return new Object[][]{
                {"c1#bounds"},
                {"c1#clip"},
                {"c1#pad1"},
                {"c1#pad2"},
                {"c1#pad3"},
                {"ce#1"},
                {"ce#2"},
                {"ce#5"},
                {"ce#large_seq"},
                {"ce#supp"},
                {"ce#unmap1"},
                {"ce#unmap2"},
                {"xx#blank"},
                {"xx#large_aux2"},
                {"xx#large_aux"},
                {"xx#pair"},
                {"xx#rg"},
                {"xx#unsorted"},
                // these cases used to be partially verifiable:
                {"xx#minimal"},
                {"auxf#values"},    // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"c1#noseq"},       // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"c1#unknown"},     // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"ce#5b"},          // reads with no read bases: https://github.com/samtools/htsjdk/issues/509
                {"ce#tag_depadded"},// reads with no read bases: https://github.com/samtools/htsjdk/issues/509
                {"ce#tag_padded"}  // reads with no read bases: https://github.com/samtools/htsjdk/issues/509

                // htsjdk.samtools.SamPairUtil.computeInsertSize() returns +6/-6 instead of +15/-15:
//                {"xx#triplet"}    // the version 2.1 variant of this file has a bad insertSize, which is
//                // probably residual detritus from https://github.com/samtools/htsjdk/issues/364
        };
    }

    @Test(dataProvider = "fullVerification")
    public void fullVerificationTest(String name) throws IOException {
        doComplianceTest(name, (version, expected, actual) -> {
//            assertEqual_NM_MD_lost(expected, actual);
            Assert.assertEquals(expected, actual, String.format("\n%s%s", expected.getSAMString(), actual.getSAMString()));
        });
    }

    @BeforeTest
    public void beforeTest() {
        Log.setGlobalLogLevel(Log.LogLevel.ERROR);
    }

    private static class TestCase {
        File bamFile;
        File refFile;
        File cramFile_21;
        File cramFile_30;

        public TestCase(File root, String name) {
            bamFile = new File(root, name + ".sam");
            refFile = new File(root, name.split("#")[0] + ".fa");
            cramFile_21 = new File(root, name + ".2.1.cram");
            cramFile_30 = new File(root, name + ".3.0.cram");
        }
    }

    private void doComplianceTest(
            final String name,
            final TriConsumer<Integer, SAMRecord, SAMRecord> assertFunction) throws IOException {
        TestCase t = new TestCase(new File("src/test/resources/htsjdk/samtools/cram/"), name);

        // retrieve all records from the original file
        List<SAMRecord> samRecords = getSAMRecordsFromFile(t.bamFile, t.refFile);
        SAMFileHeader samFileHeader = getFileHeader(t.bamFile, t.refFile);

        // write them to cram stream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ReferenceSource source = new ReferenceSource(t.refFile);
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, null, true, source, samFileHeader, name, CramVersions.CRAM_v3);
         for (SAMRecord samRecord : samRecords) {
            cramFileWriter.addAlignment(samRecord);
         }
        cramFileWriter.close();

        // read them back from the stream and compare to original sam via assertSameRecords
        CRAMFileReader cramFileReader = new CRAMFileReader(new ByteArrayInputStream(baos.toByteArray()), (SeekableStream) null, source, ValidationStringency.SILENT);
        SAMRecordIterator cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertEqual_samtools_v3(samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());

        //v2.1 test
        cramFileReader = new CRAMFileReader(new FileInputStream(t.cramFile_21), (SeekableStream) null, source, ValidationStringency.SILENT);
        cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertEqual_samtools_v2_1(samRecord, restored);
        }

        Assert.assertFalse(cramFileReaderIterator.hasNext());

        //v3.0 test
        cramFileReader = new CRAMFileReader(new FileInputStream(t.cramFile_30), (SeekableStream) null, source, ValidationStringency.SILENT);
        cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertEqual_samtools_v3(samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());
    }

    private void assertSameRecordsPartial(Integer majorVersion, SAMRecord record1, SAMRecord record2) {
        // test a partial set of fields for equality, avoiding known CRAM conversion issues
        Assert.assertEquals(record2.getFlags(), record1.getFlags());
        Assert.assertEquals(record2.getReadName(), record1.getReadName());
        Assert.assertEquals(record2.getReferenceName(), record1.getReferenceName());
        Assert.assertEquals(record2.getAlignmentStart(), record1.getAlignmentStart());

        /**
         * Known issue: CRAM v2.1 doesn't handle reads with missing bases correctly. This
         * causes '*' bases to arise when reading CRAM. Skipping the base comparison asserts.
         * https://github.com/samtools/htsjdk/issues/509
         */
        if (record1.getReadBases() != SAMRecord.NULL_SEQUENCE || majorVersion >= CramVersions.CRAM_v3.major) {
            Assert.assertEquals(record2.getReadBases(), record1.getReadBases());
        }

        Assert.assertEquals(record2.getBaseQualities(), record1.getBaseQualities());
    }

    @DataProvider(name = "CRAMSourceFiles")
    public Object[][] getCRAMSources() {
        final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

        return new Object[][]{
                {new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta")},
                {new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.1-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta")},
                {new File(TEST_DATA_DIR, "NA12878.20.21.1-100.100-SeqsPerSlice.500-unMapped.cram"),
                        new File(TEST_DATA_DIR, "human_g1k_v37.20.21.1-100.fasta")},
                {new File(TEST_DATA_DIR, "test.cram"), new File(TEST_DATA_DIR, "auxf.fa")},
                {new File(TEST_DATA_DIR, "test2.cram"), new File(TEST_DATA_DIR, "auxf.fa")},
        };
    }

        @Test(dataProvider = "CRAMSourceFiles")
    public void testCRAMThroughBAMRoundTrip(final File originalCRAMFile, final File referenceFile) throws IOException {

        // retrieve all records from the cram and make defensive deep copies
        List<SAMRecord> originalCRAMRecords = getSAMRecordsFromFile(originalCRAMFile, referenceFile);
        List<SAMRecord> copiedCRAMRecords = new ArrayList<>();
        originalCRAMRecords.forEach(origRec -> copiedCRAMRecords.add(origRec.deepCopy()));

        // write copies of the CRAM records to a BAM, and then read them back in
        final File tempBamFile = File.createTempFile("testCRAMToBAMToCRAM", BamFileIoUtils.BAM_FILE_EXTENSION);
        tempBamFile.deleteOnExit();
        SAMFileHeader samHeader = getFileHeader(originalCRAMFile, referenceFile);
        writeRecordsToFile(copiedCRAMRecords, tempBamFile, referenceFile, samHeader);
        List<SAMRecord> bamRecords = getSAMRecordsFromFile(tempBamFile, referenceFile);

        // compare to originals
        int i = 0;
        for (SAMRecord rec : bamRecords) {
            rec.setIndexingBin(null);
            Assert.assertTrue(rec.equals(originalCRAMRecords.get(i++)));
        }
        Assert.assertEquals(i, originalCRAMRecords.size());

        // write the BAM records to a CRAM and read them back in
        final File tempCRAMFile = File.createTempFile("testCRAMToBAMToCRAM", CramIO.CRAM_FILE_EXTENSION);
        tempCRAMFile.deleteOnExit();
        writeRecordsToFile(bamRecords, tempCRAMFile, referenceFile, samHeader);
        List<SAMRecord> roundTripCRAMRecords = getSAMRecordsFromFile(tempCRAMFile, referenceFile);

        // compare to originals
        i = 0;
        for (SAMRecord rec : roundTripCRAMRecords) {
            Assert.assertTrue(rec.equals(originalCRAMRecords.get(i++)));
        }
        Assert.assertEquals(i, originalCRAMRecords.size());
    }

        @Test
    public void testBAMThroughCRAMRoundTrip() throws IOException, NoSuchAlgorithmException {
        final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/cram");

        // These files are reduced versions of the CEUTrio.HiSeq.WGS.b37.NA12878.20.21.bam and human_g1k_v37.20.21.fasta
        // files used in GATK4 tests. The first 8000 records from chr20 were extracted; from those around 80 placed but
        // unmapped reads that contained cigar elements were removed, along with one read who's mate was on chr21.
        // Finally all read positions were remapped to the subsetted reference file, which contains only the ~9000 bases
        // used by the reduced read set.
        final File originalBAMInputFile = new File(TEST_DATA_DIR, "CEUTrio.HiSeq.WGS.b37.NA12878.20.first.8000.bam");
        final File referenceFile = new File(TEST_DATA_DIR, "human_g1k_v37.20.subset.fasta");

        // retrieve all records from the bam and reset the indexing bins to keep comparisons with
        // cram records from failing
        List<SAMRecord> originalBAMRecords = getSAMRecordsFromFile(originalBAMInputFile, referenceFile);
        for (int i = 0; i < originalBAMRecords.size(); i++) {
            originalBAMRecords.get(i).setIndexingBin(null);
        }

        // write the BAM records to a temporary CRAM
        final File tempCRAMFile = File.createTempFile("testBAMThroughCRAMRoundTrip", CramIO.CRAM_FILE_EXTENSION);
        tempCRAMFile.deleteOnExit();
        SAMFileHeader samHeader = getFileHeader(originalBAMInputFile, referenceFile);
        writeRecordsToFile(originalBAMRecords, tempCRAMFile, referenceFile, samHeader);

        // read the CRAM records back in and compare to the original BAM records
        List<SAMRecord> cramRecords = getSAMRecordsFromFile(tempCRAMFile, referenceFile);
        Assert.assertEquals(cramRecords.size(), originalBAMRecords.size());
        for (int i = 0; i < originalBAMRecords.size(); i++) {
            Assert.assertEquals(originalBAMRecords.get(i), cramRecords.get(i));
        }
    }

    private SAMFileHeader getFileHeader(final File sourceFile, final File referenceFile) throws IOException {
        try (final SamReader reader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(sourceFile)) {
            return reader.getFileHeader();
        }
    }

    private List<SAMRecord> getSAMRecordsFromFile(final File sourceFile, final File referenceFile) throws IOException {
        List<SAMRecord> recs = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(sourceFile)) {
            for (SAMRecord rec : reader) {
                recs.add(rec);
            }
        }
        return recs;
    }

    private void writeRecordsToFile(
            final List<SAMRecord> recs,
            final File targetFile,
            final File referenceFile,
            final SAMFileHeader samHeader) {

        // NOTE: even when the input is coord-sorted, using assumePresorted=false will cause some
        // tests to fail since it can change the order of some unmapped reads - AFAICT this is allowed
        // by the spec since the order is arbitrary for unmapped
        try (final SAMFileWriter writer = new SAMFileWriterFactory()
                .makeWriter(samHeader, true, targetFile, referenceFile)) {
            for (SAMRecord rec : recs) {
                writer.addAlignment(rec);
            }
        }
    }

    private static void assertEqual_samtools_v2_1 (SAMRecord expected, SAMRecord actual)  {
        String message = String.format("\n%s%s", expected.getSAMString(), actual.getSAMString());
        if (expected.getReadBases() != SAMRecord.NULL_SEQUENCE) {
            Assert.assertNull(actual.getAttribute("NM"), message);
            Assert.assertNull(actual.getAttribute("MD"), message);
        }

        try {
            actual = (SAMRecord) actual.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        actual.setAttribute("NM", expected.getAttribute("NM"));
        actual.setAttribute("MD", expected.getAttribute("MD"));

        if (expected.getReadBases() == SAMRecord.NULL_SEQUENCE) {
            actual.setReadBases(SAMRecord.NULL_SEQUENCE);
        }

        if (actual.getReadUnmappedFlag()) {
            Assert.assertEquals(actual.getMappingQuality(), SAMRecord.NO_MAPPING_QUALITY);
            actual.setMappingQuality(expected.getMappingQuality());
        }

        if (!expected.getCigar().equals(actual.getCigar())) {
            if (!expected.getCigar().tidy().equals(actual.getCigar().tidy())) {
                Assert.fail(message);
            } else {
                actual.setCigar(expected.getCigar());
            }
        }
        Assert.assertEquals(expected, actual, message);
    }

    private static void assertEqual_samtools_v3 (SAMRecord expected, SAMRecord actual)  {
        String message = String.format("\n%s%s", expected.getSAMString(), actual.getSAMString());

        try {
            actual = (SAMRecord) actual.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        actual.setAttribute("NM", expected.getAttribute("NM"));
        actual.setAttribute("MD", expected.getAttribute("MD"));
        if (actual.getReadUnmappedFlag()) {
            Assert.assertEquals(actual.getMappingQuality(), SAMRecord.NO_MAPPING_QUALITY);
            actual.setMappingQuality(expected.getMappingQuality());
        }

        if (!expected.getCigar().equals(actual.getCigar())) {
            if (!expected.getCigar().tidy().equals(actual.getCigar().tidy())) {
                Assert.fail(message);
            } else {
                actual.setCigar(expected.getCigar());
            }
        }

        Assert.assertEquals(expected, actual, message);
    }
}
