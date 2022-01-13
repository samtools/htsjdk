package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileWriter;
import htsjdk.samtools.SAMFileWriterFactory;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMRecordIterator;
import htsjdk.samtools.SamReader;
import htsjdk.samtools.SamReaderFactory;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.RuntimeEOFException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CRAM30ComplianceTest extends HtsjdkTest {
    // NOTE: to run these tests you need to clone https://github.com/samtools/hts-specs and initialize
    // this string to the location of the local repo
    private static final String HTS_SPECS_REPO_LOCATION = "../hts-specs/";

    @DataProvider(name = "30Passed")
    private Object[] getCRAM30Passed() {
        final Set<String> excludeList = new HashSet<String>() {{
            add("0200_cmpr_hdr.cram"); // read failed
            // hasnext() = false,
            // does not have EOF container, slices = 0,
            // lastSliceIndex = -1, fails as it tries to retrieve slice[-1]

            add("1101_BETA.cram"); // read failed
            // Encoding not found: value type=BYTE, encoding id=BETA
            // In the CompressionHeaderEncodingMap, DataSeries = FC_FeatureCode, value_type = BYTE, encodingDescriptor = BETA: (FFFFFFFB0E050000000000000000000000000000).
            // Corresponding htsjdk.samtools.cram.encoding.EncodingFactory entry is not present- there is no case: Byte -> BETA

            add("1001_name.cram"); // sam cram mismatch - ReadNames
            add("1003_qual.cram"); // sam cram mismatch - MateAlignmentStart
            add("1005_qual.cram"); // sam cram mismatch - BaseQualities

            // header mismatch - the roundtripped output file header has SO=unsorted when SO is not present in the original file
            add("0706_tag.cram");
            add("1100_HUFFMAN.cram");
            add("1200_overflow.cram");
            add("0001_empty_eof.cram");
            add("0302_unmapped.cram");
            add("1403_index_multiref.cram");
            add("0503_mapped.cram");
            add("0301_unmapped.cram");
            add("1000_name.cram");
            add("0506_mapped.cram");
            add("0904_comp_rans0.cram");
            add("0705_tag.cram");
            add("0704_tag.cram");
            add("0401_mapped.cram");
            add("1006_seq.cram");
            add("1007_seq.cram");
            add("0708_tag.cram");
            add("1404_index_multislice.cram");
            add("0709_tag.cram");
            add("0802_ctr.cram");
            add("0402_mapped.cram");
            add("0702_tag.cram");
            add("0703_tag.cram");
            add("0600_mapped.cram");
            add("1400_index_simple.cram");
            add("0505_mapped.cram");
            add("0500_mapped.cram");
            add("0901_comp_gz.cram");
            add("0101_header2.cram");
            add("1405_index_multisliceref.cram");
            add("0905_comp_rans1.cram");
            add("0900_comp_raw.cram");
            add("0707_tag.cram");
            add("1004_qual.cram");
            add("0400_mapped.cram");
            add("0903_comp_lzma.cram");
            add("0507_mapped.cram");
            add("0710_tag.cram");
            add("0502_mapped.cram");
            add("1002_qual.cram");
            add("1406_index_long.cram");
            add("1401_index_unmapped.cram");
            add("0902_comp_bz2.cram");
            add("0100_header1.cram");
            add("1402_index_3ref.cram");
            add("0300_unmapped.cram");
            add("0501_mapped.cram");
            add("0701_tag.cram");
            add("0700_tag.cram");
            add("1300_slice_aux.cram");
            add("0601_mapped.cram");
            add("0504_mapped.cram");
            add("0403_mapped.cram");
            add("0800_ctr.cram");
            add("0801_ctr.cram");
            add("0303_unmapped.cram");
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/cram/3.0/passed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .filter(file -> (file.getName()).endsWith(".cram"))
                .toArray();
    }

    @DataProvider(name = "30Failed")
    private Object[] getCRAM30Failed() {
        final Set<String> excludeList = new HashSet<String>() {{
            add(""); // failed
        }};
        return Arrays.stream(getFilesInDir(HTS_SPECS_REPO_LOCATION, "test/cram/3.0/failed/"))
                .filter(file -> !excludeList.contains(file.getName()))
                .filter(file -> (file.getName()).endsWith(".cram"))
                .toArray();
    }

    @Test(dataProvider = "30Passed", description = "Roundtrip the CRAM file. " +
            "Read the output CRAM file and compare it with the corresponding SAM file. " +
            "Compare the original CRAM file with the original SAM file." +
            "Check if their headers and records match.")
    public void testCompareRoundtrippedCRAMwithSAMPassed(final File cramFile) throws IOException {
        final File referenceFile = new File(HTS_SPECS_REPO_LOCATION + "test/cram/ce.fa");
        final File tempOutCRAM = File.createTempFile("testRoundTrip", ".cram");
        tempOutCRAM.deleteOnExit();
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSource(new ReferenceSource(referenceFile))
                .validationStringency((ValidationStringency.SILENT))
                .open(cramFile);
             final SAMFileWriter writer = new SAMFileWriterFactory()
                .makeWriter(reader.getFileHeader(), true,tempOutCRAM, referenceFile)) {
            final SAMRecordIterator inputIterator = reader.iterator();
            while (inputIterator.hasNext()) {
                writer.addAlignment(inputIterator.next());
            }
        }
        final String samFileName = cramFile.getName().replace(".cram",".sam");
        final File originalSamFile = new File(HTS_SPECS_REPO_LOCATION+ "test/cram/3.0/passed/"+samFileName);
        try (final SamReader roundtrippedCramReader = SamReaderFactory.make()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(referenceFile).open(tempOutCRAM);
             final SamReader originalSamReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(originalSamFile);
             final SamReader originalCramReader = SamReaderFactory.make()
                     .validationStringency(ValidationStringency.SILENT)
                     .referenceSequence(referenceFile).open(cramFile);) {
            // Assert the headers in original CRAM file and round tripped CRAM file are the same
            Assert.assertEquals(originalCramReader.getFileHeader(), roundtrippedCramReader.getFileHeader() );
            // Assert the headers in original CRAM file and the original SAM file are the same
            Assert.assertEquals(originalCramReader.getFileHeader(),originalSamReader.getFileHeader());
            final SAMRecordIterator roundtrippedCramIterator = roundtrippedCramReader.iterator();
            final SAMRecordIterator originalSamIterator = originalSamReader.iterator();
            final SAMRecordIterator originalCramIterator = originalCramReader.iterator();
            while (roundtrippedCramIterator.hasNext() && originalSamIterator.hasNext() && originalCramIterator.hasNext()) {
                SAMRecord roundtrippedCramRecord = roundtrippedCramIterator.next();
                SAMRecord originalSamRecord = originalSamIterator.next();
                SAMRecord originalCramRecord = originalCramIterator.next();
                // assert that the same tags are present in both original cram and rountripped cram records
                Assert.assertEquals(
                        originalCramRecord.getAttributes().stream().map(s -> s.tag).collect(Collectors.toSet()),
                        roundtrippedCramRecord.getAttributes().stream().map(s -> s.tag).collect(Collectors.toSet()));
                // assert that the value for each tag is the same in both original cram and rountripped cram records
                originalCramRecord.getAttributes().forEach(tv -> {
                    Assert.assertEquals(tv.value, roundtrippedCramRecord.getAttribute(tv.tag));
                });

                // assert that the same tags are present in both original cram and original sam records
                Assert.assertEquals(
                        originalCramRecord.getAttributes().stream().map(s -> s.tag).collect(Collectors.toSet()),
                        originalSamRecord.getAttributes().stream().map(s -> s.tag).collect(Collectors.toSet()));
                // assert that the value for each tag is the same in both original cram and original sam records
                originalCramRecord.getAttributes().forEach(tv -> {
                    Assert.assertEquals(tv.value, originalSamRecord.getAttribute(tv.tag));
                });
            }
            Assert.assertEquals(originalCramIterator.hasNext(), roundtrippedCramIterator.hasNext());
            Assert.assertEquals(originalCramIterator.hasNext(), originalSamIterator.hasNext());
        }
    }

    @Test(dataProvider = "30Failed", expectedExceptions = RuntimeEOFException.class)
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