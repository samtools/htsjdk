/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
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
package htsjdk.tribble.index.tabix;

import htsjdk.HtsjdkTest;
import htsjdk.index.BinningIndex;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.tribble.AbstractFeatureReader;
import htsjdk.tribble.FeatureReader;
import htsjdk.tribble.TestUtils;
import htsjdk.tribble.Tribble;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.bed.BEDCodec;
import htsjdk.tribble.bed.BEDFeature;
import htsjdk.tribble.index.IndexFactory;
import htsjdk.tribble.util.LittleEndianOutputStream;
import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.variantcontext.writer.VariantContextWriter;
import htsjdk.variant.variantcontext.writer.VariantContextWriterBuilder;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFFileReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalLong;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class TabixIndexTest extends HtsjdkTest {
    private static final Path SMALL_TABIX_FILE = Path.of(TestUtils.DATA_DIR, "tabix/trioDup.vcf.gz.tbi");
    private static final Path BIGGER_TABIX_FILE = Path.of(TestUtils.DATA_DIR, "tabix/bigger.vcf.gz.tbi");

    /**
     * Read an existing index from disk, write it to a temp file, read that in, and assert that both in-memory
     * representations are identical.  Disk representations may not be identical due to arbitrary bin order and
     * compression differences.
     */
    @Test(dataProvider = "readWriteTestDataProvider")
    public void readWriteTest(final Path tabixFile) throws Exception {
        final TabixIndex index = new TabixIndex(tabixFile);
        final Path indexFile = Files.createTempFile("TabixIndexTest.", FileExtensions.TABIX_INDEX);
        indexFile.toFile().deleteOnExit();
        try (final LittleEndianOutputStream los = new LittleEndianOutputStream(
                new BlockCompressedOutputStream(Files.newOutputStream(indexFile), (Path) null))) {
            index.write(los);
        }
        final TabixIndex index2 = new TabixIndex(indexFile);
        Assert.assertEquals(index, index2);
        // Unfortunately, can't do byte comparison of original file and temp file, because 1) different compression
        // levels; and more importantly, arbitrary order of bins in bin list.
    }

    @DataProvider(name = "readWriteTestDataProvider")
    public Object[][] readWriteTestDataProvider() {
        return new Object[][] {{SMALL_TABIX_FILE}, {BIGGER_TABIX_FILE}};
    }

    // TODO: This test reads an existing .tbi on a .gz, but only writes a .tbi index for a plain vcf, which
    // tabix doesn't appear to even allow.
    @Test
    public void testQueryProvidedItemsAmount() throws IOException {
        final String VCF = "src/test/resources/htsjdk/tribble/tabix/YRI.trio.2010_07.indel.sites.vcf";
        // Note that we store only compressed files
        final Path plainTextVcfInputFile = Path.of(VCF);
        plainTextVcfInputFile.toFile().deleteOnExit();
        final Path plainTextVcfIndexFile = Path.of(VCF + ".tbi");
        plainTextVcfIndexFile.toFile().deleteOnExit();
        final Path compressedVcfInputFile = Path.of(VCF + ".gz");
        final Path compressedTbiIndexFile = Path.of(VCF + ".gz.tbi");
        final VCFFileReader compressedVcfReader = new VCFFileReader(compressedVcfInputFile, compressedTbiIndexFile);

        // create plain text VCF without "index on the fly" option
        final VariantContextWriter plainTextVcfWriter = new VariantContextWriterBuilder()
                .setOptions(VariantContextWriterBuilder.NO_OPTIONS)
                .setOutputFile(VCF)
                .build();
        plainTextVcfWriter.writeHeader(compressedVcfReader.getFileHeader());
        for (VariantContext vc : compressedVcfReader) {
            if (vc != null) plainTextVcfWriter.add(vc);
        }
        plainTextVcfWriter.close();

        IndexFactory.createTabixIndex(
                        plainTextVcfInputFile,
                        new VCFCodec(),
                        TabixFormat.VCF,
                        new VCFFileReader(plainTextVcfInputFile, false)
                                .getFileHeader()
                                .getSequenceDictionary()) // create TabixIndex straight from plaintext VCF
                .write(plainTextVcfIndexFile); // write it

        // TODO: you can pass in a .tbi file as the index for a plain .vcf, but if you *don't* pass in the file name and
        // just require an index, VCFFleREader will only look for a .idx on a plain vcf
        final VCFFileReader plainTextVcfReader = new VCFFileReader(plainTextVcfInputFile, plainTextVcfIndexFile);
        // Now we have both plaintext and compressed VCFs with provided TabixIndex-es and could test their
        // "queryability"

        // magic numbers chosen from just looking in provided VCF file
        try {
            // just somewhere in middle of chromosome
            Assert.assertEquals(42, countIteratedElements(compressedVcfReader.query("1", 868379 - 1, 1006891 + 1)));
            Assert.assertEquals(42, countIteratedElements(plainTextVcfReader.query("1", 868379 - 1, 1006891 + 1)));
            // chromosome start
            Assert.assertEquals(13, countIteratedElements(compressedVcfReader.query("1", 1, 836463 + 1)));
            Assert.assertEquals(13, countIteratedElements(plainTextVcfReader.query("1", 1, 836463 + 1)));
            // chromosome end
            Assert.assertEquals(
                    36, countIteratedElements(compressedVcfReader.query("1", 76690833 - 1, 76837502 + 11111111)));
            Assert.assertEquals(
                    36, countIteratedElements(plainTextVcfReader.query("1", 76690833 - 1, 76837502 + 11111111)));
            // where's no one feature in the middle of chromosome
            Assert.assertEquals(0, countIteratedElements(compressedVcfReader.query("1", 36606472 + 1, 36623523 - 1)));
            Assert.assertEquals(0, countIteratedElements(plainTextVcfReader.query("1", 36606472 + 1, 36623523 - 1)));
            // before chromosome
            Assert.assertEquals(0, countIteratedElements(compressedVcfReader.query("1", 1, 10)));
            Assert.assertEquals(0, countIteratedElements(plainTextVcfReader.query("1", 1, 10)));
            // after chromosome
            Assert.assertEquals(0, countIteratedElements(compressedVcfReader.query("1", 76837502 * 15, 76837502 * 16)));
            Assert.assertEquals(0, countIteratedElements(plainTextVcfReader.query("1", 76837502 * 15, 76837502 * 16)));
        } catch (NullPointerException e) {
            Assert.fail("Exception caught on querying: ", e);
            // before fix exception was thrown from 'TabixIndex.getBlocks()' on 'chunks.size()' while 'chunks == null'
            // for plain files
        } finally {
            plainTextVcfReader.close();
            compressedVcfReader.close();
        }
    }

    @DataProvider(name = "bedTabixIndexTestData")
    public Object[][] getBedIndexFactory() {
        // These files have accompanying .tbi files created with tabix.
        return new Object[][] {
            new Object[] {
                // BGZP BED file with no header, 2 features
                Path.of(TestUtils.DATA_DIR, "bed/2featuresNoHeader.bed.gz"),
                Arrays.asList(new Interval("chr1", 1, 10), new Interval("chr1", 100, 1000000))
            },
            new Object[] {
                // BGZP BED file with no header, 3 features; one feature falls in between the query intervals
                Path.of(TestUtils.DATA_DIR, "bed/3featuresNoHeader.bed.gz"),
                Arrays.asList(new Interval("chr1", 1, 10), new Interval("chr1", 100, 1000000))
            },
            new Object[] {
                // same file as above (BGZP BED file with no header, 3 features), but change the query to return
                // only the single interval for the feature that falls in between the other features
                Path.of(TestUtils.DATA_DIR, "bed/3featuresNoHeader.bed.gz"), Arrays.asList(new Interval("chr1", 15, 20))
            },
            new Object[] {
                // BGZP BED file with one line header, 2 features
                Path.of(TestUtils.DATA_DIR, "bed/2featuresWithHeader.bed.gz"),
                Arrays.asList(new Interval("chr1", 1, 10), new Interval("chr1", 100, 1000000))
            },
        };
    }

    @Test(dataProvider = "bedTabixIndexTestData")
    public void testBedTabixIndex(final Path inputBed, final List<Interval> queryIntervals) throws Exception {
        // copy the input file and create an index for the copy
        final Path tempDir = IOUtil.createTempDir("testBedTabixIndex.tmp");
        tempDir.toFile().deleteOnExit();
        final Path tmpBed = tempDir.resolve(inputBed.getFileName().toString());
        Files.copy(inputBed, tmpBed);
        tmpBed.toFile().deleteOnExit();
        final TabixIndex tabixIndexGz = IndexFactory.createTabixIndex(tmpBed, new BEDCodec(), null);
        tabixIndexGz.writeBasedOnFeaturePath(tmpBed);
        final Path tmpIndex = Tribble.tabixIndexPath(tmpBed);
        tmpIndex.toFile().deleteOnExit();

        // iterate over the query intervals and validate the query results
        try (final FeatureReader<BEDFeature> originalReader = AbstractFeatureReader.getFeatureReader(
                        inputBed.toAbsolutePath().toString(), new BEDCodec());
                final FeatureReader<BEDFeature> createdReader = AbstractFeatureReader.getFeatureReader(
                        tmpBed.toAbsolutePath().toString(), new BEDCodec())) {
            for (final Interval interval : queryIntervals) {
                final Iterator<BEDFeature> originalIt =
                        originalReader.query(interval.getContig(), interval.getStart(), interval.getEnd());
                final Iterator<BEDFeature> createdIt =
                        createdReader.query(interval.getContig(), interval.getStart(), interval.getEnd());
                while (originalIt.hasNext()) {
                    Assert.assertTrue(createdIt.hasNext(), "some features not returned from query");
                    BEDFeature bedOrig = originalIt.next();
                    BEDFeature bedTmp = createdIt.next();
                    Assert.assertEquals(bedOrig.getContig(), bedTmp.getContig());
                    Assert.assertEquals(bedOrig.getStart(), bedTmp.getStart());
                    Assert.assertEquals(bedOrig.getEnd(), bedTmp.getEnd());
                }
            }
        }
    }

    @Test
    public void testReadCsiAuxGivesTheFormatAndSequenceNamesOfATabixMadeCsi() throws IOException {
        final List<String> names = List.of("chr2", "chr10", "*");
        final TabixIndex index = new TabixIndex(
                TabixFormat.SAM, names, new BinningIndex.Builder(14, 6, true).build(names.size()), TabixIndexType.CSI);
        final ByteArrayOutputStream csi = new ByteArrayOutputStream();
        index.write(new LittleEndianOutputStream(csi));
        final byte[] aux = BinningIndex.readCsi(new BinaryCodec(new ByteArrayInputStream(csi.toByteArray())))
                .aux();

        final TabixIndex.Header header = TabixIndex.readCsiAux(aux);
        Assert.assertEquals(header.format(), TabixFormat.SAM);
        Assert.assertEquals(header.sequenceNames(), names);
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testReadCsiAuxRejectsABlockTooShortForATabixHeader() {
        TabixIndex.readCsiAux(new byte[] {1, 2, 3});
    }

    @Test(expectedExceptions = TribbleException.class)
    public void testReadCsiAuxRejectsAnEmptyBlockAsSamtoolsWritesForABam() {
        TabixIndex.readCsiAux(new byte[0]);
    }

    private static int countIteratedElements(final Iterator iterator) {
        int counter = 0;
        while (iterator.hasNext()) {
            iterator.next();
            counter++;
        }
        return counter;
    }

    /**
     * An index of c1 with three records and c2 with two, each record in its own BGZF block, and the given number of
     * records without a position; with the counts tabix keeps if {@code keepCounts}.
     */
    private static TabixIndex indexWithRecords(final boolean keepCounts, final long recordsWithoutAPosition) {
        final BinningIndex.Builder builder =
                new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH);
        if (keepCounts) builder.reportingRecordCounts();
        final int[] references = {0, 0, 0, 1, 1};
        for (int i = 0; i < references.length; i++) {
            builder.add(references[i], 1000 * (i + 1), 1000 * (i + 1), (long) i << 16, (long) (i + 1) << 16);
            if (keepCounts) builder.addRecordCounts(1, 0);
        }
        if (keepCounts) builder.addNoCoordinateRecords(recordsWithoutAPosition);
        return new TabixIndex(TabixFormat.VCF, List.of("c1", "c2"), builder.build(2));
    }

    @Test
    public void testRecordCountOfASequenceIsTheCountInItsMetadata() {
        final TabixIndex index = indexWithRecords(true, 0);
        Assert.assertEquals(index.getRecordCount("c1"), OptionalLong.of(3));
        Assert.assertEquals(index.getRecordCount("c2"), OptionalLong.of(2));
    }

    @Test
    public void testRecordCountOfASequenceTheIndexDoesNotListIsZero() {
        Assert.assertEquals(indexWithRecords(true, 0).getRecordCount("c3"), OptionalLong.of(0));
    }

    @Test
    public void testRecordCountOfTheFileIncludesRecordsWithoutAPosition() {
        Assert.assertEquals(indexWithRecords(true, 4).getRecordCount(), OptionalLong.of(9));
    }

    @Test
    public void testRecordCountsOfAnIndexWithoutCountsAreEmpty() {
        final TabixIndex index = indexWithRecords(false, 0);
        Assert.assertEquals(index.getRecordCount("c1"), OptionalLong.empty());
        Assert.assertEquals(index.getRecordCount("c3"), OptionalLong.empty());
        Assert.assertEquals(index.getRecordCount(), OptionalLong.empty());
    }

    @Test
    public void testRecordCountOfAnIndexWithNoRecordsIsZero() {
        final TabixIndex index = new TabixIndex(
                TabixFormat.VCF,
                List.of(),
                new BinningIndex.Builder(BinningIndex.BAI_MIN_SHIFT, BinningIndex.BAI_DEPTH).build(0));
        Assert.assertEquals(index.getRecordCount(), OptionalLong.of(0));
    }
}
