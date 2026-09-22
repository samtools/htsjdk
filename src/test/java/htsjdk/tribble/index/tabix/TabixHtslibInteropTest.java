package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.index.ReferenceBins;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.CloseableIterator;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.utils.TabixTestUtils;
import htsjdk.variant.variantcontext.Allele;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.VariantContextBuilder;
import htsjdk.variant.variantcontext.writer.Options;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFConstants;
import htsjdk.variant.vcf.VCFFileReader;
import htsjdk.variant.vcf.VCFHeader;
import htsjdk.variant.vcf.VCFHeaderLineType;
import htsjdk.variant.vcf.VCFInfoHeaderLine;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.stream.Collectors;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * htsjdk's tabix indexes against htslib's {@code tabix}: each must be able to query through an index the other
 * wrote.
 */
public class TabixHtslibInteropTest extends HtsjdkTest {
    private static final SAMSequenceDictionary DICTIONARY = new SAMSequenceDictionary(List.of(
            new SAMSequenceRecord("c1", 60_000_000),
            new SAMSequenceRecord("c2", 60_000_000),
            new SAMSequenceRecord("c3", 60_000_000)));

    /** Regions chosen to hit single bins, bin boundaries, long records and stretches with nothing in them. */
    private static final List<int[]> REGIONS = List.of(
            new int[] {1, 1_000},
            new int[] {16_000, 17_000},
            new int[] {1_000_000, 1_040_000},
            new int[] {9_999_000, 30_000_000},
            new int[] {41_000_000, 41_000_500},
            new int[] {59_000_000, 60_000_000});

    @BeforeMethod
    public void skipWithoutTabix() {
        if (!TabixTestUtils.isTabixAvailable()) {
            throw new SkipException("tabix not available on local device");
        }
    }

    private enum Indexing {
        NONE,
        ON_THE_FLY,
        OVER_WHOLE_DICTIONARY
    }

    /** Records every 7 kb on each named contig; every 40th is a 300 kb deletion so that higher bins are used. */
    private static Path writeVcf(final Indexing indexing, final String... contigs) throws IOException {
        final Path dir = IOUtil.createTempDir("TabixHtslibInteropTest");
        IOUtil.deleteOnExit(dir);
        final Path vcf = dir.resolve("records.vcf.gz");
        final VCFHeader header = new VCFHeader();
        header.setSequenceDictionary(DICTIONARY);
        header.addMetaDataLine(new VCFInfoHeaderLine(VCFConstants.END_KEY, 1, VCFHeaderLineType.Integer, "End"));

        final VariantContextWriterBuilder builder =
                new VariantContextWriterBuilder().setReferenceDictionary(DICTIONARY);
        OutputStream indexOut = null;
        switch (indexing) {
            case NONE -> builder.setOutputPath(vcf).unsetOption(Options.INDEX_ON_THE_FLY);
            case ON_THE_FLY -> builder.setOutputPath(vcf).setOption(Options.INDEX_ON_THE_FLY);
            case OVER_WHOLE_DICTIONARY -> {
                indexOut = Files.newOutputStream(dir.resolve(vcf.getFileName() + FileExtensions.TABIX_INDEX));
                builder.setOutputStream(new BlockCompressedOutputStream(Files.newOutputStream(vcf), (Path) null))
                        .setIndexCreator(new StreamBasedTabixIndexCreator(DICTIONARY, TabixFormat.VCF, indexOut))
                        .setOption(Options.INDEX_ON_THE_FLY);
            }
        }
        try (final VariantContextWriter writer = builder.build()) {
            writer.writeHeader(header);
            for (final String contig : contigs) {
                for (int i = 0, position = 500; position < 50_000_000; i++, position += 7_000) {
                    if (i % 40 == 0) {
                        writer.add(new VariantContextBuilder(
                                        "test",
                                        contig,
                                        position,
                                        position + 300_000,
                                        List.of(Allele.REF_A, Allele.create("<DEL>")))
                                .attribute(VCFConstants.END_KEY, position + 300_000)
                                .make());
                    } else {
                        writer.add(new VariantContextBuilder(
                                        "test", contig, position, position, List.of(Allele.REF_A, Allele.ALT_C))
                                .make());
                    }
                }
            }
        } finally {
            if (indexOut != null) indexOut.close();
        }
        return vcf;
    }

