package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.*;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import htsjdk.samtools.seekablestream.ByteArraySeekableStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.SequenceUtil;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Tests that CRAI indexes produced by htsjdk enable correct region-based querying.
 *
 * Creates a multi-contig dataset programmatically, writes CRAM + CRAI, then verifies that
 * queries return exactly the expected records for various region, unmapped, and boundary scenarios.
 */
@Test(singleThreaded = true)
public class CRAIQueryTest extends HtsjdkTest {

    private static final int CONTIG_LENGTH = 10_000;
    private static final String CONTIG_1 = "chr1";
    private static final String CONTIG_2 = "chr2";
    private static final String CONTIG_3 = "chr3";

    // Use a small reads-per-slice to create multiple containers, exercising cross-container queries
    private static final int READS_PER_SLICE = 50;

    private byte[] cramBytes;
    private byte[] craiBytes;
    private ReferenceSource referenceSource;
    private SAMFileHeader header;
    private List<SAMRecord> allRecords;

    @BeforeClass
    public void setup() throws IOException {
        // Build an in-memory reference with three contigs
        final byte[] refBases = buildReferenceBases(CONTIG_LENGTH);
        final InMemoryReferenceSequenceFile rsf = new InMemoryReferenceSequenceFile();
        rsf.add(CONTIG_1, refBases);
        rsf.add(CONTIG_2, refBases);
        rsf.add(CONTIG_3, refBases);
        referenceSource = new ReferenceSource(rsf);

        // Build a header with three contigs
        header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(CONTIG_1, CONTIG_LENGTH));
        header.addSequence(new SAMSequenceRecord(CONTIG_2, CONTIG_LENGTH));
        header.addSequence(new SAMSequenceRecord(CONTIG_3, CONTIG_LENGTH));
        header.addReadGroup(new SAMReadGroupRecord("rg1"));
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);

        // Build records spread across all contigs with known positions
        allRecords = buildRecords(header, refBases);

        // Write CRAM + CRAI
        final ByteArrayOutputStream cramBaos = new ByteArrayOutputStream();
        final ByteArrayOutputStream craiBaos = new ByteArrayOutputStream();
        final CRAMEncodingStrategy strategy = new CRAMEncodingStrategy()
                .setMinimumSingleReferenceSliceSize(READS_PER_SLICE)
                .setReadsPerSlice(READS_PER_SLICE);

        try (final CRAMFileWriter writer =
                new CRAMFileWriter(strategy, cramBaos, craiBaos, true, referenceSource, header, "test.cram")) {
            for (final SAMRecord record : allRecords) {
                writer.addAlignment(record);
            }
        }

        cramBytes = cramBaos.toByteArray();
        craiBytes = craiBaos.toByteArray();
    }

    // ---- Query correctness tests ----

    @Test
    public void testQuerySingleContigFullRange() throws IOException {
        // Query the entire chr1 range and verify we get exactly the chr1 records
        final List<SAMRecord> expected = recordsOnContig(CONTIG_1);
        final List<SAMRecord> actual = queryContained(CONTIG_1, 1, CONTIG_LENGTH);
        assertReadNamesEqual(actual, expected, "chr1 full range");
    }

    @Test
    public void testQuerySecondContig() throws IOException {
        final List<SAMRecord> expected = recordsOnContig(CONTIG_2);
        final List<SAMRecord> actual = queryContained(CONTIG_2, 1, CONTIG_LENGTH);
        assertReadNamesEqual(actual, expected, "chr2 full range");
    }

    @Test
    public void testQueryThirdContig() throws IOException {
        final List<SAMRecord> expected = recordsOnContig(CONTIG_3);
        final List<SAMRecord> actual = queryContained(CONTIG_3, 1, CONTIG_LENGTH);
        assertReadNamesEqual(actual, expected, "chr3 full range");
    }

    @Test
    public void testQueryNarrowRegion() throws IOException {
        // Query a narrow region that should contain a subset of records
        final int queryStart = 500;
        final int queryEnd = 600;
        final List<SAMRecord> expected = recordsOnContig(CONTIG_1).stream()
                .filter(r -> r.getAlignmentStart() >= queryStart && r.getAlignmentEnd() <= queryEnd)
                .collect(Collectors.toList());
        final List<SAMRecord> actual = queryContained(CONTIG_1, queryStart, queryEnd);
        assertReadNamesEqual(actual, expected, "chr1 narrow region");
    }

    @Test
    public void testQueryOverlapping() throws IOException {
        // Overlapping query should return records that overlap the region, even if not fully contained
        final int queryStart = 500;
        final int queryEnd = 510;
        final List<SAMRecord> actual = queryOverlapping(CONTIG_1, queryStart, queryEnd);

        // Every returned record must overlap the query region
        for (final SAMRecord rec : actual) {
            Assert.assertEquals(rec.getReferenceName(), CONTIG_1);
            Assert.assertTrue(
                    rec.getAlignmentStart() <= queryEnd && rec.getAlignmentEnd() >= queryStart,
                    "Record " + rec.getReadName() + " at " + rec.getAlignmentStart() + "-" + rec.getAlignmentEnd()
                            + " does not overlap query " + queryStart + "-" + queryEnd);
        }

        // Verify we got at least some records (our data is dense enough)
        Assert.assertFalse(actual.isEmpty(), "Overlapping query should return at least one record");
    }

    @Test
    public void testQueryEmptyRegion() throws IOException {
        // Query a region beyond all reads (reads end well before CONTIG_LENGTH)
        final List<SAMRecord> actual = queryOverlapping(CONTIG_1, 9900, 9999);
        Assert.assertTrue(actual.isEmpty(), "Query in empty region should return no records");
    }

    @Test
    public void testQueryUnmappedReads() throws IOException {
        final List<SAMRecord> expectedUnmapped =
                allRecords.stream().filter(SAMRecord::getReadUnmappedFlag).collect(Collectors.toList());

        try (final CRAMFileReader reader = openReader()) {
            try (final CloseableIterator<SAMRecord> iterator = reader.queryUnmapped()) {
                final List<SAMRecord> actual = new ArrayList<>();
                while (iterator.hasNext()) {
                    actual.add(iterator.next());
                }
                assertReadNamesEqual(actual, expectedUnmapped, "unmapped reads");
            }
        }
    }

    @Test
    public void testQueryMultipleIntervals() throws IOException {
        // Query two disjoint regions across different contigs using QueryInterval
        final QueryInterval[] intervals = new QueryInterval[] {
            new QueryInterval(0, 100, 200), // chr1:100-200
            new QueryInterval(1, 100, 200), // chr2:100-200
        };

        try (final CRAMFileReader reader = openReader()) {
            try (final CloseableIterator<SAMRecord> iterator = reader.query(intervals, false)) {
                final List<SAMRecord> actual = new ArrayList<>();
                while (iterator.hasNext()) {
                    actual.add(iterator.next());
                }

                // Every returned record should overlap one of the queried intervals
                for (final SAMRecord rec : actual) {
                    final boolean overlapsFirst = rec.getReferenceIndex() == 0
                            && rec.getAlignmentStart() <= 200
                            && rec.getAlignmentEnd() >= 100;
                    final boolean overlapsSecond = rec.getReferenceIndex() == 1
                            && rec.getAlignmentStart() <= 200
                            && rec.getAlignmentEnd() >= 100;
                    Assert.assertTrue(
                            overlapsFirst || overlapsSecond,
                            "Record " + rec.getReadName() + " does not overlap either query interval");
                }
            }
        }
    }

    @Test
    public void testQueryAlignmentStart() throws IOException {
        // Find a record on chr1 and query by its exact alignment start
        final SAMRecord target = recordsOnContig(CONTIG_1).get(0);

        try (final CRAMFileReader reader = openReader()) {
            try (final CloseableIterator<SAMRecord> iterator =
                    reader.queryAlignmentStart(CONTIG_1, target.getAlignmentStart())) {
                Assert.assertTrue(
                        iterator.hasNext(),
                        "queryAlignmentStart should find records at position " + target.getAlignmentStart());
                final SAMRecord found = iterator.next();
                Assert.assertEquals(found.getAlignmentStart(), target.getAlignmentStart());
            }
        }
    }

    @Test
    public void testQueryContainedVsOverlapping() throws IOException {
        // Contained query should be a subset of overlapping query for the same region
        final int queryStart = 200;
        final int queryEnd = 300;
        final List<SAMRecord> contained = queryContained(CONTIG_1, queryStart, queryEnd);
        final List<SAMRecord> overlapping = queryOverlapping(CONTIG_1, queryStart, queryEnd);

        final Set<String> containedNames =
                contained.stream().map(SAMRecord::getReadName).collect(Collectors.toSet());
        final Set<String> overlappingNames =
                overlapping.stream().map(SAMRecord::getReadName).collect(Collectors.toSet());

        Assert.assertTrue(
                overlappingNames.containsAll(containedNames),
                "Contained results should be a subset of overlapping results");
    }

    @Test
    public void testTotalRecordCount() throws IOException {
        // Reading all records through index-based iteration should match our expected count
        try (final CRAMFileReader reader = openReader()) {
            final SAMFileSpan allContainers = reader.getFilePointerSpanningReads();
            try (final CloseableIterator<SAMRecord> iterator = reader.getIterator(allContainers)) {
                int count = 0;
                while (iterator.hasNext()) {
                    iterator.next();
                    count++;
                }
                Assert.assertEquals(
                        count, allRecords.size(), "Total record count via index span should match input record count");
            }
        }
    }

    // ---- Helpers ----

    private CRAMFileReader openReader() throws IOException {
        return new CRAMFileReader(
                new ByteArraySeekableStream(cramBytes),
                new ByteArraySeekableStream(craiBytes),
                referenceSource,
                ValidationStringency.STRICT);
    }

    private List<SAMRecord> queryContained(final String contig, final int start, final int end) throws IOException {
        return queryWithContainedFlag(contig, start, end, true);
    }

    private List<SAMRecord> queryOverlapping(final String contig, final int start, final int end) throws IOException {
        return queryWithContainedFlag(contig, start, end, false);
    }

    private List<SAMRecord> queryWithContainedFlag(
            final String contig, final int start, final int end, final boolean contained) throws IOException {
        try (final CRAMFileReader reader = openReader()) {
            final int seqIdx = header.getSequenceIndex(contig);
            final QueryInterval[] intervals = new QueryInterval[] {new QueryInterval(seqIdx, start, end)};
            try (final CloseableIterator<SAMRecord> iterator = reader.query(intervals, contained)) {
                final List<SAMRecord> results = new ArrayList<>();
                while (iterator.hasNext()) {
                    results.add(iterator.next());
                }
                return results;
            }
        }
    }

    private List<SAMRecord> recordsOnContig(final String contig) {
        return allRecords.stream()
                .filter(r -> !r.getReadUnmappedFlag() && contig.equals(r.getReferenceName()))
                .collect(Collectors.toList());
    }

    private void assertReadNamesEqual(
            final List<SAMRecord> actual, final List<SAMRecord> expected, final String label) {
        final List<String> actualNames =
                actual.stream().map(SAMRecord::getReadName).collect(Collectors.toList());
        final List<String> expectedNames =
                expected.stream().map(SAMRecord::getReadName).collect(Collectors.toList());
        Assert.assertEquals(actualNames, expectedNames, label + ": read name lists differ");
    }

    /**
     * Builds a set of records across three contigs with known positions, plus some unmapped reads.
     * Records are spaced 10bp apart on each contig so positions are predictable.
     */
    private static List<SAMRecord> buildRecords(final SAMFileHeader header, final byte[] refBases) {
        final List<SAMRecord> records = new ArrayList<>();
        final int readLength = 50;
        final int spacing = 10;
        final int readsPerContig = 100;

        for (int contigIdx = 0; contigIdx < 3; contigIdx++) {
            for (int i = 0; i < readsPerContig; i++) {
                final int alignmentStart = 1 + i * spacing;
                final String name = String.format("read_%d_%04d", contigIdx, i);
                final SAMRecord rec = new SAMRecord(header);
                rec.setReadName(name);
                rec.setReferenceIndex(contigIdx);
                rec.setAlignmentStart(alignmentStart);
                rec.setCigarString(readLength + "M");
                rec.setReadUnmappedFlag(false);

                // Build read bases from reference
                final byte[] bases = new byte[readLength];
                System.arraycopy(refBases, alignmentStart - 1, bases, 0, readLength);
                rec.setReadBases(bases);

                final byte[] quals = new byte[readLength];
                Arrays.fill(quals, (byte) 30);
                rec.setBaseQualities(quals);
                rec.setAttribute("RG", "rg1");
                SequenceUtil.calculateMdAndNmTags(rec, refBases, true, true);
                records.add(rec);
            }
        }

        // Add unmapped reads
        for (int i = 0; i < 10; i++) {
            final SAMRecord rec = new SAMRecord(header);
            rec.setReadName(String.format("unmapped_%04d", i));
            rec.setReadUnmappedFlag(true);
            rec.setReferenceIndex(SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX);
            rec.setAlignmentStart(SAMRecord.NO_ALIGNMENT_START);
            rec.setReadBases("ACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTACGTAC".getBytes());
            final byte[] quals = new byte[50];
            Arrays.fill(quals, (byte) 30);
            rec.setBaseQualities(quals);
            rec.setAttribute("RG", "rg1");
            records.add(rec);
        }

        return records;
    }

    private static byte[] buildReferenceBases(final int length) {
        final byte[] bases = new byte[length];
        final byte[] pattern = "ACGTACGTAC".getBytes();
        for (int i = 0; i < length; i++) {
            bases[i] = pattern[i % pattern.length];
        }
        return bases;
    }
}
