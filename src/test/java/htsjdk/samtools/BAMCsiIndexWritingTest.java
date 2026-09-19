package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.utils.SamtoolsTestUtils;
import htsjdk.utils.TabixTestUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

/** Writing a CSI index beside a BAM, asked for outright or through {@link BamIndexType#AUTO}. */
public class BAMCsiIndexWritingTest extends HtsjdkTest {
    private static final int BAI_LIMIT = 1 << 29;
    private static final int LONG_SEQUENCE = 600_000_000;
    private static final int MAPPED_PER_CONTIG = 300;
    private static final int PLACED_UNMAPPED = 7;
    private static final int NO_COORDINATE = 5;

    private final List<Path> directories = new ArrayList<>();

    @AfterClass
    public void deleteDirectories() {
        directories.forEach(IOUtil::recursiveDelete);
    }

    /** Reads on the first three contigs spread over {@code span} bases, some placed but unmapped, some unplaced. */
    private static SAMRecordSetBuilder records(final int sequenceLength, final int span) {
        final SAMRecordSetBuilder builder =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, sequenceLength);
        for (int contig = 0; contig < 3; contig++) {
            for (int i = 0; i < MAPPED_PER_CONTIG; i++) {
                final int start = 1 + (int) ((long) i * (span - 1_000) / MAPPED_PER_CONTIG);
                builder.addFrag("mapped" + contig + "_" + i, contig, start, false);
            }
        }
        for (int i = 0; i < PLACED_UNMAPPED; i++) {
            builder.addFrag("placed" + i, 1, 5_000 + 40_000 * i, false, true, null, null, -1);
        }
        for (int i = 0; i < NO_COORDINATE; i++) {
            builder.addUnmappedFragment("unplaced" + i);
        }
        return builder;
    }

    /** Writes the records to {@code reads.bam} in a fresh directory, with whatever index the factory is set for. */
    private Path writeBam(final SAMFileWriterFactory factory, final SAMRecordSetBuilder records) throws IOException {
        final Path directory = Files.createTempDirectory("BAMCsiIndexWritingTest");
        directories.add(directory);
        final Path bam = directory.resolve("reads.bam");
        try (SAMFileWriter writer =
                factory.setCreateIndex(true).setCreateMd5File(false).makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        return bam;
    }

    private static SAMFileWriterFactory factory(final BamIndexType indexType) {
        return new SAMFileWriterFactory().setBamIndexType(indexType);
    }

    private static List<String> fileNames(final Path directory) throws IOException {
        try (Stream<Path> files = Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    private static BinningIndex.CsiContents readCsi(final Path csi) throws IOException {
        try (InputStream in = new BlockCompressedInputStream(Files.newInputStream(csi))) {
            return BinningIndex.readCsi(new BinaryCodec(in));
        }
    }

    private static List<String> query(final Path bam, final int contig, final int start, final int end)
            throws IOException {
        final List<String> names = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            final String contigName = reader.getFileHeader().getSequence(contig).getSequenceName();
            try (CloseableIterator<SAMRecord> records = reader.queryOverlapping(contigName, start, end)) {
                records.forEachRemaining(record -> names.add(record.getReadName()));
            }
        }
        return names;
    }

    @Test
    public void testCsiIsNamedAsSamtoolsNamesIt() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        Assert.assertEquals(fileNames(bam.getParent()), List.of("reads.bam", "reads.bam.csi"));
    }

    @Test
    public void testBaiIsStillTheDefault() throws IOException {
        final Path bam = writeBam(new SAMFileWriterFactory(), records(1_000_000, 1_000_000));
        Assert.assertEquals(fileNames(bam.getParent()), List.of("reads.bai", "reads.bam"));
    }

    @Test
    public void testCsiIsBgzfCompressedWithNothingInItsAuxBlock() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        final Path csi = bam.resolveSibling("reads.bam.csi");
        Assert.assertTrue(IOUtil.isBlockCompressed(csi), "not BGZF");
        Assert.assertEquals(readCsi(csi).aux().length, 0);
    }

    @Test
    public void testCsiSchemeIsTheShallowestThatReachesTheLongestSequence() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        final BinningIndex index = readCsi(bam.resolveSibling("reads.bam.csi")).index();
        Assert.assertEquals(index.getMinShift(), 14);
        Assert.assertEquals(index.getDepth(), 2); // 2^(14 + 3 * 2) is the first span to reach 1 Mbp
    }

    @Test
    public void testCsiMinShiftIsHonoured() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI).setCsiMinShift(12), records(1_000_000, 1_000_000));
        Assert.assertEquals(readCsi(bam.resolveSibling("reads.bam.csi")).index().getMinShift(), 12);
    }

    @Test
    public void testCsiRecordsMappedUnmappedAndUnplacedCounts() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        final BinningIndex index = readCsi(bam.resolveSibling("reads.bam.csi")).index();

        final ReferenceBins.Metadata secondContig =
                index.getReference(1).getMetadata().orElseThrow();
        Assert.assertEquals(secondContig.mappedCount(), MAPPED_PER_CONTIG);
        Assert.assertEquals(secondContig.unmappedCount(), PLACED_UNMAPPED);
        Assert.assertEquals(index.getReference(0).getMetadata().orElseThrow().unmappedCount(), 0);
        Assert.assertEquals(index.getNoCoordinateCount().orElseThrow(), NO_COORDINATE);
    }

    @Test
    public void testReaderReportsTheCountsStoredInTheCsi() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            final BAMIndexMetaData metaData = reader.indexing().getIndex().getMetaData(1);
            Assert.assertEquals(metaData.getAlignedRecordCount(), MAPPED_PER_CONTIG);
            Assert.assertEquals(metaData.getUnalignedRecordCount(), PLACED_UNMAPPED);
        }
    }

    @Test
    public void testQueriesThroughACsiMatchQueriesThroughABai() throws IOException {
        final SAMRecordSetBuilder records = records(1_000_000, 1_000_000);
        final Path withBai = writeBam(factory(BamIndexType.BAI), records);
        final Path withCsi = writeBam(factory(BamIndexType.CSI), records);
        for (int contig = 0; contig < 3; contig++) {
            for (int start = 1; start < 1_000_000; start += 61_803) {
                final List<String> expected = query(withBai, contig, start, start + 20_000);
                Assert.assertEquals(query(withCsi, contig, start, start + 20_000), expected, contig + ":" + start);
            }
        }
        Assert.assertFalse(query(withCsi, 1, 1, 100_000).isEmpty());
    }

    @Test
    public void testUnplacedReadsAreFoundThroughACsi() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        int unplaced = 0;
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam);
                CloseableIterator<SAMRecord> records = reader.queryUnmapped()) {
            while (records.hasNext()) {
                records.next();
                unplaced++;
            }
        }
        Assert.assertEquals(unplaced, NO_COORDINATE);
    }

    @Test
    public void testCsiFindsAReadBeyondTheBaiLimit() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.CSI), records(LONG_SEQUENCE, LONG_SEQUENCE));
        final List<String> beyond = query(bam, 2, BAI_LIMIT + 1, LONG_SEQUENCE);
        Assert.assertFalse(beyond.isEmpty());
        Assert.assertTrue(beyond.size() < MAPPED_PER_CONTIG, "the query was not narrowed at all");
    }

    @Test
    public void testBaiRejectsAReadBeyondItsLimitAndPointsAtCsi() throws IOException {
        try {
            writeBam(factory(BamIndexType.BAI), records(LONG_SEQUENCE, LONG_SEQUENCE));
            Assert.fail("a read beyond 2^29 was indexed in a BAI");
        } catch (final SAMException e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null) {
                rootCause = rootCause.getCause();
            }
            Assert.assertTrue(rootCause.getMessage().contains("use a CSI index"), rootCause.getMessage());
        }
    }

    @Test
    public void testAutoWritesACsiWhenASequenceIsTooLongForABai() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.AUTO), records(LONG_SEQUENCE, LONG_SEQUENCE));
        Assert.assertEquals(fileNames(bam.getParent()), List.of("reads.bam", "reads.bam.csi"));
        Assert.assertFalse(query(bam, 2, BAI_LIMIT + 1, LONG_SEQUENCE).isEmpty());
    }

    @Test
    public void testAutoWritesABaiWhenEverySequenceFitsOne() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.AUTO), records(BAI_LIMIT, 1_000_000));
        Assert.assertEquals(fileNames(bam.getParent()), List.of("reads.bai", "reads.bam"));
    }

    @Test
    public void testCreateIndexWritesACsiForAnExistingBam() throws IOException {
        final SAMRecordSetBuilder records = records(1_000_000, 1_000_000);
        final Path bam = writeBam(factory(BamIndexType.BAI), records);
        final List<String> expected = query(bam, 1, 200_000, 400_000);
        final Path bai = bam.resolveSibling("reads.bai");
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            BAMIndexer.createIndex(reader, bam.resolveSibling("reads.bam.csi"), null, BamIndexType.CSI);
        }
        Files.delete(bai);

        Assert.assertEquals(query(bam, 1, 200_000, 400_000), expected);
    }

    @Test
    public void testSamtoolsQueriesThroughTheCsi() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools not available on local device");
        }
        final Path bam = writeBam(factory(BamIndexType.CSI), records(LONG_SEQUENCE, LONG_SEQUENCE));
        final String contig;
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            contig = reader.getFileHeader().getSequence(2).getSequenceName();
        }
        final String region = contig + ":" + (BAI_LIMIT + 1) + "-" + LONG_SEQUENCE;

        final String count = SamtoolsTestUtils.executeSamToolsCommand("view -c " + bam.toAbsolutePath() + " " + region)
                .stdout
                .trim();
        Assert.assertEquals(
                Integer.parseInt(count),
                query(bam, 2, BAI_LIMIT + 1, LONG_SEQUENCE).size());
    }

    /**
     * Reads every 50 bases, so that many small bins hold little of the file, with one in fifty long enough to put
     * chunks in the bins above them: what it takes for small bins to be folded into their parents.
     */
    private static SAMRecordSetBuilder recordsDenseEnoughForBinsToBeFolded() {
        final SAMRecordSetBuilder builder =
                new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate, true, LONG_SEQUENCE);
        for (int contig = 0; contig < 2; contig++) {
            for (int i = 0; i < 4_000; i++) {
                final int start = 1 + 50 * i;
                if (i % 50 == 0) {
                    builder.addFrag("long" + contig + "_" + i, contig, start, false, false, "20000M", null, 30);
                } else {
                    builder.addFrag("read" + contig + "_" + i, contig, start, false);
                }
            }
        }
        return builder;
    }

    /**
     * samtools writes a reference's bins in no particular order, so the indexes are compared as read, not as bytes.
     * Both index the finished file: an index written along with a BAM names the end of the file's last record as
     * the end of its block, where one made by reading names the start of the block after.
     */
    @Test
    public void testCsiOfAnExistingBamHasTheContentOfTheOneSamtoolsBuilds() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools not available on local device");
        }
        final Path bam = writeBam(factory(BamIndexType.BAI), recordsDenseEnoughForBinsToBeFolded());
        final Path ours = bam.resolveSibling("reads.bam.csi");
        final Path theirs = bam.resolveSibling("samtools.csi");
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            BAMIndexer.createIndex(reader, ours, null, BamIndexType.CSI);
        }
        final BinningIndex.CsiContents oursRead = readCsi(ours);
        SamtoolsTestUtils.executeSamToolsCommand(
                "index -c -m " + oursRead.index().getMinShift() + " -o " + theirs + " " + bam.toAbsolutePath());

        final BinningIndex.CsiContents theirsRead = readCsi(theirs);
        final int bins = oursRead.index().getReference(0).getBinCount();
        Assert.assertTrue(bins < 4_000 * 50 / (1 << 14), "bins should have been folded: " + bins);
        Assert.assertEquals(oursRead.index(), theirsRead.index());
        Assert.assertEquals(oursRead.aux(), theirsRead.aux());
    }

    @Test
    public void testSamtoolsReadsTheCountsFromTheCsi() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools not available on local device");
        }
        final Path bam = writeBam(factory(BamIndexType.CSI), records(1_000_000, 1_000_000));
        final String contig;
        try (SamReader reader = SamReaderFactory.makeDefault().open(bam)) {
            contig = reader.getFileHeader().getSequence(1).getSequenceName();
        }

        final String idxstats = SamtoolsTestUtils.executeSamToolsCommand("idxstats " + bam.toAbsolutePath()).stdout;
        final String expectedLine = contig + "\t1000000\t" + MAPPED_PER_CONTIG + "\t" + PLACED_UNMAPPED;
        Assert.assertTrue(idxstats.lines().anyMatch(expectedLine::equals), idxstats);
        Assert.assertTrue(idxstats.lines().anyMatch(("*\t0\t0\t" + NO_COORDINATE)::equals), idxstats);
    }

    /**
     * Block-compressed SAM whose header's first sequence has no reads, so that an index numbering sequences as the
     * file meets them would number every one differently from the header; indexed with a CSI by createIndex.
     */
    private Path writeSamGzWithCsi(final SAMRecordSetBuilder records) throws IOException {
        final Path directory = Files.createTempDirectory("BAMCsiIndexWritingTest");
        directories.add(directory);
        final Path samGz = directory.resolve("reads.sam.gz");
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(records.getHeader(), true, samGz, null)) {
            records.iterator().forEachRemaining(writer::addAlignment);
        }
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, directory.resolve("reads.sam.gz.csi"), null, BamIndexType.CSI);
        }
        return samGz;
    }

    private static SAMRecordSetBuilder recordsOnTheSecondAndFourthSequences() {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 2_000; i++) {
            records.addFrag("read" + i, i < 1_000 ? 1 : 3, 1 + 60 * (i % 1_000), false);
        }
        return records;
    }

    @Test
    public void testCsiCreatedForSamTextNamesEverySequenceOfTheHeaderInOrder() throws IOException {
        final SAMRecordSetBuilder records = recordsOnTheSecondAndFourthSequences();
        final Path samGz = writeSamGzWithCsi(records);

        final TabixIndex.Header tabixHeader = TabixIndex.readCsiAux(
                readCsi(samGz.resolveSibling("reads.sam.gz.csi")).aux());
        Assert.assertEquals(tabixHeader.format(), TabixFormat.SAM);
        Assert.assertEquals(
                tabixHeader.sequenceNames(),
                records.getHeader().getSequenceDictionary().getSequences().stream()
                        .map(SAMSequenceRecord::getSequenceName)
                        .toList());
    }

    @Test
    public void testCsiCreatedForABamNamesNoSequences() throws IOException {
        final Path bam = writeBam(factory(BamIndexType.BAI), records(1_000_000, 1_000_000));
        final Path csi = bam.resolveSibling("reads.bam.csi");
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            BAMIndexer.createIndex(reader, csi, null, BamIndexType.CSI);
        }
        Assert.assertEquals(readCsi(csi).aux().length, 0);
    }

    /**
     * samtools asks a CSI by the header's numbering and tabix by the names in it, so an index that served only one
     * of them would give the other some other sequence's reads, or none.
     */
    @Test
    public void testCsiCreatedForSamTextIsReadRightlyBySamtoolsAndByTabix() throws IOException {
        if (!TabixTestUtils.isTabixAvailable() || !SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools and tabix are not both available");
        }
        final SAMRecordSetBuilder records = recordsOnTheSecondAndFourthSequences();
        final Path samGz = writeSamGzWithCsi(records);

        for (final int contig : new int[] {0, 1, 2, 3}) {
            final String name = records.getHeader().getSequence(contig).getSequenceName();
            final String region = name + ":20000-40000";
            final long expected = records.getRecords().stream()
                    .filter(record -> record.getReferenceIndex() == contig
                            && record.getAlignmentStart() <= 40_000
                            && record.getAlignmentEnd() >= 20_000)
                    .count();
            Assert.assertEquals(expected > 0, contig == 1 || contig == 3);

            final String samtoolsCount = SamtoolsTestUtils.executeSamToolsCommand("view -c " + samGz + " " + region)
                    .stdout
                    .trim();
            Assert.assertEquals(samtoolsCount, Long.toString(expected), "samtools, " + region);
            Assert.assertEquals(
                    TabixTestUtils.executeTabix(samGz.toString(), region).size(), expected, "tabix, " + region);
        }
    }
}
