package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Queries of block-compressed SAM through a BAI or CSI index. What a query should return is settled by asking the
 * same of a BAM that holds the same records.
 */
public class BlockCompressedSamQueryTest extends HtsjdkTest {
    private static final int PLACED = 8_000;

    private SAMFileHeader header;
    private Path bam;
    private Path samGzWithBai;
    private Path samGzWithCsi;
    private Path samGzWithoutIndex;

    @BeforeClass
    public void writeFiles() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        final Random random = new Random(11);
        for (int i = 0; i < PLACED; i++) {
            // Reference 1 is left without reads, and one read in fifty is long enough to reach into other bins.
            final int reference = i < PLACED / 2 ? 0 : 2;
            final int start = 1 + 50 * (i % (PLACED / 2)) + random.nextInt(40);
            if (i % 50 == 0) {
                records.addFrag("long" + i, reference, start, false, false, "20000M", null, 30);
            } else if (i % 7 == 0) {
                records.addPair("pair" + i, reference, start, start + 300);
            } else {
                records.addFrag("read" + i, reference, start, i % 2 == 0);
            }
        }
        for (int i = 0; i < 40; i++) {
            records.addUnmappedFragment("unplaced" + i);
        }
        header = records.getHeader();

        final Path directory = Files.createTempDirectory("samGzQuery");
        IOUtil.deleteOnExit(directory);
        bam = directory.resolve("records.bam");
        samGzWithBai = directory.resolve("bai.sam.gz");
        samGzWithCsi = directory.resolve("csi.sam.gz");
        samGzWithoutIndex = directory.resolve("bare.sam.gz");
        final List<SAMRecord> sorted = new ArrayList<>();
        records.iterator().forEachRemaining(sorted::add);
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, bam)) {
            sorted.forEach(writer::addAlignment);
        }
        for (final Path samGz : List.of(samGzWithBai, samGzWithCsi, samGzWithoutIndex)) {
            try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(header, true, samGz, null)) {
                sorted.forEach(writer::addAlignment);
            }
        }
        index(samGzWithBai, samGzWithBai.resolveSibling("bai.sam.gz.bai"), BamIndexType.BAI);
        index(samGzWithCsi, samGzWithCsi.resolveSibling("csi.sam.gz.csi"), BamIndexType.CSI);
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            files.forEach(IOUtil::deleteOnExit);
        }
    }

    private static void index(final Path samGz, final Path index, final BamIndexType type) throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, index, null, type);
        }
    }

    private static List<String> drain(final SAMRecordIterator records) {
        try (records) {
            return records.stream().map(SAMRecord::getSAMString).collect(Collectors.toList());
        }
    }

    /** Runs the same randomly placed queries, small and large, against the SAM and the BAM. */
    private void assertQueriesAgreeWithBam(final Path samGz, final boolean contained) throws IOException {
        final Random random = new Random(5);
        int recordsSeen = 0;
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            for (int i = 0; i < 150; i++) {
                final String sequence = header.getSequence(random.nextInt(3)).getSequenceName();
                final int start = 1 + random.nextInt(220_000);
                final int end = start + random.nextInt(i % 5 == 0 ? 60_000 : 700);
                final List<String> found = drain(sam.query(sequence, start, end, contained));
                Assert.assertEquals(
                        found,
                        drain(expected.query(sequence, start, end, contained)),
                        sequence + ":" + start + "-" + end);
                recordsSeen += found.size();
            }
        }
        Assert.assertTrue(recordsSeen > 1_000, "the queries should have found plenty of records, not " + recordsSeen);
    }

    @Test
    public void testOverlapQueriesThroughABai() throws IOException {
        assertQueriesAgreeWithBam(samGzWithBai, false);
    }

    @Test
    public void testContainedQueriesThroughABai() throws IOException {
        assertQueriesAgreeWithBam(samGzWithBai, true);
    }

    @Test
    public void testOverlapQueriesThroughACsi() throws IOException {
        assertQueriesAgreeWithBam(samGzWithCsi, false);
    }

    @Test
    public void testContainedQueriesThroughACsi() throws IOException {
        assertQueriesAgreeWithBam(samGzWithCsi, true);
    }

    @Test
    public void testQueryOfSeveralIntervalsAtOnce() throws IOException {
        final QueryInterval[] intervals = QueryInterval.optimizeIntervals(new QueryInterval[] {
            new QueryInterval(0, 1_000, 3_000),
            new QueryInterval(0, 150_000, 151_000),
            new QueryInterval(1, 1, 100_000),
            new QueryInterval(2, 40_000, 40_500),
        });
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            final List<String> found = drain(sam.query(intervals, false));
            Assert.assertEquals(found, drain(expected.query(intervals, false)));
            Assert.assertTrue(found.size() > 50);
        }
    }

    @Test
    public void testQueryOfAReferenceWithoutReadsFindsNothing() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            Assert.assertEquals(drain(sam.queryOverlapping("chr2", 1, 0)), List.of());
        }
    }

    @Test
    public void testQueryAlignmentStart() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithCsi);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            int found = 0;
            for (final SAMRecord record : expected) {
                if (found == 25 || record.getReadUnmappedFlag()) break;
                if (record.getAlignmentStart() % 3 != 0) continue;
                found++;
                final String sequence = record.getReferenceName();
                Assert.assertEquals(
                        drain(sam.queryAlignmentStart(sequence, record.getAlignmentStart())),
                        queryAlignmentStartInBam(sequence, record.getAlignmentStart()));
            }
            Assert.assertEquals(found, 25);
        }
    }

    private List<String> queryAlignmentStartInBam(final String sequence, final int start) throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            return drain(reader.queryAlignmentStart(sequence, start));
        }
    }

    @Test
    public void testQueryUnmappedThroughABai() throws IOException {
        assertUnmappedAgreeWithBam(samGzWithBai);
    }

    @Test
    public void testQueryUnmappedThroughACsi() throws IOException {
        assertUnmappedAgreeWithBam(samGzWithCsi);
    }

    private void assertUnmappedAgreeWithBam(final Path samGz) throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            final List<String> found = drain(sam.queryUnmapped());
            Assert.assertEquals(found, drain(expected.queryUnmapped()));
            Assert.assertEquals(found.size(), 40);
        }
    }

    @Test
    public void testQueryMate() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader other = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            int pairs = 0;
            for (final SAMRecord record : other) {
                if (pairs == 10) break;
                if (!record.getReadPairedFlag() || !record.getFirstOfPairFlag()) continue;
                pairs++;
                final SAMRecord mate = sam.queryMate(record);
                Assert.assertEquals(mate.getReadName(), record.getReadName());
                Assert.assertTrue(mate.getSecondOfPairFlag());
            }
            Assert.assertEquals(pairs, 10);
        }
    }

    @Test
    public void testOneReaderAnswersQueryAfterQuery() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai)) {
            final List<String> first = drain(sam.queryOverlapping("chr1", 5_000, 6_000));
            drain(sam.queryOverlapping("chr3", 90_000, 95_000));
            Assert.assertEquals(drain(sam.queryOverlapping("chr1", 5_000, 6_000)), first);
            Assert.assertFalse(first.isEmpty());
            Assert.assertEquals(drain(sam.iterator()).size(), drainBam().size());
        }
    }

    private List<String> drainBam() throws IOException {
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            return drain(reader.iterator());
        }
    }

    @Test(expectedExceptions = IllegalStateException.class)
    public void testASecondQueryWhileOneIsOpenIsRefused() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithBai);
                SAMRecordIterator open = sam.queryOverlapping("chr1", 5_000, 6_000)) {
            sam.queryOverlapping("chr1", 7_000, 8_000);
        }
    }

    @Test
    public void testIndexBesideTheFileIsFound() throws IOException {
        try (SamReader withBai = SamReaderFactory.makeDefault().open(samGzWithBai);
                SamReader withCsi = SamReaderFactory.makeDefault().open(samGzWithCsi);
                SamReader without = SamReaderFactory.makeDefault().open(samGzWithoutIndex)) {
            Assert.assertTrue(withBai.hasIndex());
            Assert.assertTrue(withCsi.hasIndex());
            Assert.assertFalse(without.hasIndex());
            Assert.assertTrue(withBai.indexing().hasBrowseableIndex());
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testQueryWithoutAnIndexIsUnsupported() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGzWithoutIndex)) {
            sam.queryOverlapping("chr1", 5_000, 6_000);
        }
    }

    @Test
    public void testIndexNamedExplicitly() throws IOException {
        final Path elsewhere = Files.createTempFile("elsewhere.", ".csi");
        IOUtil.deleteOnExit(elsewhere);
        Files.copy(
                samGzWithCsi.resolveSibling("csi.sam.gz.csi"),
                elsewhere,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGzWithoutIndex).index(elsewhere));
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr3", 10_000, 12_000)),
                    drain(expected.queryOverlapping("chr3", 10_000, 12_000)));
        }
    }

    @Test
    public void testIndexGivenAsAStream() throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGzWithoutIndex)
                                .index(new SeekablePathStream(samGzWithBai.resolveSibling("bai.sam.gz.bai"))));
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr1", 10_000, 12_000)),
                    drain(expected.queryOverlapping("chr1", 10_000, 12_000)));
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testIndexCoveringADifferentNumberOfReferencesIsRefused() throws IOException {
        final SAMRecordSetBuilder other = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        other.getHeader()
                .setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("only", 1_000_000))));
        other.addFrag("read", 0, 100, false);
        final Path otherBam = Files.createTempFile("other.", ".bam");
        IOUtil.deleteOnExit(otherBam);
        IOUtil.deleteOnExit(
                otherBam.resolveSibling(otherBam.getFileName().toString().replaceAll("\\.bam$", ".bai")));
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(other.getHeader(), true, otherBam)) {
            other.getRecords().forEach(writer::addAlignment);
        }
        final Path otherBai =
                otherBam.resolveSibling(otherBam.getFileName().toString().replaceAll("\\.bam$", ".bai"));

        try (SamReader sam = SamReaderFactory.makeDefault()
                .open(SamInputResource.of(samGzWithoutIndex).index(otherBai))) {
            sam.queryOverlapping("chr1", 5_000, 6_000);
        }
    }

    @Test
    public void testIndexStreamThatIsRefusedIsClosedOnce() throws IOException {
        final int[] closes = {0};
        final SeekablePathStream baiOfAnotherFile = new SeekablePathStream(bam.resolveSibling("records.bai")) {
            @Override
            public void close() throws IOException {
                closes[0]++;
                super.close();
            }
        };
        // The oracle BAM's index fits the file, so cut the reader's header down to make it not fit
        final Path oneSequence = Files.createTempFile("oneSequence.", ".sam.gz");
        IOUtil.deleteOnExit(oneSequence);
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        records.getHeader()
                .setSequenceDictionary(new SAMSequenceDictionary(List.of(new SAMSequenceRecord("only", 1_000_000))));
        records.addFrag("read", 0, 100, false);
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().makeWriter(records.getHeader(), true, oneSequence, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        try (SamReader sam = SamReaderFactory.makeDefault()
                .open(SamInputResource.of(oneSequence).index(baiOfAnotherFile))) {
            Assert.assertThrows(SAMFormatException.class, () -> sam.queryOverlapping("only", 1, 1_000));
        }
        Assert.assertEquals(closes[0], 1);
    }

    @Test
    public void testIndexesBuiltBySamtools() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools is not available");
        }
        final Path bai = Files.createTempFile("samtools.", ".bai");
        final Path csi = Files.createTempFile("samtools.", ".csi");
        IOUtil.deleteOnExit(bai);
        IOUtil.deleteOnExit(csi);
        SamtoolsTestUtils.executeSamToolsCommand("index -b -o " + bai + " " + samGzWithoutIndex);
        SamtoolsTestUtils.executeSamToolsCommand("index -c -o " + csi + " " + samGzWithoutIndex);

        for (final Path index : List.of(bai, csi)) {
            try (SamReader sam = SamReaderFactory.makeDefault()
                            .open(SamInputResource.of(samGzWithoutIndex).index(index));
                    SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
                for (final int start : new int[] {1, 16_380, 100_000, 199_000}) {
                    Assert.assertEquals(
                            drain(sam.queryOverlapping("chr3", start, start + 5_000)),
                            drain(expected.queryOverlapping("chr3", start, start + 5_000)),
                            index + " at " + start);
                }
                Assert.assertEquals(drain(sam.queryUnmapped()), drain(expected.queryUnmapped()), index.toString());
            }
        }
    }
}
