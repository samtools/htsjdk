/*
 * The MIT License
 *
 * Copyright (c) 2009-2016 The Broad Institute
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
import htsjdk.samtools.BamIndexValidator.IndexValidationStringency;
import htsjdk.samtools.metrics.MetricBase;
import htsjdk.samtools.metrics.MetricsFile;
import htsjdk.samtools.reference.FastaSequenceFile;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.Histogram;
import htsjdk.samtools.util.StringUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * Tests almost all error conditions detected by the sam file validator. The
 * conditions not tested are proactively prevented by sam generation code.
 *
 * @author Doug Voet
 */
public class ValidateSamFileTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/ValidateSamFileTest");
    private static final int TERMINATION_GZIP_BLOCK_SIZE = 28;
    private static final int RANDOM_NUMBER_TRUNC_BYTE = 128;

    @Test
    public void testValidSamFile() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(new File(TEST_DATA_DIR, "valid.sam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertTrue(results.isEmpty());
    }

    @Test
    public void testValidCRAMFileWithoutSeqDict() throws Exception {
        final File reference = new File(TEST_DATA_DIR, "nm_tag_validation.fa");
        final SamReader samReader = SamReaderFactory
                .makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .referenceSequence(reference)
                .open(new File(TEST_DATA_DIR, "nm_tag_validation.cram"));
        final Histogram<String> results = executeValidation(samReader,
                new FastaSequenceFile(reference, true),
                IndexValidationStringency.EXHAUSTIVE);
        Assert.assertTrue(!results.isEmpty());
    }

    @Test
    public void testSamFileVersion1pt5() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(new File(TEST_DATA_DIR, "test_samfile_version_1pt5.bam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.getCount(),2.0);
    }

    @Test
    public void testSortOrder() throws IOException {
        Histogram<String> results = executeValidation(SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open(new File(TEST_DATA_DIR, "invalid_coord_sort_order.sam")), null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.RECORD_OUT_OF_ORDER.getHistogramString()).getValue(), 1.0);
        results = executeValidation(SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open(new File(TEST_DATA_DIR, "invalid_queryname_sort_order.sam")), null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.RECORD_OUT_OF_ORDER.getHistogramString()).getValue(), 5.0);
    }

    @Test
    public void testVerbose() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 20; i++) {
            samBuilder.addFrag(String.valueOf(i), 1, i, false);
        }
        for (final SAMRecord record : samBuilder) {
            record.setProperPairFlag(true);
        }

        final StringWriter results = new StringWriter();
        final SamFileValidator validator = new SamFileValidator(new PrintWriter(results), 8000);
        validator.setVerbose(true, 10);
        validator.validateSamFileVerbose(samBuilder.getSamReader(), null);

        final int lineCount = results.toString().split("\n").length;
        Assert.assertEquals(lineCount, 11); // 1 extra message added to indicate maximum number of errors
        Assert.assertEquals(validator.getNumErrors(), 6);
        Assert.assertEquals(validator.getNumWarnings(), 4);
    }

    @Test
    public void testUnpairedRecords() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 6; i++) {
            samBuilder.addFrag(String.valueOf(i), i, i, false);
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setProperPairFlag(true);
        records.next().setMateUnmappedFlag(true);
        records.next().setMateNegativeStrandFlag(true);
        records.next().setFirstOfPairFlag(true);
        records.next().setSecondOfPairFlag(true);
        records.next().setMateReferenceIndex(1);

        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_PROPER_PAIR.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_MATE_UNMAPPED.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_MATE_NEG_STRAND.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_FIRST_OF_PAIR.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_SECOND_OF_PAIR.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_MATE_REF_INDEX.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_UNPAIRED_MATE_REFERENCE.getHistogramString()).getValue(), 1.0);
    }

    @Test(dataProvider = "Topologies")
    public void testPairedRecords(final SAMSequenceRecord.Topology topology) throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        final SAMSequenceDictionary dict = samBuilder.getHeader().getSequenceDictionary();
        for (int i = 0; i < 6; i++) {
            samBuilder.addPair(String.valueOf(i), i, i, i + 100);
            dict.getSequence(i).setTopology(topology);
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setMateReferenceName("*");
        records.next().setMateAlignmentStart(Integer.MAX_VALUE); // mate start off the end
        records.next().setMateAlignmentStart(records.next().getAlignmentStart() + 1);
        records.next().setMateNegativeStrandFlag(!records.next().getReadNegativeStrandFlag());
        records.next().setMateReferenceIndex(records.next().getReferenceIndex() + 1);
        records.next().setMateUnmappedFlag(!records.next().getReadUnmappedFlag());
        final int start = dict.getSequence(records.next().getContig()).getSequenceLength() + 1;
        records.next().setAlignmentStart(start); // rec start off the end

        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_ALIGNMENT_START.getHistogramString()).getValue(), (topology == SAMSequenceRecord.Topology.circular) ? 2.0 : 4.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_MATE_UNMAPPED.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_FLAG_MATE_NEG_STRAND.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_FLAG_MATE_UNMAPPED.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_MATE_ALIGNMENT_START.getHistogramString()).getValue(), 3.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_MATE_REF_INDEX.getHistogramString()).getValue(), 2.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_UNALIGNED_MATE_START.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testSkipMateValidation() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 5; i++) {
            samBuilder.addPair(String.valueOf(i), i, i, i + 100);
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setMateReferenceName("*");
        records.next().setMateAlignmentStart(Integer.MAX_VALUE);
        records.next().setMateAlignmentStart(records.next().getAlignmentStart() + 1);
        records.next().setMateNegativeStrandFlag(!records.next().getReadNegativeStrandFlag());
        records.next().setMateReferenceIndex(records.next().getReferenceIndex() + 1);
        records.next().setMateUnmappedFlag(!records.next().getReadUnmappedFlag());

        final Histogram<String> results = executeValidationWithErrorIgnoring(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE, Collections.EMPTY_LIST, true);

        Assert.assertNull(results.get(SAMValidationError.Type.MISMATCH_FLAG_MATE_NEG_STRAND.getHistogramString()));
        Assert.assertNull(results.get(SAMValidationError.Type.MISMATCH_FLAG_MATE_UNMAPPED.getHistogramString()));
        Assert.assertNull(results.get(SAMValidationError.Type.MISMATCH_MATE_ALIGNMENT_START.getHistogramString()));
        Assert.assertNull(results.get(SAMValidationError.Type.MISMATCH_MATE_REF_INDEX.getHistogramString()));
    }

    @Test(dataProvider = "missingMateTestCases")
    public void testMissingMate(final SAMFileHeader.SortOrder sortOrder) throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder(true, sortOrder);

        samBuilder.addPair(String.valueOf(1), 1, 1, 101);
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next();
        records.remove();
        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.MATE_NOT_FOUND.getHistogramString()).getValue(), 1.0);
    }

    @DataProvider(name = "missingMateTestCases")
    public Object[][] missingMateTestCases() {
        return new Object[][]{
                {SAMFileHeader.SortOrder.coordinate},
                {SAMFileHeader.SortOrder.queryname},
                {SAMFileHeader.SortOrder.unsorted},
        };
    }

    @Test
    public void testUnmappedRecords() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 4; i++) {
            samBuilder.addUnmappedFragment(String.valueOf(i));
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setReadNegativeStrandFlag(true);
        records.next().setSecondaryAlignment(true);
        records.next().setMappingQuality(10);
        records.next().setCigarString("36M");

        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_NOT_PRIM_ALIGNMENT.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_MAPPING_QUALITY.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testMappedRecords() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 2; i++) {
            samBuilder.addFrag(String.valueOf(i), i, i, false);
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setCigarString("25M3S25M");
        records.next().setReferenceName("*");

        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_CIGAR.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_FLAG_READ_UNMAPPED.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISSING_TAG_NM.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_CIGAR_SEQ_LENGTH.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testNmFlagValidation() throws IOException {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();

        for (int i = 0; i < 3; i++) {
            samBuilder.addFrag(String.valueOf(i), i, i + 1, false);
        }
        final Iterator<SAMRecord> records = samBuilder.iterator();
        records.next().setAttribute(ReservedTagConstants.NM, 4);

        // PIC-215: Confirm correct NM value when there is an insertion and a deletion.
        final SAMRecord recordWithInsert = records.next();
        final byte[] sequence = recordWithInsert.getReadBases();
        Arrays.fill(sequence, (byte) 'A');
        recordWithInsert.setReadBases(sequence);
        recordWithInsert.setCigarString("1D" + Integer.toString(sequence.length - 1) + "M1I");
        recordWithInsert.setAttribute(ReservedTagConstants.NM, 2);

        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), new ReferenceSequenceFile() {
            private int index = 0;

            @Override
            public SAMSequenceDictionary getSequenceDictionary() {
                return null;
            }

            @Override
            public ReferenceSequence nextSequence() {
                final byte[] bases = new byte[10000];
                Arrays.fill(bases, (byte) 'A');
                return new ReferenceSequence("foo", index++, bases);
            }

            @Override
            public void reset() {
                this.index = 0;
            }

            @Override
            public boolean isIndexed() { return false; }

            @Override
            public ReferenceSequence getSequence(final String contig) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ReferenceSequence getSubsequenceAt(final String contig, final long start, final long stop) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void close() throws IOException {
                //no-op
            }
        }, IndexValidationStringency.EXHAUSTIVE);

        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_TAG_NM.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISSING_TAG_NM.getHistogramString()).getValue(), 1.0);
    }

    @Test(dataProvider = "testMateCigarScenarios")
    public void testMateCigarScenarios(final String scenario, final String inputFile, final SAMValidationError.Type expectedError)
            throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(new File(TEST_DATA_DIR, inputFile));
        final Histogram<String> results = executeValidation(reader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertNotNull(results.get(expectedError.getHistogramString()), scenario);
        Assert.assertEquals(results.get(expectedError.getHistogramString()).getValue(), 1.0, scenario);
    }

    @DataProvider(name = "testMateCigarScenarios")
    public Object[][] testMateCigarScenarios() {
        return new Object[][]{
                {"invalid mate cigar", "invalid_mate_cigar_string.sam", SAMValidationError.Type.MISMATCH_MATE_CIGAR_STRING},
                {"inappropriate mate cigar", "inappropriate_mate_cigar_string.sam", SAMValidationError.Type.MATE_CIGAR_STRING_INVALID_PRESENCE}
        };
    }

    @Test(dataProvider = "testTruncatedScenarios")
    public void testTruncated(final String scenario, final String inputFile, final SAMValidationError.Type expectedError)
            throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(new File(TEST_DATA_DIR, inputFile));
        final Histogram<String> results = executeValidation(reader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertNotNull(results.get(expectedError.getHistogramString()), scenario);
        Assert.assertEquals(results.get(expectedError.getHistogramString()).getValue(), 1.0, scenario);
    }

    @DataProvider(name = "testTruncatedScenarios")
    public Object[][] testTruncatedScenarios() {
        return new Object[][]{
                {"truncated bam", "truncated.bam", SAMValidationError.Type.TRUNCATED_FILE},
                {"truncated quals", "truncated_quals.sam", SAMValidationError.Type.MISMATCH_READ_LENGTH_AND_QUALS_LENGTH},
                // TODO: Because validation is turned off when parsing, this error is not detectable currently by validator.
                //{"truncated tag", "truncated_tag.sam", SAMValidationError.Type.TRUNCATED_FILE},
                // TODO: Currently, this is not considered an error.  Should it be?
                //{"hanging tab", "hanging_tab.sam", SAMValidationError.Type.TRUNCATED_FILE},
        };
    }

    @Test(expectedExceptions = SAMException.class, dataProvider = "testFatalParsingErrors")
    public void testFatalParsingErrors(final String scenario, final String inputFile) throws Exception {
        final SamReader reader = SamReaderFactory.makeDefault().open(new File(TEST_DATA_DIR, inputFile));
        executeValidation(reader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.fail("Exception should have been thrown.");
    }

    @DataProvider(name = "testFatalParsingErrors")
    public Object[][] testFatalParsingErrorScenarios() {
        return new Object[][]{
                {"missing fields", "missing_fields.sam"},
                {"zero length read", "zero_length_read.sam"}
        };
    }

    @Test(dataProvider = "testQualitiesNotStored")
    public void testNotStoredQualitiesFields(final String inputFile, final double expectedValue) throws IOException {
        try (final SamReader reader = SamReaderFactory.makeDefault().open((new File(TEST_DATA_DIR, inputFile)))) {
            final Histogram<String> results = executeValidation(reader, null, IndexValidationStringency.EXHAUSTIVE);
            Assert.assertEquals(results.get(SAMValidationError.Type.QUALITY_NOT_STORED.getHistogramString()).getValue(), expectedValue);
        }
    }

    @DataProvider(name="testQualitiesNotStored")
    public Object[][] NotStoredQualitiesFieldsScenarios() {
            return new Object[][]{
                    {"not_stored_qualities_more_than_100.sam",100.0},
                    {"not_stored_qualities.sam", 2.0},
                    {"not_stored_qualities.bam", 1.0}
            };
    }

    @Test
    public void testHeaderVersionValidation() throws Exception {
        final String header = "@HD	VN:Hi,Mom!	SO:queryname";
        final InputStream strm = new ByteArrayInputStream(StringUtil.stringToBytes(header));
        final SamReader samReader = SamReaderFactory.makeDefault().open(SamInputResource.of(strm));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_VERSION_NUMBER.getHistogramString()).getValue(), 1.0);
    }

    @Test(enabled = false, description = "File is actually valid for Standard quality scores so this test fails with an NPE.")
    public void testQualityFormatValidation() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().open(new File("./src/test/resources/htsjdk/samtools/util/QualityEncodingDetectorTest/illumina-as-standard.bam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        final Histogram.Bin<String> bin = results.get(SAMValidationError.Type.INVALID_QUALITY_FORMAT.getHistogramString());
        final double value = bin.getValue();
        Assert.assertEquals(value, 1.0);
    }

    @DataProvider(name="Topologies")
    public Object[][] Topologies() {
        return new Object[][]{
                {null},
                {SAMSequenceRecord.Topology.linear},
                {SAMSequenceRecord.Topology.circular},
        };
    }

    @Test(dataProvider = "Topologies")
    public void testCigarOffEndOfReferenceValidation(final SAMSequenceRecord.Topology topology) throws Exception {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();
        samBuilder.getHeader().getSequenceDictionary().getSequence(0).setTopology(topology); // set the topology
        samBuilder.addFrag(String.valueOf(0), 0, 1, false);
        final int contigLength = samBuilder.getHeader().getSequence(0).getSequenceLength();
        // Should hang off the end.
        samBuilder.addFrag(String.valueOf(1), 0, contigLength - 1, false);
        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);
        if (topology == SAMSequenceRecord.Topology.circular) {
            Assert.assertNull(results.get(SAMValidationError.Type.CIGAR_MAPS_OFF_REFERENCE.getHistogramString()));
        }
        else {
            Assert.assertNotNull(results.get(SAMValidationError.Type.CIGAR_MAPS_OFF_REFERENCE.getHistogramString()));
            Assert.assertEquals(results.get(SAMValidationError.Type.CIGAR_MAPS_OFF_REFERENCE.getHistogramString()).getValue(), 1.0);
        }
    }
    
    @Test
    public void testCigarNoSeqValidation() throws Exception {
        final SAMRecordSetBuilder samBuilder = new SAMRecordSetBuilder();
        samBuilder.addFrag("name", 0, 1, false);
        samBuilder.iterator().next().setReadBases(SAMRecord.NULL_SEQUENCE);
        samBuilder.iterator().next().setBaseQualities(SAMRecord.NULL_SEQUENCE);
        final Histogram<String> results = executeValidation(samBuilder.getSamReader(), null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertNull(results.get(SAMValidationError.Type.MISMATCH_CIGAR_SEQ_LENGTH .getHistogramString()));
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testConflictingTags() throws Exception {
        final String header = "@HD	VN:1.0	SO:queryname	SO:coordinate";
        final InputStream strm = new ByteArrayInputStream(StringUtil.stringToBytes(header));
        final SamReader reader = SamReaderFactory.makeDefault().open(SamInputResource.of(strm));
        Assert.fail("Exception should have been thrown.");
    }

    @Test
    public void testRedundantTags() throws Exception {
        final String header = "@HD	VN:1.0	SO:coordinate	SO:coordinate";
        final InputStream strm = new ByteArrayInputStream(StringUtil.stringToBytes(header));
        final SamReader samReader = SamReaderFactory.makeDefault().open(SamInputResource.of(strm));
        Assert.assertEquals(SAMFileHeader.SortOrder.coordinate, samReader.getFileHeader().getSortOrder());
        CloserUtil.close(samReader);
    }

    @Test
    public void testHeaderValidation() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open(new File(TEST_DATA_DIR, "buggyHeader.sam"));
        final File referenceFile = new File(TEST_DATA_DIR, "../hg19mini.fasta");
        final ReferenceSequenceFile reference = new FastaSequenceFile(referenceFile, false);
        final Histogram<String> results = executeValidation(samReader, reference, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.UNRECOGNIZED_HEADER_TYPE.getHistogramString()).getValue(), 3.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.HEADER_TAG_MULTIPLY_DEFINED.getHistogramString()).getValue(), 1.0);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_FILE_SEQ_DICT.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testSeqQualMismatch() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open(new File(TEST_DATA_DIR, "seq_qual_len_mismatch.sam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISMATCH_SEQ_QUAL_LENGTH.getHistogramString()).getValue(), 8.0);
    }

    @Test
    public void testPlatformMissing() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open((new File(TEST_DATA_DIR, "missing_platform_unit.sam")));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.MISSING_PLATFORM_VALUE.getHistogramString()).getValue(), 1.0);
    }
    
    @Test
    public void testPlatformInvalid() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open((new File(TEST_DATA_DIR, "invalid_platform_unit.sam")));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_PLATFORM_VALUE.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testDuplicateRGIDs() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .open((new File(TEST_DATA_DIR, "duplicate_rg.sam")));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.DUPLICATE_READ_GROUP_ID.getHistogramString()).getValue(), 1.0);
    }

    @Test
    public void testIndexFileValidation() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT)
                .enable(SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES).open((new File(TEST_DATA_DIR, "bad_index.bam")));

        Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_INDEX_FILE_POINTER.getHistogramString()).getValue(), 1.0);

        results = executeValidation(samReader, null, IndexValidationStringency.LESS_EXHAUSTIVE);
        Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_INDEX_FILE_POINTER.getHistogramString()).getValue(), 1.0);

    }

    private void testHeaderVersion(final String version, final boolean expectValid) throws Exception {
        final File samFile = File.createTempFile("validateHeader.", ".sam");
        samFile.deleteOnExit();
        final PrintWriter pw = new PrintWriter(samFile);
        pw.println("@HD\tVN:" + version);
        pw.close();
        final SamReader reader = SamReaderFactory.makeDefault().open(samFile);
        final Histogram<String> results = executeValidation(reader, null, IndexValidationStringency.EXHAUSTIVE);
        if (expectValid) Assert.assertNull(results.get(SAMValidationError.Type.INVALID_VERSION_NUMBER.getHistogramString()));
        else {
            Assert.assertNotNull(results.get(SAMValidationError.Type.INVALID_VERSION_NUMBER.getHistogramString()));
            Assert.assertEquals(results.get(SAMValidationError.Type.INVALID_VERSION_NUMBER.getHistogramString()).getValue(), 1.0);
        }
    }

    @Test
    public void testHeaderVersions() throws Exception {
        // Test the acceptable versions
        for (final String version : SAMFileHeader.ACCEPTABLE_VERSIONS) {
            testHeaderVersion(version, true);
        }

        // Test an unacceptable version
        testHeaderVersion("1.1", false);
    }

    @Test(enabled = false)
    public void duplicateReads() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(new File(TEST_DATA_DIR, "duplicated_reads.sam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertFalse(results.isEmpty());
        Assert.assertEquals(results.get(SAMValidationError.Type.MATES_ARE_SAME_END.getHistogramString()).getValue(), 2.0);
    }

    @Test
    public void duplicateReadsOutOfOrder() throws Exception {
        final SamReader samReader = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.SILENT).open(new File(TEST_DATA_DIR, "duplicated_reads_out_of_order.sam"));
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertFalse(results.isEmpty());
        Assert.assertEquals(results.get(SAMValidationError.Type.MATES_ARE_SAME_END.getHistogramString()).getValue(), 2.0);
    }


    @DataProvider(name = "TagCorrectlyProcessData")
    public Object[][] tagCorrectlyProcessData() throws IOException {
        final String E2TagCorrectlyProcessTestData =
                "@HD\tVN:1.0\tSO:unsorted\n" +
                        "@SQ\tSN:chr1\tLN:101\n" +
                        "@RG\tID:0\tSM:Hi,Mom!\n" +
                        "E\t147\tchr1\t15\t255\t10M\t=\t2\t-30\tCAACAGAAGC\t)'.*.+2,))\tE2:Z:CAA";

        final String U2TagCorrectlyProcessTestData =
                "@HD\tVN:1.0\tSO:unsorted\n" +
                        "@SQ\tSN:chr1\tLN:101\n" +
                        "@RG\tID:0\tSM:Hi,Mom!\n" +
                        "E\t147\tchr1\t15\t255\t10M\t=\t2\t-30\tCAACAGAAGC\t)'.*.+2,))\tU2:Z:CAA";

        final String GOTagCorrectlyProcessTestData =
                "@HD\tVN:1.0\tGO:NOTKNOWN\n" +
                        "@SQ\tSN:chr1\tLN:101\n" +
                        "@RG\tID:0\tSM:Hi,Mom!\n" +
                        "E\t147\tchr1\t15\t255\t10M\t=\t2\t-30\tCAACAGAAGC\t)'.*.+2,))\tU2:Z:CAA";

        return new Object[][]{
                {E2TagCorrectlyProcessTestData.getBytes(), SAMValidationError.Type.E2_BASE_EQUALS_PRIMARY_BASE},
                {E2TagCorrectlyProcessTestData.getBytes(), SAMValidationError.Type.MISMATCH_READ_LENGTH_AND_E2_LENGTH},
                {U2TagCorrectlyProcessTestData.getBytes(), SAMValidationError.Type.MISMATCH_READ_LENGTH_AND_U2_LENGTH},
                {GOTagCorrectlyProcessTestData.getBytes(), SAMValidationError.Type.HEADER_TAG_NON_CONFORMING_VALUE}
        };
    }

    @Test(dataProvider = "TagCorrectlyProcessData")
    public void tagCorrectlyProcessTest(byte[] bytesFromFile,
                                        SAMValidationError.Type errorType) throws Exception {
        final SamReader samReader = SamReaderFactory
                .makeDefault()
                .validationStringency(ValidationStringency.SILENT)
                .open(
                        SamInputResource.of(
                                new ByteArrayInputStream(bytesFromFile)
                        )
                );
        final Histogram<String> results = executeValidation(samReader, null, IndexValidationStringency.EXHAUSTIVE);
        Assert.assertEquals(results.get(errorType.getHistogramString()).getValue(), 1.0);
    }

    @DataProvider(name = "validateBamFileTerminationData")
    public Object[][] validateBamFileTerminationData() throws IOException {
        return new Object[][]{
                {getBrokenFile(TERMINATION_GZIP_BLOCK_SIZE), SAMValidationError.Type.BAM_FILE_MISSING_TERMINATOR_BLOCK, 1, 0},
                {getBrokenFile(RANDOM_NUMBER_TRUNC_BYTE), SAMValidationError.Type.TRUNCATED_FILE, 0, 1}
        };
    }

    @Test(dataProvider = "validateBamFileTerminationData")
    public void validateBamFileTerminationTest(final File file, final SAMValidationError.Type errorType, final int numWarnings, final int numErrors) throws IOException {
        final SamFileValidator samFileValidator = new SamFileValidator(new PrintWriter(System.out), 8000);
        samFileValidator.validateBamFileTermination(file);
        Assert.assertEquals(samFileValidator.getErrorsByType().get(errorType).getValue(), 1.0);
        Assert.assertEquals(samFileValidator.getNumWarnings(), numWarnings);
        Assert.assertEquals(samFileValidator.getNumErrors(), numErrors);
    }

    private Histogram<String> executeValidation(final SamReader samReader, final ReferenceSequenceFile reference,
                                                final IndexValidationStringency stringency) throws IOException {
        return executeValidationWithErrorIgnoring(samReader, reference, stringency, Collections.EMPTY_LIST, false);
    }

    private Histogram<String> executeValidationWithErrorIgnoring(final SamReader samReader,
                                                                 final ReferenceSequenceFile reference,
                                                                 final IndexValidationStringency stringency,
                                                                 final Collection<SAMValidationError.Type> ignoringError,
                                                                 final boolean skipMateValidation) throws IOException {
        final File outFile = File.createTempFile("validation", ".txt");
        outFile.deleteOnExit();

        final PrintWriter out = new PrintWriter(outFile);
        final SamFileValidator samFileValidator = new SamFileValidator(out, 8000);
        samFileValidator.setIndexValidationStringency(stringency).setErrorsToIgnore(ignoringError);
        samFileValidator.setSkipMateValidation(skipMateValidation);
        samFileValidator.validateSamFileSummary(samReader, reference);

        final LineNumberReader reader = new LineNumberReader(new FileReader(outFile));
        if (reader.readLine().equals("No errors found")) {
            return new Histogram<>();
        }
        final MetricsFile<MetricBase, String> outputFile = new MetricsFile<>();
        outputFile.read(new FileReader(outFile));
        Assert.assertNotNull(outputFile.getHistogram());
        return outputFile.getHistogram();
    }

    private File getBrokenFile(int truncByte) throws IOException {
        final FileChannel stream = FileChannel.open(new File(TEST_DATA_DIR + "/test_samfile_version_1pt5.bam").toPath());
        final File breakingFile = File.createTempFile("trunc", ".bam");
        breakingFile.deleteOnExit();
        FileChannel.open(breakingFile.toPath(), StandardOpenOption.WRITE).transferFrom(stream, 0, stream.size() - truncByte);
        return breakingFile;
    }
}
