package htsjdk.samtools.cram.spec30;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.CloseableIterator;
import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.testng.Assert;

/**
 * Shared utilities for tests that validate htsjdk against the hts-specs CRAM test suite.
 * Test data is at {@code src/test/resources/htsjdk/hts-specs/test/cram/}.
 */
public class HtsSpecsComplianceTestBase extends HtsjdkTest {

    protected static final Path SPEC_30_DIR = Path.of("src/test/resources/htsjdk/hts-specs/test/cram/3.0/passed");
    protected static final Path SPEC_30_FAILED_DIR =
            Path.of("src/test/resources/htsjdk/hts-specs/test/cram/3.0/failed");
    protected static final Path SPEC_31_DIR = Path.of("src/test/resources/htsjdk/hts-specs/test/cram/3.1/passed");
    protected static final File REFERENCE = new File("src/test/resources/htsjdk/hts-specs/test/cram/ce.fa");

    /**
     * Decode a CRAM file from the 3.0/passed directory and return its records.
     */
    protected List<SAMRecord> decodeCram(final String basename) throws IOException {
        return decodeCramFile(SPEC_30_DIR.resolve(basename + ".cram").toFile());
    }

    /**
     * Decode a CRAM file and return its records.
     */
    protected List<SAMRecord> decodeCramFile(final File cramFile) throws IOException {
        final ReferenceSource source = new ReferenceSource(REFERENCE);
        try (final CRAMFileReader reader =
                new CRAMFileReader(new FileInputStream(cramFile), (File) null, source, ValidationStringency.SILENT)) {
            return drainIterator(reader.getIterator());
        }
    }

    /**
     * Read a SAM file from the 3.0/passed directory and return its records.
     */
    protected List<SAMRecord> readSam(final String basename) throws IOException {
        return readSamFile(SPEC_30_DIR.resolve(basename + ".sam").toFile());
    }

    /**
     * Read a SAM file and return its records.
     */
    protected List<SAMRecord> readSamFile(final File samFile) throws IOException {
        try (final SamReader reader = SamReaderFactory.makeDefault()
                .referenceSequence(REFERENCE)
                .validationStringency(ValidationStringency.SILENT)
                .open(samFile)) {
            return drainIterator(reader.iterator());
        }
    }

    // Tags that htsjdk auto-generates during CRAM decode and may not be present in source SAM
    private static final String[] CRAM_AUTO_TAGS = {"MD", "NM"};

    /**
     * Decode a CRAM from 3.0/passed and compare record-by-record against the paired SAM.
     * Strips CRAM auto-generated tags (MD, NM) from the decoded records when the SAM record
     * lacks them, since htsjdk regenerates these on decode even if the SAM doesn't have them.
     */
    protected void assertCramMatchesSam(final String basename) throws IOException {
        final List<SAMRecord> cramRecords = decodeCram(basename);
        final List<SAMRecord> samRecords = readSam(basename);
        assertRecordsMatch(cramRecords, samRecords, basename);
    }

    /**
     * Compare two lists of records, handling known CRAM/SAM differences:
     * - Strips auto-generated MD/NM tags from CRAM records when the SAM doesn't have them
     * - Uses field-by-field comparison to tolerate CRAM's loss of signed/unsigned distinction
     *   in B-array tags (CRAM stores all integer arrays as signed)
     */
    protected void assertRecordsMatch(
            final List<SAMRecord> actual, final List<SAMRecord> expected, final String label) {
        Assert.assertEquals(actual.size(), expected.size(), label + ": record count mismatch");
        for (int i = 0; i < expected.size(); i++) {
            final SAMRecord act = actual.get(i);
            final SAMRecord exp = expected.get(i);

            // Strip auto-generated tags from the CRAM record if the SAM record doesn't have them
            for (final String tag : CRAM_AUTO_TAGS) {
                if (act.getAttribute(tag) != null && exp.getAttribute(tag) == null) {
                    act.setAttribute(tag, null);
                }
            }

            assertRecordFieldsEqual(act, exp, label + ": record " + i);
        }
    }