    /** Start positions of the records htsjdk returns for a 1-based inclusive region, in file order. */
    private static List<Integer> startsFromHtsjdk(final Path vcf, final String contig, final int[] region) {
        final List<Integer> starts = new ArrayList<>();
        try (final VCFFileReader reader = new VCFFileReader(vcf, true);
                final CloseableIterator<VariantContext> iterator = reader.query(contig, region[0], region[1])) {
            while (iterator.hasNext()) starts.add(iterator.next().getStart());
        }
        return starts;
    }

    /** Start positions of the records tabix returns for the same region. */
    private static List<Integer> startsFromTabix(final Path vcf, final String contig, final int[] region) {
        return TabixTestUtils.executeTabix(vcf.toString(), contig + ":" + region[0] + "-" + region[1]).stream()
                .map(line -> Integer.parseInt(line.split("\t")[1]))
                .collect(Collectors.toList());
    }

    /** What a query should return, worked out from how {@link #writeVcf} lays records out. */
    private static List<Integer> expectedStarts(final int[] region) {
        final List<Integer> starts = new ArrayList<>();
        for (int i = 0, position = 500; position < 50_000_000; i++, position += 7_000) {
            final int end = (i % 40 == 0) ? position + 300_000 : position;
            if (position <= region[1] && end >= region[0]) starts.add(position);
        }
        return starts;
    }

    @Test
    public void testHtsjdkQueriesThroughAnIndexWrittenByTabix() throws IOException {
        final Path vcf = writeVcf(Indexing.NONE, "c1", "c2");
        TabixTestUtils.indexVcf(vcf);
        for (final int[] region : REGIONS) {
            Assert.assertEquals(startsFromHtsjdk(vcf, "c2", region), expectedStarts(region));
        }
    }

    @Test
    public void testTabixQueriesThroughAnIndexWrittenByHtsjdk() throws IOException {
        final Path vcf = writeVcf(Indexing.ON_THE_FLY, "c1", "c2");
        for (final int[] region : REGIONS) {
            Assert.assertEquals(startsFromTabix(vcf, "c2", region), expectedStarts(region));
        }
    }

    @Test
    public void testTabixQueriesThroughAnIndexListingSequencesWithoutRecords() throws IOException {
        final Path vcf = writeVcf(Indexing.OVER_WHOLE_DICTIONARY, "c2");
        Assert.assertEquals(TabixTestUtils.executeTabix("-l", vcf.toString()), List.of("c1", "c2", "c3"));
        for (final int[] region : REGIONS) {
            Assert.assertEquals(startsFromTabix(vcf, "c2", region), expectedStarts(region));
        }
        Assert.assertEquals(startsFromTabix(vcf, "c1", new int[] {1, 60_000_000}), List.of());
    }

    private static final List<int[]> LARGE_REGIONS = List.of(
            new int[] {536_870_000, 536_872_000}, // straddles 2^29, TBI's limit
            new int[] {600_000_000, 600_100_000},
            new int[] {100_000_000, 700_000_000},
            new int[] {829_000_000, 830_000_000});

    private static Path writeLargeContigVcf(final TabixIndexType indexType, final int csiMinShift) throws IOException {
        final VariantContextWriterBuilder builder = new VariantContextWriterBuilder();
        if (indexType == null) {
            builder.unsetOption(Options.INDEX_ON_THE_FLY);
        } else {
            builder.setOption(Options.INDEX_ON_THE_FLY)
                    .setTabixIndexType(indexType)
                    .setCsiMinShift(csiMinShift);
        }
        return TestVcfs.write(
                TestVcfs.tempDir("TabixHtslibInteropTest"),
                TestVcfs.LARGE_CONTIG_DICTIONARY,
                builder,
                "small",
                "large");
    }

    @Test
    public void testHtsjdkQueriesThroughACsiIndexWrittenByTabix() throws IOException {
        final Path vcf = writeLargeContigVcf(null, 14);
        TabixTestUtils.executeTabix("-f", "-C", "-p", "vcf", vcf.toString());
        for (final int[] region : LARGE_REGIONS) {
            Assert.assertEquals(
                    TestVcfs.queryStarts(vcf, "large", region[0], region[1]),
                    TestVcfs.LARGE_CONTIG.startsOverlapping(region[0], region[1]));
        }
    }

    @Test
    public void testTabixQueriesThroughACsiIndexWrittenByHtsjdk() throws IOException {
        final Path vcf = writeLargeContigVcf(TabixIndexType.CSI, 14);
        Assert.assertEquals(TabixTestUtils.executeTabix("-l", vcf.toString()), List.of("small", "large"));
        for (final int[] region : LARGE_REGIONS) {
            Assert.assertEquals(
                    startsFromTabix(vcf, "large", region),
                    TestVcfs.LARGE_CONTIG.startsOverlapping(region[0], region[1]));
        }
    }

