package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.index.tabix.TabixFormat;
import htsjdk.tribble.index.tabix.TabixIndex;
import htsjdk.tribble.index.tabix.TabixIndexType;
import htsjdk.utils.SamtoolsTestUtils;
import htsjdk.utils.TabixTestUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Block-compressed SAM indexed the way tabix indexes it: references numbered as the file meets them, and named. The
 * header's first sequence has no reads here, so every reference has one number in the header and another in the
 * index, and an index asked by header ordinal would answer with some other reference's reads.
 */
public class BlockCompressedSamTabixIndexTest extends HtsjdkTest {
    private List<SAMRecord> sorted;
    private SAMFileHeader header;
    private Path bam;

    @BeforeClass
    public void writeBam() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 3_000; i++) {
            records.addFrag("read" + i, i < 1_500 ? 1 : 3, 1 + 60 * (i % 1_500), i % 2 == 0);
        }
        for (int i = 0; i < 25; i++) {
            records.addUnmappedFragment("unplaced" + i);
        }
        header = records.getHeader();
        sorted = new ArrayList<>();
        records.iterator().forEachRemaining(sorted::add);

        bam = Files.createTempFile("tabixOracle.", ".bam");
        IOUtil.deleteOnExit(bam);
        IOUtil.deleteOnExit(bam.resolveSibling(bam.getFileName().toString().replaceAll("\\.bam$", ".bai")));
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(header, true, bam)) {
            sorted.forEach(writer::addAlignment);
        }
    }

    /** A fresh copy of the records as block-compressed SAM, in a directory of its own so that its index is unambiguous. */
    private Path writeSamGz() throws IOException {
        final Path directory = Files.createTempDirectory("samGzTabix");
        IOUtil.deleteOnExit(directory);
        final Path samGz = directory.resolve("records.sam.gz");
        IOUtil.deleteOnExit(samGz);
        try (SAMFileWriter writer = new SAMFileWriterFactory().makeWriter(header, true, samGz, null)) {
            sorted.forEach(writer::addAlignment);
        }
        return samGz;
    }

    /**
     * Indexes a file as tabix would: a reference per sequence that has reads, in the order the file meets them.
     *
     * @param format what the index is to say the file's format is
     * @param naming what the index is to call each sequence, given its name in the file
     * @param listUnplaced whether to list {@code *} as a sequence, as tabix does for a file with unplaced reads
     */
    private Path indexAsTabixWould(
            final Path samGz,
            final TabixIndexType type,
            final TabixFormat format,
            final UnaryOperator<String> naming,
            final boolean listUnplaced)
            throws IOException {
        final List<String> names = new ArrayList<>();
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, type == TabixIndexType.CSI);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            for (final SAMRecord record : reader) {
                if (record.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                if (!names.contains(record.getReferenceName())) {
                    names.add(record.getReferenceName());
                }
                final Chunk chunk = ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
                builder.add(
                        names.indexOf(record.getReferenceName()),
                        record.getAlignmentStart(),
                        record.getAlignmentEnd(),
                        chunk.getChunkStart(),
                        chunk.getChunkEnd());
            }
        }
        final List<String> listed = names.stream().map(naming).collect(Collectors.toCollection(ArrayList::new));
        if (listUnplaced) {
            listed.add(SAMRecord.NO_ALIGNMENT_REFERENCE_NAME);
        }
        final Path index = samGz.resolveSibling(samGz.getFileName() + type.getExtension());
        IOUtil.deleteOnExit(index);
        new TabixIndex(format, listed, builder.build(listed.size()), type).write(index);
        return index;
    }

    private static List<String> drain(final SAMRecordIterator records) {
        try (records) {
            return records.stream().map(SAMRecord::getSAMString).collect(Collectors.toList());
        }
    }

    /** Queries on each of the header's first four sequences, two of which have reads, and for the unplaced reads. */
    private void assertAnswersAsTheBamDoes(final Path samGz) throws IOException {
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz);
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertTrue(sam.hasIndex(), "the index beside the file should have been found");
            int found = 0;
            for (int reference = 0; reference < 4; reference++) {
                final String sequence = header.getSequence(reference).getSequenceName();
                for (final int start : new int[] {1, 16_000, 45_000, 89_000}) {
                    final List<String> records = drain(sam.queryOverlapping(sequence, start, start + 3_000));
                    Assert.assertEquals(
                            records,
                            drain(expected.queryOverlapping(sequence, start, start + 3_000)),
                            sequence + ":" + start);
                    found += records.size();
                }
            }
            Assert.assertTrue(found > 300, "the queries should have found plenty of records, not " + found);
            Assert.assertEquals(drain(sam.queryUnmapped()), drain(expected.queryUnmapped()));
        }
    }

    @Test
    public void testTbiIsAskedByName() throws IOException {
        final Path samGz = writeSamGz();
        indexAsTabixWould(samGz, TabixIndexType.TBI, TabixFormat.SAM, UnaryOperator.identity(), true);
        assertAnswersAsTheBamDoes(samGz);
    }

    @Test
    public void testCsiWithATabixHeaderIsAskedByName() throws IOException {
        final Path samGz = writeSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, UnaryOperator.identity(), true);
        assertAnswersAsTheBamDoes(samGz);
    }

    @Test
    public void testCsiWithATabixHeaderGivenAsAStream() throws IOException {
        final Path samGz = writeSamGz();
        final Path csi = indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, UnaryOperator.identity(), false);
        final Path elsewhere = Files.createTempFile("elsewhere.", ".index");
        IOUtil.deleteOnExit(elsewhere);
        Files.move(csi, elsewhere, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        try (SamReader sam = SamReaderFactory.makeDefault()
                        .open(SamInputResource.of(samGz)
                                .index(new htsjdk.samtools.seekablestream.SeekablePathStream(elsewhere)));
                SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            Assert.assertEquals(
                    drain(sam.queryOverlapping("chr4", 10_000, 14_000)),
                    drain(expected.queryOverlapping("chr4", 10_000, 14_000)));
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexMadeForAnotherFormatIsRefused() throws IOException {
        final Path samGz = writeSamGz();
        indexAsTabixWould(samGz, TabixIndexType.TBI, TabixFormat.VCF, UnaryOperator.identity(), false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexNamingASequenceTheHeaderLacksIsRefused() throws IOException {
        final Path samGz = writeSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, name -> "other_" + name, false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test
    public void testTbiBuiltByTabix() throws IOException {
        assertIndexBuiltByTabixAnswersAsTheBamDoes();
    }

    @Test
    public void testCsiBuiltByTabix() throws IOException {
        assertIndexBuiltByTabixAnswersAsTheBamDoes("-C");
    }

    private void assertIndexBuiltByTabixAnswersAsTheBamDoes(final String... options) throws IOException {
        if (!TabixTestUtils.isTabixAvailable()) {
            throw new SkipException("tabix is not available");
        }
        final Path samGz = writeSamGz();
        final List<String> arguments = new ArrayList<>(List.of(options));
        arguments.addAll(List.of("-f", "-p", "sam", samGz.toString()));
        TabixTestUtils.executeTabix(arguments.toArray(new String[0]));
        try (java.util.stream.Stream<Path> files = Files.list(samGz.getParent())) {
            files.forEach(IOUtil::deleteOnExit);
        }
        assertAnswersAsTheBamDoes(samGz);
    }

    /** Indexes block-compressed SAM with a CSI of htsjdk's own making, placed beside it. */
    private static Path indexWithCsi(final Path samGz) throws IOException {
        final Path csi = samGz.resolveSibling(samGz.getFileName() + ".csi");
        IOUtil.deleteOnExit(csi);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, csi, null, BamIndexType.CSI);
        }
        return csi;
    }

    @Test
    public void testCsiWrittenForSamTextNamesEverySequenceOfTheHeaderInOrder() throws IOException {
        final Path samGz = writeSamGz();
        try (FileBackedBinningIndex csi = FileBackedBinningIndex.open(indexWithCsi(samGz), false)) {
            final TabixIndex.Header tabixHeader = TabixIndex.readCsiAux(csi.getAux());
            Assert.assertEquals(tabixHeader.format(), TabixFormat.SAM);
            Assert.assertEquals(
                    tabixHeader.sequenceNames(),
                    header.getSequenceDictionary().getSequences().stream()
                            .map(SAMSequenceRecord::getSequenceName)
                            .collect(Collectors.toList()));
        }
        assertAnswersAsTheBamDoes(samGz);
    }

    @Test
    public void testCsiWrittenForABamNamesNoSequences() throws IOException {
        final Path csi = Files.createTempFile("bam.", ".csi");
        IOUtil.deleteOnExit(csi);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(bam)) {
            BAMIndexer.createIndex(reader, csi, null, BamIndexType.CSI);
        }
        try (FileBackedBinningIndex index = FileBackedBinningIndex.open(csi, false)) {
            Assert.assertEquals(index.getAux().length, 0);
        }
    }

    /**
     * samtools asks a CSI by the header's numbering and tabix by the names in it; the file's first sequence has no
     * reads, so an index that served only one of the two would give the other some other sequence's reads, or none.
     */
    @Test
    public void testCsiWrittenForSamTextIsReadRightlyBySamtoolsAndByTabix() throws IOException {
        if (!TabixTestUtils.isTabixAvailable() || !SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools and tabix are not both available");
        }
        final Path samGz = writeSamGz();
        indexWithCsi(samGz);
        try (SamReader expected = SamReaderFactory.makeDefault().open(bam)) {
            for (final String region : List.of("chr2:1-20000", "chr4:30000-50000", "chr1", "chr3")) {
                final String sequence = region.split(":")[0];
                final int start = region.contains(":") ? Integer.parseInt(region.split("[:-]")[1]) : 0;
                final int end = region.contains(":") ? Integer.parseInt(region.split("[:-]")[2]) : 0;
                final int count =
                        drain(expected.queryOverlapping(sequence, start, end)).size();

                final String samtoolsCount = SamtoolsTestUtils.executeSamToolsCommand("view -c " + samGz + " " + region)
                        .stdout
                        .trim();
                Assert.assertEquals(samtoolsCount, Integer.toString(count), "samtools, " + region);
                Assert.assertEquals(
                        TabixTestUtils.executeTabix(samGz.toString(), region).size(), count, "tabix, " + region);
            }
        }
    }

    /** A CSI of the file, references numbered as the header numbers them, with the given bytes as its aux block. */
    private Path indexWithCsiCarrying(final Path samGz, final byte[] aux) throws IOException {
        final BinningIndex.Builder builder = new BinningIndex.Builder(14, 5, true);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            for (final SAMRecord record : reader) {
                if (record.getReferenceIndex() == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                    break;
                }
                final Chunk chunk = ((BAMFileSpan) record.getFileSource().getFilePointer()).getSingleChunk();
                builder.add(
                        record.getReferenceIndex(),
                        record.getAlignmentStart(),
                        record.getAlignmentEnd(),
                        chunk.getChunkStart(),
                        chunk.getChunkEnd());
            }
        }
        final Path csi = samGz.resolveSibling(samGz.getFileName() + ".csi");
        IOUtil.deleteOnExit(csi);
        try (BinaryCodec codec =
                new BinaryCodec(new BlockCompressedOutputStream(Files.newOutputStream(csi), (Path) null))) {
            builder.build(header.getSequenceDictionary().size()).writeCsi(codec, aux);
        }
        return csi;
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testCsiWhoseAuxBlockIsNotATabixHeaderIsRefused() throws IOException {
        final Path samGz = writeSamGz();
        indexWithCsiCarrying(samGz, new byte[] {1, 2, 3});
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testFileNamedAsATabixIndexThatIsNotOneIsRefused() throws IOException {
        final Path samGz = writeSamGz();
        final Path bareCsi = indexWithCsiCarrying(samGz, new byte[0]);
        Files.move(bareCsi, samGz.resolveSibling(samGz.getFileName() + ".tbi"));
        IOUtil.deleteOnExit(samGz.resolveSibling(samGz.getFileName() + ".tbi"));
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }

    @Test(expectedExceptions = SAMFormatException.class)
    public void testTabixIndexListingASequenceTwiceIsRefused() throws IOException {
        final Path samGz = writeSamGz();
        indexAsTabixWould(samGz, TabixIndexType.CSI, TabixFormat.SAM, name -> "chr2", false);
        try (SamReader sam = SamReaderFactory.makeDefault().open(samGz)) {
            sam.queryOverlapping("chr2", 1, 1_000);
        }
    }
}