    /**
     * Compare two SAMRecords field-by-field, tolerating CRAM's loss of the signed/unsigned
     * distinction in B-array tags. CRAM always decodes integer array tags as signed (B:c, B:s, B:i)
     * even if the original SAM used unsigned types (B:C, B:S, B:I), since the underlying byte
     * values are identical.
     */
    private void assertRecordFieldsEqual(final SAMRecord actual, final SAMRecord expected, final String label) {
        // Try direct equality first -- handles the common case efficiently
        if (actual.equals(expected)) {
            return;
        }

        // If direct equality fails, compare all non-attribute fields
        Assert.assertEquals(actual.getReadName(), expected.getReadName(), label + " readName");
        Assert.assertEquals(actual.getFlags(), expected.getFlags(), label + " flags");
        Assert.assertEquals(actual.getReferenceName(), expected.getReferenceName(), label + " referenceName");
        Assert.assertEquals(actual.getAlignmentStart(), expected.getAlignmentStart(), label + " alignmentStart");
        Assert.assertEquals(actual.getMappingQuality(), expected.getMappingQuality(), label + " mappingQuality");
        Assert.assertEquals(actual.getCigarString(), expected.getCigarString(), label + " cigar");
        Assert.assertEquals(actual.getMateReferenceName(), expected.getMateReferenceName(), label + " mateRef");
        Assert.assertEquals(actual.getMateAlignmentStart(), expected.getMateAlignmentStart(), label + " mateStart");
        Assert.assertEquals(actual.getInferredInsertSize(), expected.getInferredInsertSize(), label + " tlen");
        Assert.assertEquals(actual.getReadBases(), expected.getReadBases(), label + " bases");
        Assert.assertEquals(actual.getBaseQualities(), expected.getBaseQualities(), label + " qualities");

        // Compare attributes with deep array equality (tolerates signed/unsigned type mismatch)
        final List<SAMRecord.SAMTagAndValue> actAttrs = actual.getAttributes();
        final List<SAMRecord.SAMTagAndValue> expAttrs = expected.getAttributes();
        Assert.assertEquals(
                actAttrs.size(),
                expAttrs.size(),
                label + " attribute count (actual tags: " + tagNames(actAttrs) + ", expected: " + tagNames(expAttrs)
                        + ")");

        for (int j = 0; j < expAttrs.size(); j++) {
            final SAMRecord.SAMTagAndValue a = actAttrs.get(j);
            final SAMRecord.SAMTagAndValue e = expAttrs.get(j);
            Assert.assertEquals(a.tag, e.tag, label + " attr " + j + " tag name");
            assertTagValuesEqual(a.value, e.value, label + " attr " + a.tag);
        }
    }

    private static void assertTagValuesEqual(final Object actual, final Object expected, final String label) {
        if (actual instanceof byte[] && expected instanceof byte[]) {
            Assert.assertEquals((byte[]) actual, (byte[]) expected, label);
        } else if (actual instanceof short[] && expected instanceof short[]) {
            Assert.assertEquals((short[]) actual, (short[]) expected, label);
        } else if (actual instanceof int[] && expected instanceof int[]) {
            Assert.assertEquals((int[]) actual, (int[]) expected, label);
        } else if (actual instanceof float[] && expected instanceof float[]) {
            Assert.assertTrue(Arrays.equals((float[]) actual, (float[]) expected), label);
        } else {
            Assert.assertEquals(actual, expected, label);
        }
    }

    private static String tagNames(final List<SAMRecord.SAMTagAndValue> attrs) {
        final StringBuilder sb = new StringBuilder();
        for (final SAMRecord.SAMTagAndValue a : attrs) {
            if (sb.length() > 0) sb.append(",");
            sb.append(a.tag);
        }
        return sb.toString();
    }

    /**
     * Round-trip: read SAM → write CRAM → read back → compare against original SAM.
     */
    protected void assertRoundTrip(final String basename) throws IOException {
        final File samFile = SPEC_30_DIR.resolve(basename + ".sam").toFile();
        final List<SAMRecord> originalRecords = readSamFile(samFile);
        if (originalRecords.isEmpty()) {
            return; // nothing to round-trip
        }

        final ReferenceSource source = new ReferenceSource(REFERENCE);
        final SAMFileHeader header;
        try (final SamReader reader =
                SamReaderFactory.makeDefault().referenceSequence(REFERENCE).open(samFile)) {
            header = reader.getFileHeader();
        }

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (final CRAMFileWriter writer = new CRAMFileWriter(baos, source, header, basename + ".cram")) {
            for (final SAMRecord record : originalRecords) {
                writer.addAlignment(record);
            }
        }

        try (final CRAMFileReader reader = new CRAMFileReader(
                new ByteArrayInputStream(baos.toByteArray()), (File) null, source, ValidationStringency.SILENT)) {
            final List<SAMRecord> roundTripped = drainIterator(reader.getIterator());
            assertRecordsMatch(roundTripped, originalRecords, basename + " round-trip");
        }
    }

    private static List<SAMRecord> drainIterator(final CloseableIterator<SAMRecord> iterator) {
        final List<SAMRecord> records = new ArrayList<>();
        while (iterator.hasNext()) {
            records.add(iterator.next());
        }
        iterator.close();
        return records;
    }
}