    @Test
    public void testTabixQueriesThroughACsiIndexWithSmallerBins() throws IOException {
        final Path vcf = writeLargeContigVcf(TabixIndexType.CSI, 12);
        for (final int[] region : LARGE_REGIONS) {
            Assert.assertEquals(
                    startsFromTabix(vcf, "large", region),
                    TestVcfs.LARGE_CONTIG.startsOverlapping(region[0], region[1]));
        }
    }

    @Test
    public void testHtsjdkAndTabixAgreeOnACsiIndexWrittenByTabix() throws IOException {
        final Path vcf = writeLargeContigVcf(null, 14);
        TabixTestUtils.executeTabix("-f", "-C", "-p", "vcf", vcf.toString());
        for (final int[] region : LARGE_REGIONS) {
            Assert.assertEquals(
                    TestVcfs.queryStarts(vcf, "large", region[0], region[1]), startsFromTabix(vcf, "large", region));
        }
    }

    @Test
    public void testHtsjdkAndTabixAgreeOnAnIndexWrittenByTabix() throws IOException {
        final Path vcf = writeVcf(Indexing.NONE, "c1", "c3");
        TabixTestUtils.indexVcf(vcf);
        for (final int[] region : REGIONS) {
            Assert.assertEquals(startsFromHtsjdk(vcf, "c3", region), startsFromTabix(vcf, "c3", region));
        }
    }

    /**
     * tabix also writes each sequence a pseudo-bin of record counts, and a count of records without a position,
     * which htsjdk leaves out of a TBI; so it is the bins and the linear indexes that are compared.
     */
    @Test
    public void testTbiWrittenByHtsjdkHasTheBinsAndLinearIndexOfTheOneTabixWrites() throws IOException {
        final Path vcf = writeVcf(Indexing.ON_THE_FLY, "c1", "c2");
        final Path tbi = vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX);
        final BinningIndex ours = new TabixIndex(tbi).getBinningIndex();
        TabixTestUtils.indexVcf(vcf);
        final BinningIndex theirs = new TabixIndex(tbi).getBinningIndex();

        Assert.assertEquals(ours.getReferenceCount(), theirs.getReferenceCount());
        for (int i = 0; i < ours.getReferenceCount(); i++) {
            final ReferenceBins ourBins = ours.getReference(i);
            final ReferenceBins theirBins = theirs.getReference(i);
            Assert.assertEquals(ourBins.getBinCount(), theirBins.getBinCount(), "bins of sequence " + i);
            for (int bin = 0; bin < ourBins.getBinCount(); bin++) {
                Assert.assertEquals(ourBins.getBinNumber(bin), theirBins.getBinNumber(bin));
                Assert.assertEquals(
                        ourBins.getChunks(bin), theirBins.getChunks(bin), "bin " + ourBins.getBinNumber(bin));
            }
            Assert.assertEquals(ourBins.getLinearIndex(), theirBins.getLinearIndex(), "linear index of sequence " + i);
        }
    }

    @Test
    public void testCsiWrittenByHtsjdkHasTheContentOfTheOneTabixWrites() throws IOException {
        final Path vcf = writeLargeContigVcf(TabixIndexType.CSI, 14);
        final Path csi = vcf.resolveSibling(vcf.getFileName() + FileExtensions.CSI);
        final TabixIndex ours = new TabixIndex(csi);
        TabixTestUtils.executeTabix("-f", "-C", "-p", "vcf", vcf.toString());
        Assert.assertEquals(ours.getBinningIndex(), new TabixIndex(csi).getBinningIndex());
    }

    @Test
    public void testHtsjdkReadsTheRecordCountsOfAnIndexWrittenByTabix() throws IOException {
        final Path vcf = writeVcf(Indexing.NONE, "c1", "c2");
        TabixTestUtils.indexVcf(vcf);
        final TabixIndex index = new TabixIndex(vcf.resolveSibling(vcf.getFileName() + FileExtensions.TABIX_INDEX));
        final long perContig = (50_000_000 - 500 - 1) / 7_000 + 1; // writeVcf's positions: 500, 7500, ... < 50 Mb
        Assert.assertEquals(index.getRecordCount("c1"), OptionalLong.of(perContig));
        Assert.assertEquals(index.getRecordCount("c2"), OptionalLong.of(perContig));
        Assert.assertEquals(index.getRecordCount(), OptionalLong.of(2 * perContig));
    }
}
