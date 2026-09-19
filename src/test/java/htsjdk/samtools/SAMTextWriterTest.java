/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
import htsjdk.index.BinningIndex;
import htsjdk.index.FileBackedBinningIndex;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.Test;

public class SAMTextWriterTest extends HtsjdkTest {

    private SAMRecordSetBuilder getSamRecordSet(final boolean sortForMe, final SAMFileHeader.SortOrder sortOrder) {
        final SAMRecordSetBuilder ret = new SAMRecordSetBuilder(sortForMe, sortOrder);
        ret.addPair("readB", 20, 200, 300);
        ret.addPair("readA", 20, 100, 150);
        ret.addFrag("readC", 20, 140, true);
        ret.addFrag("readD", 20, 140, false);
        return ret;
    }

    @Test
    public void testNullHeader() throws Exception {
        final SAMRecordSetBuilder recordSetBuilder = getSamRecordSet(true, SAMFileHeader.SortOrder.coordinate);
        for (final SAMRecord rec : recordSetBuilder.getRecords()) {
            rec.setHeader(null);
        }
        doTest(recordSetBuilder);
    }

    @Test
    public void testBasic() throws Exception {
        doTest(SamFlagField.DECIMAL);
    }

    @Test
    public void testBasicHexFlag() throws Exception {
        doTest(SamFlagField.HEXADECIMAL);
    }

    @Test
    public void testBasicOctalFlag() throws Exception {
        doTest(SamFlagField.OCTAL);
    }

    @Test
    public void testBasicStringFlag() throws Exception {
        doTest(SamFlagField.STRING);
    }

    private void doTest(final SAMRecordSetBuilder recordSetBuilder) throws Exception {
        doTest(recordSetBuilder, SamFlagField.DECIMAL);
    }

    private void doTest(final SamFlagField samFlagField) throws Exception {
        doTest(getSamRecordSet(true, SAMFileHeader.SortOrder.coordinate), samFlagField);
    }

    private void doTest(final SAMRecordSetBuilder recordSetBuilder, final SamFlagField samFlagField) throws Exception {
        SamReader inputSAM = recordSetBuilder.getSamReader();
        final Path samFile = Files.createTempFile("tmp.", ".sam");
        samFile.toFile().deleteOnExit();
        final Map<String, Object> tagMap = new HashMap<String, Object>();
        tagMap.put("XC", 'q');
        tagMap.put("XI", 12345);
        tagMap.put("XF", 1.2345f);
        tagMap.put("XS", "Hi,Mom!");
        for (final Map.Entry<String, Object> entry : tagMap.entrySet()) {
            inputSAM.getFileHeader()
                    .setAttribute(entry.getKey(), entry.getValue().toString());
        }
        final SAMFileWriter samWriter = new SAMFileWriterFactory()
                .setSamFlagFieldOutput(samFlagField)
                .makeSAMWriter(inputSAM.getFileHeader(), false, samFile);
        for (final SAMRecord samRecord : inputSAM) {
            samWriter.addAlignment(samRecord);
        }
        samWriter.close();

        // Read it back in and confirm that it matches the input
        inputSAM = recordSetBuilder.getSamReader();
        // Stuff in the attributes again since this has been created again.
        for (final Map.Entry<String, Object> entry : tagMap.entrySet()) {
            inputSAM.getFileHeader()
                    .setAttribute(entry.getKey(), entry.getValue().toString());
        }

        final SamReader newSAM = SamReaderFactory.makeDefault().open(samFile);
        Assert.assertEquals(newSAM.getFileHeader(), inputSAM.getFileHeader());
        final Iterator<SAMRecord> inputIt = inputSAM.iterator();
        final Iterator<SAMRecord> newSAMIt = newSAM.iterator();
        while (inputIt.hasNext()) {
            Assert.assertTrue(newSAMIt.hasNext());
            final SAMRecord inputSAMRecord = inputIt.next();
            final SAMRecord newSAMRecord = newSAMIt.next();

            // Force reference index attributes to be populated
            inputSAMRecord.getReferenceIndex();
            newSAMRecord.getReferenceIndex();
            inputSAMRecord.getMateReferenceIndex();
            newSAMRecord.getMateReferenceIndex();

            Assert.assertEquals(newSAMRecord, inputSAMRecord);
        }
        Assert.assertFalse(newSAMIt.hasNext());
        inputSAM.close();
    }

    @Test
    public void testEmptyArrayAttributeHasNoCommaWhenWrittenToSAM() {
        final SAMFileHeader header = new SAMFileHeader();
        final SAMRecord record = new SAMRecord(header);
        record.setAttribute("xa", new int[0]);
        Assert.assertTrue(record.getSAMString().endsWith("xa:B:i"));
    }

    // On-the-fly indexing of bgzipped SAM

