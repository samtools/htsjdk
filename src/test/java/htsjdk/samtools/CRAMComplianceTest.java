package htsjdk.samtools;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.Log;

import htsjdk.samtools.util.Tuple;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * A set of tests around compatibility test files. The compatibility test files are composed of 4 file per case:
 * SAM file, reference FASTA file, CRAM v2.1 and v3.0 generated from the SAM file using samtools v1.3.1
 * The naming convention is to mention reference name in the SAM file name and to append CRAM version to file name,
 * for example:
 * <ul>
 * <li>ce.fa</li>
 * <li>ce#unmap.sam</li>
 * <li>ce#unmap.2.1.cram</li>
 * <li>ce#unmap.3.0.cram</li>
 * </ul>
 */
public class CRAMComplianceTest {

    @FunctionalInterface
    public interface TriConsumer<T1, T2, T3> {
        void accept(T1 arg1, T2 arg2, T3 arg3);
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

                // the following cases used to be partially verifiable:

                {"auxf#values"},    // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"c1#noseq"},       // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"c1#unknown"},     // unsigned attributes: https://github.com/samtools/htsjdk/issues/499
                {"ce#5b"},          // reads with no read bases: https://github.com/samtools/htsjdk/issues/509
                {"ce#tag_depadded"},// reads with no read bases: https://github.com/samtools/htsjdk/issues/509
                {"ce#tag_padded"},  // reads with no read bases: https://github.com/samtools/htsjdk/issues/509
                {"xx#triplet"},     // 5'-5' vs leftmost-rightmost insert size
                {"xx#minimal"},     // cigar string "5H0M5H" is restored as "10H": https://github.com/samtools/htsjdk/issues/713
                {"ce#unmap"},       // unmapped reads with non-zero MAPQ value that is not restored: https://github.com/samtools/htsjdk/issues/714
        };
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

    @Test(dataProvider = "fullVerification")
    public void doComplianceTest(
            final String name) throws IOException {
        TestCase t = new TestCase(new File("src/test/resources/htsjdk/samtools/cram/"), name);

        // retrieve all records from the original file
        List<SAMRecord> samRecords = getSAMRecordsFromFile(t.bamFile, t.refFile);
        SAMFileHeader samFileHeader = getFileHeader(t.bamFile, t.refFile);
        ReferenceSource source = new ReferenceSource(t.refFile);

        // CRAM v2.1 in-memory HTSJDK-HTDJDK roundtrip tests:
        assert_HTSJDK_HTSJDK_roundtripRecords(samRecords, source, samFileHeader, name, CramVersions.CRAM_v2_1);

        // CRAM v3.0 in-memory HTSJDK-HTDJDK roundtrip tests:
        assert_HTSJDK_HTSJDK_roundtripRecords(samRecords, source, samFileHeader, name, CramVersions.CRAM_v3);


        // CRAM v2.1 file samtools-HTDJDK roundtrip tests:
        assert_roundtripRecords(samRecords, new FileInputStream(t.cramFile_21), source, CramVersions.CRAM_v2_1,
                CRAMComplianceTest::assertEqual_SAM_samtools_CRAM_HTSJDK_rountdtrip);

        // CRAM v3.0 file samtools-HTDJDK roundtrip tests:
        assert_roundtripRecords(samRecords, new FileInputStream(t.cramFile_30), source, CramVersions.CRAM_v3,
                CRAMComplianceTest::assertEqual_SAM_samtools_CRAM_HTSJDK_rountdtrip);
    }

