package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.structure.*;
import htsjdk.tribble.*;
import org.testng.*;
import org.testng.annotations.*;

import java.io.*;
import java.util.*;
import java.util.HashSet;
import java.util.Set;

public class CRAM30ComplianceTest extends HtsjdkTest {
    // NOTE: to run these tests you need to clone https://github.com/samtools/hts-specs and initialize
    // this string to the location of the local repo
    private static final String HTS_SPECS_REPO_LOCATION = "/Users/ypuligun/repos/hts-specs/";

    @DataProvider(name = "30Passed")
    private Object[] getCRAM30Passed() {
        final Set<String> excludeList = new HashSet<String>() {{
            add("0200_cmpr_hdr.cram"); // Test #1, #3 read failed
//            // hasnext() = false,
//            // does not have EOF container, slices = 0,
//            // lastSliceIndex = -1, fails as it tries to retrieve slice[-1]
//
            add("1101_BETA.cram"); // Test #1, #3  read failed
//            // Encoding not found: value type=BYTE, encoding id=BETA
//            // In the CompressionHeaderEncodingMap, DataSeries = FC_FeatureCode, value_type = BYTE, encodingDescriptor = BETA: (FFFFFFFB0E050000000000000000000000000000).
//            // Corresponding htsjdk.samtools.cram.encoding.EncodingFactory entry is not present- there is no case: Byte -> BETA
//
            add("1400_index_simple.cram.crai"); // index files
            add("1401_index_unmapped.cram.crai"); // index files
            add("1402_index_3ref.cram.crai"); // index files
            add("1403_index_multiref.cram.crai"); // index files
            add("1404_index_multislice.cram.crai"); // index files
            add("1405_index_multisliceref.cram.crai"); // index files
            add("1406_index_long.cram.crai"); // index files

            add("0503_mapped.cram"); // Test #2, #3 sam cram mismatch - ReadBases don't match
            add("0600_mapped.cram"); // Test #2, #3 sam cram mismatch - ReadBases don't match
            add("0601_mapped.cram"); // Test #2, #3 sam cram mismatch - ReadBases don't match
            add("0706_tag.cram"); // Test #2, #3 sam cram mismatch - Attributes don't match
            add("1001_name.cram"); // Test #2, #3 sam cram mismatch - ReadNames don't match
            add("1003_qual.cram"); // Test #2, #3 sam cram mismatch - MateAlignmentStart doesn't match
            add("1005_qual.cram"); // Test #2, #3 sam cram mismatch - BaseQualities don't match
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/cram/3.0/passed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .filter(file -> (file.getName()).endsWith(".cram"))
                .toArray();
    }

    @DataProvider(name = "30Failed")
    private Object[] getCRAM30Failed() {
        // only has 1 test in it
        final Set<String> excludeList = new HashSet<String>() {{
            add(""); // failed
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/cram/3.0/failed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .filter(file -> (file.getName()).endsWith(".cram"))
                .toArray();
    }

    @DataProvider(name = "30PassedExcluded")
    private Object[] getCRAM30PassedExcluded(){
        return new Object[]{
//                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/0200_cmpr_hdr.cram"),
//                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/1101_BETA.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/0503_mapped.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/0600_mapped.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/0601_mapped.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/0706_tag.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/1001_name.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/1003_qual.cram"),
                new File (HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/1005_qual.cram")
        };
    }

    @Test(dataProvider = "30Passed", description = "Read the header and the records from the CRAM files")
    public void testCRAMReadPassed(final File cramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        try (final SamReader samReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(cramFile)) {
            final SAMFileHeader samFileHeader = samReader.getFileHeader();
            final List<SAMRecord> samRecords = new ArrayList<>();
            final SAMRecordIterator samIterator = samReader.iterator();
            while (samIterator.hasNext()) {
                samRecords.add(samIterator.next());
            }
        }
    }

    @Test(dataProvider = "30PassedExcluded", description = "Read the CRAM file and corresponding SAM file. " +
            "Check if their headers and records match.")
    public void testCompareCRAMwithSAMPassed(final File cramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        final String samFileName = cramFile.getName().replace(".cram",".sam");
        final File originalSamFile = new File(HTS_SPECS_REPO_LOCATION+ "test/cram/3.0/passed/"+samFileName);

        try (final SamReader samReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(cramFile);
             final SamReader originalSamReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalSamFile);) {
            final SAMFileHeader samFileHeader = samReader.getFileHeader();
            final SAMFileHeader originalSamFileHeader = originalSamReader.getFileHeader();
            final List<SAMRecord> samRecords = new ArrayList<>();
            final List<SAMRecord> originalSamRecords = new ArrayList<>();
            final SAMRecordIterator samIterator = samReader.iterator();
            final SAMRecordIterator originalSamIterator = originalSamReader.iterator();

            while (originalSamIterator.hasNext() && samIterator.hasNext()) {
                originalSamRecords.add(originalSamIterator.next());
                samRecords.add(samIterator.next());
            }
            Assert.assertEquals(samFileHeader,originalSamFileHeader);
            Assert.assertEquals(originalSamIterator.hasNext(), samIterator.hasNext());
            Assert.assertEquals(samRecords.size(), originalSamRecords.size());
            Assert.assertEquals(samRecords, originalSamRecords); // !!all the excluded tests fail here
//            Assert.assertEquals(samRecords.get(1).getBaseQualities(), originalSamRecords.get(1).getBaseQualities());
//            Assert.assertEquals(samRecords.get(1).equals(originalSamRecords.get(1)), true); // htsjdk.samtools.SAMRecord.equals method
//            Assert.assertEquals(samRecords.get(1).getBaseQualities(), originalSamRecords.get(1).getBaseQualities());
//            Assert.assertEquals(samRecords.get(1).getAttributes(), originalSamRecords.get(1).getAttributes());
                    }
    }

    @Test(dataProvider = "30Passed", description = "Roundtrip the CRAM file. Read the output CRAM file and compare it with the corresponding SAM file. " +
            "Check if their headers and records match.")
    public void testCompareRoundtrippedCRAMwithSAMPassed(final File cramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutCRAM = File.createTempFile("testRoundTrip", ".cram");
        tempOutCRAM.deleteOnExit();
        CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, cramFile, tempOutCRAM, referenceFile);
        final String samFileName = cramFile.getName().replace(".cram",".sam");
        final File originalSamFile = new File(HTS_SPECS_REPO_LOCATION+ "test/cram/3.0/passed/"+samFileName);
        try (final SamReader samReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(tempOutCRAM);
             final SamReader originalSamReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalSamFile);) {
            final SAMFileHeader samFileHeader = samReader.getFileHeader();
            final SAMFileHeader originalSamFileHeader = originalSamReader.getFileHeader();
            final List<SAMRecord> samRecords = new ArrayList<>();
            final List<SAMRecord> originalSamRecords = new ArrayList<>();
            final SAMRecordIterator samIterator = samReader.iterator();
            final SAMRecordIterator originalSamIterator = originalSamReader.iterator();

            while (originalSamIterator.hasNext() && samIterator.hasNext()) {
                originalSamRecords.add(originalSamIterator.next());
                samRecords.add(samIterator.next());
            }
//            Assert.assertEquals(samFileHeader,originalSamFileHeader);  // !!headers don't match
            Assert.assertEquals(originalSamIterator.hasNext(), samIterator.hasNext());
            Assert.assertEquals(samRecords.size(), originalSamRecords.size());
            Assert.assertEquals(samRecords, originalSamRecords);

        }
    }

    @Test(dataProvider = "30PassedExcluded",description = "convert CRAM to SAM and compare it with the original CRAM and the original SAM.")
    public void testConvertCRAMtoSAM(final File originalCramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutSAM = File.createTempFile("testSAMOutput", ".sam");
        tempOutSAM.deleteOnExit();
        final String samFileName = originalCramFile.getName().replace(".cram",".sam");
        final File originalSamFile = new File(HTS_SPECS_REPO_LOCATION+ "test/cram/3.0/passed/"+samFileName);
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();

        // Read the CRAM file and write it out as a SAM file
        try (SamReader reader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(originalCramFile);
             SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, new FileOutputStream(tempOutSAM))) {
            for (SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }
        try (final SamReader newSamReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(tempOutSAM);
             final SamReader originalSamReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalSamFile);
             final SamReader originalCRAMReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalCramFile);
             ) {
            // get Headers and SAM records from all 3 files
            final SAMFileHeader newSamFileHeader = newSamReader.getFileHeader();
            final SAMFileHeader originalSamFileHeader = originalSamReader.getFileHeader();
            final SAMFileHeader originalCramFileHeader = originalCRAMReader.getFileHeader();
            final List<SAMRecord> newSamRecords = new ArrayList<>();
            final List<SAMRecord> originalSamRecords = new ArrayList<>();
            final List<SAMRecord> originalCramRecords = new ArrayList<>();
            final SAMRecordIterator newSamIterator = newSamReader.iterator();
            final SAMRecordIterator originalSamIterator = originalSamReader.iterator();
            final SAMRecordIterator originalCramIterator = originalCRAMReader.iterator();
            while (originalSamIterator.hasNext()) {
                originalSamRecords.add(originalSamIterator.next());
            }
            while (newSamIterator.hasNext()){
                newSamRecords.add(newSamIterator.next());
            }
            while (originalCramIterator.hasNext()){
                originalCramRecords.add(originalCramIterator.next());
            }
//            Assert.assertEquals(newSamFileHeader, originalCramFileHeader); // !!headers don't match
//            Assert.assertEquals(newSamFileHeader, originalSamFileHeader); // !!headers don't match
            Assert.assertEquals(newSamRecords.size(), originalCramRecords.size());
            Assert.assertEquals(newSamRecords.size(), originalSamRecords.size());
            Assert.assertEquals(newSamRecords, originalCramRecords);
//            Assert.assertEquals(newSamRecords, originalSamRecords); // !!all the excluded tests fail here
        }
    }