    /** Records on three references, ending with placed reads. */
    private static SAMRecordSetBuilder indexTestRecords() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 300; i++) {
            builder.addFrag("read" + i, i % 3, 1 + 50 * (i / 3), i % 2 == 0);
        }
        return builder;
    }

    /** Records ending with unplaced reads. */
    private static SAMRecordSetBuilder indexTestRecordsWithUnplaced() {
        final SAMRecordSetBuilder builder = indexTestRecords();
        for (int i = 0; i < 10; i++) {
            builder.addUnmappedFragment("unplaced" + i);
        }
        return builder;
    }

    /** Writes records to a sam.gz, optionally with an on-the-fly index. */
    private static Path writeSamGz(
            final SAMRecordSetBuilder records, final Path directory, final boolean createIndex, final BamIndexType type)
            throws IOException {
        final Path samGz = directory.resolve("reads.sam.gz");
        final SAMFileWriterFactory factory =
                new SAMFileWriterFactory().setCreateIndex(createIndex).setCreateMd5File(false);
        if (type != null) factory.setSamIndexType(type);
        try (SAMFileWriter writer = factory.makeSAMWriter(records.getHeader(), true, samGz)) {
            records.getRecords().forEach(writer::addAlignment);
        }
        return samGz;
    }

    /** Reads a BAI from a path. */
    private static BinningIndex readBai(final Path path) {
        try (FileBackedBinningIndex idx = FileBackedBinningIndex.open(path, true)) {
            return idx.loadAll();
        }
    }

    /** Reads a CSI from a path, returning its index. */
    private static BinningIndex.CsiContents readCsi(final Path csi) throws IOException {
        try (InputStream in = new BlockCompressedInputStream(Files.newInputStream(csi))) {
            return BinningIndex.readCsi(new BinaryCodec(in));
        }
    }

    /** Builds an index of a finished file using BAMIndexer.createIndex. */
    private static BinningIndex createIndexOfFile(final Path samGz, final BamIndexType type) throws IOException {
        final Path indexPath = Files.createTempFile("createIndex.", type == BamIndexType.CSI ? ".csi" : ".bai");
        IOUtil.deleteOnExit(indexPath);
        try (SamReader reader = SamReaderFactory.makeDefault()
                .enable(SamReaderFactory.Option.INCLUDE_SOURCE_IN_RECORDS)
                .open(samGz)) {
            BAMIndexer.createIndex(reader, indexPath, null, type);
        }
        if (type == BamIndexType.CSI) {
            return readCsi(indexPath).index();
        } else {
            return readBai(indexPath);
        }
    }

    @Test
    public void testOnTheFlyCsiEqualsCreateIndexEndingWithPlacedRead() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecords();
        final Path directory = Files.createTempDirectory("samGzCsiPlaced");
        IOUtil.deleteOnExit(directory);
        final Path samGz = writeSamGz(records, directory, true, BamIndexType.CSI);
        final BinningIndex onTheFly =
                readCsi(samGz.resolveSibling("reads.sam.gz.csi")).index();
        final BinningIndex afterTheFact = createIndexOfFile(samGz, BamIndexType.CSI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyBaiEqualsCreateIndexEndingWithPlacedRead() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecords();
        final Path directory = Files.createTempDirectory("samGzBaiPlaced");
        IOUtil.deleteOnExit(directory);
        final Path samGz = writeSamGz(records, directory, true, BamIndexType.BAI);
        final BinningIndex onTheFly = readBai(samGz.resolveSibling("reads.sam.gz.bai"));
        final BinningIndex afterTheFact = createIndexOfFile(samGz, BamIndexType.BAI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyCsiEqualsCreateIndexEndingWithUnplacedReads() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecordsWithUnplaced();
        final Path directory = Files.createTempDirectory("samGzCsiUnplaced");
        IOUtil.deleteOnExit(directory);
        final Path samGz = writeSamGz(records, directory, true, BamIndexType.CSI);
        final BinningIndex onTheFly =
                readCsi(samGz.resolveSibling("reads.sam.gz.csi")).index();
        final BinningIndex afterTheFact = createIndexOfFile(samGz, BamIndexType.CSI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testOnTheFlyBaiEqualsCreateIndexEndingWithUnplacedReads() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecordsWithUnplaced();
        final Path directory = Files.createTempDirectory("samGzBaiUnplaced");
        IOUtil.deleteOnExit(directory);
        final Path samGz = writeSamGz(records, directory, true, BamIndexType.BAI);
        final BinningIndex onTheFly = readBai(samGz.resolveSibling("reads.sam.gz.bai"));
        final BinningIndex afterTheFact = createIndexOfFile(samGz, BamIndexType.BAI);
        Assert.assertEquals(onTheFly, afterTheFact);
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testRegionQueriesThroughOnTheFlyCsiMatchBam() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecords();
        final Path directory = Files.createTempDirectory("samGzQueryCsi");
        IOUtil.deleteOnExit(directory);
        writeSamGz(records, directory, true, BamIndexType.CSI);
        final Path samGz = directory.resolve("reads.sam.gz");

        // Write a BAM with a BAI for comparison
        final Path bam = directory.resolve("reads.bam");
        try (SAMFileWriter writer =
                new SAMFileWriterFactory().setCreateIndex(true).makeBAMWriter(records.getHeader(), true, bam)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        try (SamReader samReader = SamReaderFactory.makeDefault().open(samGz);
                SamReader bamReader = SamReaderFactory.makeDefault().open(bam)) {
            for (int contig = 0; contig < 3; contig++) {
                final String name = records.getHeader().getSequence(contig).getSequenceName();
                for (int start = 1; start < 5_000; start += 1_000) {
                    final List<String> samResult = new ArrayList<>();
                    try (CloseableIterator<SAMRecord> it = samReader.queryOverlapping(name, start, start + 500)) {
                        it.forEachRemaining(r -> samResult.add(r.getReadName()));
                    }
                    final List<String> bamResult = new ArrayList<>();
                    try (CloseableIterator<SAMRecord> it = bamReader.queryOverlapping(name, start, start + 500)) {
                        it.forEachRemaining(r -> bamResult.add(r.getReadName()));
                    }
                    Assert.assertEquals(samResult, bamResult, name + ":" + start);
                }
            }
        }
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testCsiCarriesTabixHeaderNamingEverySequenceInOrder() throws IOException {
        // Use records that skip a reference (0 has reads, 1 does not, 2 has reads) to verify all are listed
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < 100; i++) {
            records.addFrag("read" + i, i < 50 ? 0 : 2, 1 + 100 * (i % 50), false);
        }
        final Path directory = Files.createTempDirectory("samGzTabixHeader");
        IOUtil.deleteOnExit(directory);
        writeSamGz(records, directory, true, BamIndexType.CSI);
        final Path csi = directory.resolve("reads.sam.gz.csi");

        final BinningIndex.CsiContents contents = readCsi(csi);
        final TabixIndex.Header tabixHeader = TabixIndex.readCsiAux(contents.aux());
        Assert.assertEquals(tabixHeader.format(), TabixFormat.SAM);
        Assert.assertEquals(
                tabixHeader.sequenceNames(),
                records.getHeader().getSequenceDictionary().getSequences().stream()
                        .map(SAMSequenceRecord::getSequenceName)
                        .toList());
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testSamtoolsAndTabixReadTheOnTheFlyCsi() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable() || !TabixTestUtils.isTabixAvailable()) {
            throw new SkipException("samtools and tabix are not both available");
        }
        final SAMRecordSetBuilder records = indexTestRecords();
        final Path directory = Files.createTempDirectory("samGzSamtoolsTabix");
        IOUtil.deleteOnExit(directory);
        final Path samGz = writeSamGz(records, directory, true, BamIndexType.CSI);

        for (int contig = 0; contig < 3; contig++) {
            final int c = contig;
            final String name = records.getHeader().getSequence(contig).getSequenceName();
            final String region = name + ":1000-3000";
            final long expected = records.getRecords().stream()
                    .filter(r -> r.getReferenceIndex() == c
                            && r.getAlignmentStart() <= 3_000
                            && r.getAlignmentEnd() >= 1_000)
                    .count();
            final String samtoolsCount = SamtoolsTestUtils.executeSamToolsCommand("view -c " + samGz + " " + region)
                    .stdout
                    .trim();
            Assert.assertEquals(samtoolsCount, Long.toString(expected), "samtools, " + region);
            Assert.assertEquals(
                    TabixTestUtils.executeTabix(samGz.toString(), region).size(), expected, "tabix, " + region);
        }
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testBgzfBlocksAreNotOnePerRecord() throws IOException {
        final SAMRecordSetBuilder records = indexTestRecords();
        final Path directory = Files.createTempDirectory("samGzBlocks");
        IOUtil.deleteOnExit(directory);
        final Path withIndex = writeSamGz(records, directory, true, BamIndexType.CSI);
        final Path directoryNoIndex = Files.createTempDirectory("samGzBlocksNoIndex");
        IOUtil.deleteOnExit(directoryNoIndex);
        final Path withoutIndex = writeSamGz(records, directoryNoIndex, false, null);

        // The files should have the same content: the stronger statement is byte-identical files
        Assert.assertEquals(Files.readAllBytes(withIndex), Files.readAllBytes(withoutIndex));
        IOUtil.recursiveDelete(directory);
        IOUtil.recursiveDelete(directoryNoIndex);
    }
}