    private void assert_HTSJDK_HTSJDK_roundtripRecords(List<SAMRecord> samRecords, ReferenceSource source, SAMFileHeader samFileHeader, String name, Version cramVersion) throws IOException {
        // write original records in CRAM format into memory:
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CRAMFileWriter cramFileWriter = new CRAMFileWriter(baos, null, true, source, samFileHeader, name, cramVersion);
        samRecords.forEach(cramFileWriter::addAlignment);
        cramFileWriter.close();
        // make sure the written CRAM is actually CRAM of the requested version:
        Assert.assertEquals(CramIO.readCramHeader(new ByteArrayInputStream(baos.toByteArray())).getVersion(), cramVersion);

        // read CRAM records and compare them against the original SAM records:
        assert_roundtripRecords(samRecords, new ByteArrayInputStream(baos.toByteArray()), source, cramVersion,
                CRAMComplianceTest::assertEqual_SAM_HTSJDK_CRAM_HTSJDK_roundtrip);
    }

    private void assert_roundtripRecords(List<SAMRecord> samRecords, InputStream inputStream, ReferenceSource source, Version cramVersion, TriConsumer<Integer, SAMRecord, SAMRecord> assertFunction) throws IOException {
        CRAMFileReader cramFileReader = new CRAMFileReader(inputStream, (SeekableStream) null, source, ValidationStringency.SILENT);
        SAMRecordIterator cramFileReaderIterator = cramFileReader.getIterator();
        for (SAMRecord samRecord : samRecords) {
            Assert.assertTrue(cramFileReaderIterator.hasNext());
            SAMRecord restored = cramFileReaderIterator.next();
            Assert.assertNotNull(restored);
            assertFunction.accept(cramVersion.major, samRecord, restored);
        }
        Assert.assertFalse(cramFileReaderIterator.hasNext());
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

    /**
     * Assertion method to ensure records compressed with samtools and then restored with HTSJDK match the original SAM
     * records read by HTSJDK.
     * Here is a list of known expectations:
     * <ul>
     * <li>NM and MD tags are not captured by samtools for reads with known bases</li>
     * <li>unknown read bases ({@link SAMRecord#NULL_SEQUENCE}) is not supported in CRAM v2.1</li>
     * <li>CRAM does not support mapping quality score for unmapped reads</li>
     * <li>only tidy cigars survive roundtrip, see {@link Cigar#tidy()} for details</li>
     * <li>5'-5' vs leftmost-rightmost insert size definition clash: CRAM allows writers to omit insert size if it can be computed
     * but the specific algorithm is not defined in the specs. Samtools uses leftmost-rightmost but HTSJDK uses 5'-5',
     * therefore a file compressed by samtools and read by HTSJDK exhibits sporadic insert size mismatches.</li>
     * </ul>
     *
     * @param major    CRAM major version number
     * @param expected expected original SAM record to compare against
     * @param actual   the actual record read from CRAM
     */
    private static void assertEqual_SAM_samtools_CRAM_HTSJDK_rountdtrip(int major, SAMRecord expected, SAMRecord actual) {
        String message = String.format("\nMismatching records:\n%s%s", expected.getSAMString(), actual.getSAMString());

        if (expected.getReadBases() != SAMRecord.NULL_SEQUENCE) {
            // samtools does not capture NM and MD tags if bases are known:
            Assert.assertNull(actual.getAttribute("NM"), message);
            Assert.assertNull(actual.getAttribute("MD"), message);
        }

        try {
            // we are going to modify the record, so better make a copy:
            actual = (SAMRecord) actual.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        // copy NM and MD because HTSJDK does not restore them by default:
        actual.setAttribute("NM", expected.getAttribute("NM"));
        actual.setAttribute("MD", expected.getAttribute("MD"));

        if (major < CramVersions.CRAM_v3.major && expected.getReadBases() == SAMRecord.NULL_SEQUENCE) {
            // CRAM3 has a special flag to handle unknown bases but older versions don't, so fix the actual value for v2.1:
            actual.setReadBases(SAMRecord.NULL_SEQUENCE);
        }

        if (actual.getReadUnmappedFlag()) {
            // CRAM is strict about mapping quality score on unmapped reads:
            Assert.assertEquals(actual.getMappingQuality(), SAMRecord.NO_MAPPING_QUALITY);
            actual.setMappingQuality(expected.getMappingQuality());
        }

        if (!expected.getCigar().equals(actual.getCigar())) {
            // try tidying cigars before comparision:
            Cigar tidiedExpectedCigar = expected.getCigar().tidy();
            Cigar tidiedActualCigar = expected.getCigar().tidy();
            if (tidiedExpectedCigar.equals(tidiedActualCigar)) {
                // tidied cigars match, so overwrite the actual value with expected:
                actual.setCigar(expected.getCigar());
            } else {
                Assert.fail(message);
            }
        }

        if (expected.getInferredInsertSize() != actual.getInferredInsertSize()) {
            // try another definition of insert size: tlen = rightmost - leftmost
            boolean first = actual.getFirstOfPairFlag();
            Tuple<Integer, Integer> left, right;
            int tlen;
            if (first) {
                left = refBoundsOnForwardStrand(actual);
                right = mateRefBoundsOnForwardStrand(actual, actual.getCigar().getReferenceLength());
                tlen = right.b - left.a;
            } else {
                right = refBoundsOnForwardStrand(actual);
                left = mateRefBoundsOnForwardStrand(actual, actual.getCigar().getReferenceLength() + 1);
                tlen = -right.b + left.a;
            }
            // adjust for 1 base:
            tlen += Math.signum(tlen);

            Assert.assertEquals(tlen, expected.getInferredInsertSize());
            actual.setInferredInsertSize(tlen);
        }

        Assert.assertEquals(expected, actual, message);
    }

    /**
     * Compute alignment boundaries in forward reference coordinates for a record
     * @param record a record which mate's alignment start and end is to be computed
     * @return a start/end tuple, both 1-based
     */
    private static Tuple<Integer, Integer> refBoundsOnForwardStrand(SAMRecord record) {
        int left = record.getReadNegativeStrandFlag() ? record.getAlignmentEnd() : record.getAlignmentStart();
        int right = record.getReadNegativeStrandFlag() ? record.getAlignmentStart() : record.getAlignmentEnd();
        return new Tuple<>(left, right);
    }

    /**
     * Compute alignment boundaries in forward reference coordinates for the mate of the given record.
     * @param record a record which mate's alignment start and end is to be computed
     * @param referenceLength number of reference bases taken  by the mate record
     * @return a start/end tuple, both 1-based
     */
    private static Tuple<Integer, Integer> mateRefBoundsOnForwardStrand(SAMRecord record, int referenceLength) {
        int left = record.getMateNegativeStrandFlag() ? record.getMateAlignmentStart() + referenceLength - 1 : record.getMateAlignmentStart();
        int right = record.getMateNegativeStrandFlag() ? record.getMateAlignmentStart() : record.getMateAlignmentStart() + referenceLength - 1;
        return new Tuple<>(left, right);
    }

    /**
     * Assertion method to ensure records compressed with HTSJDK and then restored with HTSJDK match the original SAM
     * records read by HTSJDK.
     * <ul>
     * <li>unknown read bases ({@link SAMRecord#NULL_SEQUENCE}) is not supported in CRAM v2.1</li>
     * <li>CRAM does not support mapping quality score for unmapped reads</li>
     * <li>only tidy cigars survive roundtrip, see {@link Cigar#tidy()} for details</li>
     * </ul>
     *
     * @param major    CRAM major version number
     * @param expected expected original SAM record to compare against
     * @param actual   the actual record read from CRAM
     */
    private static void assertEqual_SAM_HTSJDK_CRAM_HTSJDK_roundtrip(int major, SAMRecord expected, SAMRecord actual) {
        String message = String.format("\nMismatching records:\n%s%s", expected.getSAMString(), actual.getSAMString());

        try {
            // we are going to modify the record, so better make a copy:
            actual = (SAMRecord) actual.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }

        if (major < CramVersions.CRAM_v3.major && expected.getReadBases() == SAMRecord.NULL_SEQUENCE) {
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
}