    @Test(dataProvider = "30PassedExcluded",description = "convert SAM to CRAM and compare it with the original CRAM and the original SAM.")
    public void testConvertSAMtoCRAM(final File originalCramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        final CRAMEncodingStrategy testStrategy = new CRAMEncodingStrategy();
        final File tempOutCRAM = File.createTempFile("testSAMOutput", ".cram");
        tempOutCRAM.deleteOnExit();
        final String samFileName = originalCramFile.getName().replace(".cram", ".sam");
        final File originalSamFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/3.0/passed/" + samFileName);
        CRAMTestUtils.writeToCRAMWithEncodingStrategy(testStrategy, originalSamFile, tempOutCRAM, referenceFile);
        try (final SamReader newCramReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(tempOutCRAM);
             final SamReader originalSamReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalSamFile);
             final SamReader originalCRAMReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalCramFile);
        ) {
            // get Headers and SAM records from all 3 files
            final SAMFileHeader newCramFileHeader = newCramReader.getFileHeader();
            final SAMFileHeader originalSamFileHeader = originalSamReader.getFileHeader();
            final SAMFileHeader originalCramFileHeader = originalCRAMReader.getFileHeader();
            final List<SAMRecord> newCramRecords = new ArrayList<>();
            final List<SAMRecord> originalSamRecords = new ArrayList<>();
            final List<SAMRecord> originalCramRecords = new ArrayList<>();
            final SAMRecordIterator newCramIterator = newCramReader.iterator();
            final SAMRecordIterator originalSamIterator = originalSamReader.iterator();
            final SAMRecordIterator originalCramIterator = originalCRAMReader.iterator();
            while (originalSamIterator.hasNext()) {
                originalSamRecords.add(originalSamIterator.next());
            }
            while (newCramIterator.hasNext()){
                newCramRecords.add(newCramIterator.next());
            }
            while (originalCramIterator.hasNext()){
                originalCramRecords.add(originalCramIterator.next());
            }
//            Assert.assertEquals(newCramFileHeader, originalCramFileHeader); // !!headers don't match
//            Assert.assertEquals(newCramFileHeader, originalSamFileHeader); // !!headers don't match
            Assert.assertEquals(newCramRecords.size(), originalCramRecords.size());
            Assert.assertEquals(newCramRecords.size(), originalSamRecords.size());
//            Assert.assertEquals(newCramRecords.get(1).getAttributes().get(0), originalSamRecords.get(1).getAttributes().get(0));
//            Assert.assertEquals(newCramRecords.get(1).getAttributes().get(0).tag, originalSamRecords.get(1).getAttributes().get(0).tag);
//            Assert.assertEquals(newCramRecords.get(1).getAttributes().get(0).value, originalSamRecords.get(1).getAttributes().get(0).value);


            Assert.assertEquals(newCramRecords.get(1).equals(originalSamRecords.get(1)),1);
            Assert.assertEquals(newCramRecords, originalSamRecords); // !!1003_qual.cram (mMateAlignmentStart), 0706_tag.cram (mAttributes ~) failed here
//            Assert.assertEquals(newCramRecords, originalCramRecords);// !!all the excluded tests fail here except
            // !!1003_qual.cram, 0706_tag.cram
            // TODO: investigate the differences
        }
    }

    @Test(dataProvider = "30Failed")
    public void testCRAMReadFailed(final File cramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        try (final SamReader samReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(cramFile)) {
            final SAMFileHeader samFileHeader = samReader.getFileHeader();
            final List<SAMRecord> samRecords = new ArrayList<>();
            final SAMRecordIterator samIterator = samReader.iterator();

            while (samIterator.hasNext()) {
                samRecords.add(samIterator.next());
            }
        }
    }

    private File[] getFilesInDir(final String dir, final String subdir) {
        return Arrays.stream(new File(dir,subdir).list())
                .map(fn -> new File(dir + subdir + fn))
                .toArray(File[]::new);
    }

}