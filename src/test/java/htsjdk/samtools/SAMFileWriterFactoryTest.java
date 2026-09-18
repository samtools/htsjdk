/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import static htsjdk.samtools.SamReader.Type.*;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Md5CalculatingOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.zip.DeflaterFactory;
import htsjdk.utils.SamtoolsTestUtils;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.Deflater;
import org.testng.Assert;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

@SuppressWarnings("EmptyTryBlock")
public class SAMFileWriterFactoryTest extends HtsjdkTest {

    private static final Path TEST_DATA_DIR = Paths.get("src/test/resources/htsjdk/samtools");

    /**
     * PIC-442Confirm that writing to a special file does not cause exception when writing additional files.
     */
    @Test(groups = {"unix"})
    public void specialFileWriterTest() {
        createSmallBam(Paths.get("/dev/null"));
    }

    @Test()
    public void ordinaryFileWriterTest() throws Exception {
        final Path outputPath = Files.createTempFile("tmp.", FileExtensions.BAM);
        Files.delete(outputPath);
        outputPath.toFile().deleteOnExit();
        createSmallBam(outputPath);
        final Path indexPath = SamFiles.findIndex(outputPath);
        indexPath.toFile().deleteOnExit();
        final Path md5Path = outputPath.resolveSibling(outputPath.getFileName().toString() + ".md5");
        md5Path.toFile().deleteOnExit();
        Assert.assertTrue(Files.size(outputPath) > 0);
        Assert.assertTrue(Files.size(indexPath) > 0);
        Assert.assertTrue(Files.size(md5Path) > 0);
    }

    @Test()
    public void ordinaryPathWriterTest() throws Exception {
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            final Path outputPath = jimfs.getPath("ordinaryPathWriterTest" + FileExtensions.BAM);
            createSmallBam(outputPath);
            final Path indexPath = SamFiles.findIndex(outputPath);
            final Path md5File = IOUtil.addExtension(outputPath, ".md5");
            Assert.assertTrue(Files.size(outputPath) > 0);
            Assert.assertTrue(Files.size(indexPath) > 0);
            Assert.assertTrue(Files.size(md5File) > 0);
        }
    }

    @Test()
    public void pathWriterFailureMentionsCause() throws Exception {
        try {
            final Path outputPath = Paths.get("nope://no.txt");
            createSmallBam(outputPath);
            Assert.fail("Should have thrown a RuntimeIOException");
        } catch (RuntimeIOException expected) {
            Assert.assertTrue(expected.getCause().toString().contains("NoSuchFileException"));
        }
    }

    @Test(description = "create a BAM in memory,  should start with GZipInputStream.GZIP_MAGIC")
    public void inMemoryBam() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        createSmallBamToOutputStream(os, true);
        os.flush();
        os.close();
        final byte blob[] = os.toByteArray();
        Assert.assertTrue(blob.length > 2);
        final int head = ((int) blob[0] & 0xff) | ((blob[1] << 8) & 0xff00);
        Assert.assertTrue(java.util.zip.GZIPInputStream.GZIP_MAGIC == head);
    }

    @Test(description = "create a SAM in memory,  should start with '@HD'")
    public void inMemorySam() throws Exception {
        final ByteArrayOutputStream os = new ByteArrayOutputStream();
        createSmallBamToOutputStream(os, false);
        os.flush();
        os.close();
        final String sam = new String(os.toByteArray());
        Assert.assertFalse(sam.isEmpty());
        Assert.assertTrue(sam.startsWith("@HD\t"), "SAM: bad prefix");
    }

    @Test(
            description =
                    "Read and then write SAM to verify header attribute ordering does not change depending on JVM version")
    public void samRoundTrip() throws Exception {
        final Path input = TEST_DATA_DIR.resolve("roundtrip.sam");

        final Path outputPath = Files.createTempFile("roundtrip-out", ".sam");
        Files.delete(outputPath);
        outputPath.toFile().deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
                SAMFileWriter writer =
                        factory.makeSAMWriter(reader.getFileHeader(), false, Files.newOutputStream(outputPath))) {
            for (SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }

        final String originalsam;
        try (InputStream is = Files.newInputStream(input)) {
            originalsam = IOUtil.readFully(is);
        }

        final String writtenSam;
        try (InputStream is = Files.newInputStream(outputPath)) {
            writtenSam = IOUtil.readFully(is);
        }

        Assert.assertEquals(writtenSam, originalsam);
    }

    @Test(description = "Write SAM records with null SAMFileHeader")
    public void samNullHeaderRoundTrip() throws Exception {
        final Path input = TEST_DATA_DIR.resolve("roundtrip.sam");

        final Path outputPath = Files.createTempFile("nullheader-out", ".sam");
        Files.delete(outputPath);
        outputPath.toFile().deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
                SAMFileWriter writer =
                        factory.makeSAMWriter(reader.getFileHeader(), false, Files.newOutputStream(outputPath))) {
            for (SAMRecord rec : reader) {
                rec.setHeader(null);
                writer.addAlignment(rec);
            }
        }

        final String originalsam;
        try (final InputStream is = Files.newInputStream(input)) {
            originalsam = IOUtil.readFully(is);
        }

        final String writtenSam;
        try (final InputStream is = Files.newInputStream(outputPath)) {
            writtenSam = IOUtil.readFully(is);
        }

        Assert.assertEquals(writtenSam, originalsam);
    }

    private void createSmallBam(final Path outputPath) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(true);
        factory.setCreateMd5File(true);
        final SAMFileHeader header = new SAMFileHeader();
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        try (SAMFileWriter writer = factory.makeBAMWriter(header, false, outputPath)) {
            fillSmallBam(writer);
        }
    }

    private void createSmallBamToOutputStream(final OutputStream outputStream, boolean binary) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(false);
        factory.setCreateMd5File(false);
        final SAMFileHeader header = new SAMFileHeader();
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));

        try (SAMFileWriter writer = (binary
                ? factory.makeBAMWriter(header, false, outputStream)
                : factory.makeSAMWriter(header, false, outputStream))) {
            fillSmallBam(writer);
        }
    }

    @Test(description = "check that factory settings are propagated towriter")
    public void testFactorySettings() throws Exception {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(false);
        factory.setCreateMd5File(false);
        final Path wontBeUsed = Paths.get("wontBeUsed.tmp");
        final int maxRecsInRam = 271828;
        factory.setMaxRecordsInRam(maxRecsInRam);
        factory.setTempDirectory(wontBeUsed);
        final SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        try (final SAMFileWriter writer = factory.makeBAMWriter(header, false, new ByteArrayOutputStream())) {
            Assert.assertEquals(maxRecsInRam, ((SAMFileWriterImpl) writer).getMaxRecordsInRam());
            Assert.assertEquals(wontBeUsed, ((SAMFileWriterImpl) writer).getTempDirectory());
        }
        try (final SAMFileWriter writer = factory.makeSAMWriter(header, false, new ByteArrayOutputStream())) {
            Assert.assertEquals(maxRecsInRam, ((SAMFileWriterImpl) writer).getMaxRecordsInRam());
            Assert.assertEquals(wontBeUsed, ((SAMFileWriterImpl) writer).getTempDirectory());
        }
    }

    private int fillSmallBam(final SAMFileWriter writer) {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
        builder.addUnmappedFragment("HiMom!");
        int numRecs = builder.getRecords().size();
        for (final SAMRecord rec : builder.getRecords()) {
            writer.addAlignment(rec);
        }
        return numRecs;
    }

    private Path prepareOutputFileWithSuffix(final String suffix) throws IOException {
        final Path outputPath = Files.createTempFile("tmp.", suffix);
        Files.delete(outputPath);
        outputPath.toFile().deleteOnExit();
        return outputPath;
    }

    //  Create a writer factory that creates and index and md5 file and set the header to coord sorted
    private SAMFileWriterFactory createWriterFactoryWithOptions(final SAMFileHeader header) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(true);
        factory.setCreateMd5File(true);
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        header.addReadGroup(new SAMReadGroupRecord("1"));
        return factory;
    }

    private void verifyWriterOutput(
            final Path outputPath,
            final ReferenceSource refSource,
            final int nRecs,
            final boolean verifySupplementalFiles)
            throws IOException {
        // The format-magic checks below open the file with SeekableFileStream, which only supports the
        // default (local) filesystem, and rely on the file name carrying a proper format extension. The
        // jimfs-based tests deliberately use bare extension names (no dot), so restrict these checks to
        // the default filesystem; the reader round-trip and supplemental-file checks below still validate
        // the non-default-filesystem cases.
        if (outputPath.getFileSystem() == FileSystems.getDefault()) {
            if (outputPath.getFileName().toString().endsWith(SamReader.Type.CRAM_TYPE.fileExtension())) {
                Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(outputPath))));
            }

            if (outputPath.getFileName().toString().endsWith(SamReader.Type.BAM_TYPE.fileExtension())) {
                Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(outputPath))));
            }

            if (outputPath.getFileName().toString().endsWith(SamReader.Type.SAM_TYPE.fileExtension())) {
                byte[] head = new byte[3];
                new DataInputStream(Files.newInputStream(outputPath)).readFully(head);
                Assert.assertEquals("@HD".getBytes(), head);
            }
        }

        if (verifySupplementalFiles) {
            final Path indexPath = SamFiles.findIndex(outputPath);
            IOUtil.deleteOnExit(indexPath);
            final Path md5Path =
                    outputPath.resolveSibling(outputPath.getFileName().toString() + ".md5");
            IOUtil.deleteOnExit(md5Path);
            Assert.assertTrue(Files.size(indexPath) > 0);
            Assert.assertTrue(Files.size(md5Path) > 0);
        }

        SamReaderFactory factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT);
        if (refSource != null) {
            factory.referenceSource(refSource);
        }
        try (final SamReader reader = factory.open(outputPath)) {

            final SAMRecordIterator it = reader.iterator();
            int count = 0;
            for (; it.hasNext(); it.next()) {
                count++;
            }
            Assert.assertTrue(count == nRecs);
        }
    }

    @DataProvider(name = "bamOrCramWriter")
    public Object[][] bamOrCramWriter() {
        return new Object[][] {
            {
                SamReader.Type.SAM_TYPE.fileExtension(),
            },
            {
                SamReader.Type.BAM_TYPE.fileExtension(),
            },
            {SamReader.Type.CRAM_TYPE.fileExtension()}
        };
    }

    @Test(dataProvider = "bamOrCramWriter")
    public void testMakeWriter(final String extension) throws Exception {
        final Path outputPath = prepareOutputFileWithSuffix("." + extension);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputPath, referencePath)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(
                outputPath,
                new ReferenceSource(referencePath),
                nRecs,
                !SamReader.Type.SAM_TYPE.fileExtension().equals(extension));
    }

    @Test(dataProvider = "bamOrCramWriter")
    public void testMakeWriterPath(String extension) throws Exception {
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            Path outputPath = jimfs.getPath("testMakeWriterPath" + extension);
            Files.deleteIfExists(outputPath);
            final SAMFileHeader header = new SAMFileHeader();
            final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
            final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

            int nRecs;
            try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputPath, referencePath)) {
                nRecs = fillSmallBam(samWriter);
            }
            verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, true);
        }
    }

    @Test(dataProvider = "bamOrCramWriter")
    public void testMakeWriterPathAndReferencePath(String extension) throws Exception {
        final String referenceName = "hg19mini.fasta";
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            Path outputPath = jimfs.getPath("testMakeWriterPath" + extension);
            Files.deleteIfExists(outputPath);
            final SAMFileHeader header = new SAMFileHeader();
            final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
            final Path referencePath = jimfs.getPath(referenceName);
            Files.copy(TEST_DATA_DIR.resolve(referenceName), referencePath);

            int nRecs;
            try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputPath, referencePath)) {
                nRecs = fillSmallBam(samWriter);
            }
            verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, true);
        }
    }

    @Test
    public void testMakeCRAMWriterWithOptions() throws Exception {
        final Path outputPath = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, false, outputPath, referencePath)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, true);
    }

    @Test
    public void testMakeCRAMWriterWithNoReference() throws Exception {
        final Path outputPath = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);

        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, false, outputPath, (Path) null)) {
            fillSmallBam(samWriter);
        }
    }

    @Test
    public void testMakeCRAMWriterIgnoresOptions() throws Exception {
        final Path outputPath = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

        // Note: does not honor factory settings for CREATE_MD5 or CREATE_INDEX.
        final int nRecs;
        try (SAMFileWriter samWriter =
                factory.makeCRAMWriter(header, Files.newOutputStream(outputPath), referencePath)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, false);
    }

    @Test
    public void testMakeCRAMWriterPresortedDefault() throws Exception {
        final Path outputPath = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

        // Defaults to preSorted==true
        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, true, outputPath, referencePath)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, true);
    }

    @Test
    public void testAsync() throws IOException {
        final SAMFileWriterFactory builder = new SAMFileWriterFactory();

        final Path outputPath = prepareOutputFileWithSuffix(FileExtensions.BAM);
        final SAMFileHeader header = new SAMFileHeader();
        final Path referencePath = TEST_DATA_DIR.resolve("hg19mini.fasta");

        try (SAMFileWriter writer = builder.makeWriter(header, false, outputPath, referencePath)) {
            Assert.assertEquals(
                    writer instanceof AsyncSAMFileWriter,
                    Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS,
                    "testAsync default");
        }

        try (SAMFileWriter writer = builder.setUseAsyncIo(true).makeWriter(header, false, outputPath, referencePath)) {
            Assert.assertTrue(writer instanceof AsyncSAMFileWriter, "testAsync option=set");
        }

        try (SAMFileWriter writer = builder.setUseAsyncIo(false).makeWriter(header, false, outputPath, referencePath)) {
            Assert.assertFalse(writer instanceof AsyncSAMFileWriter, "testAsync option=unset");
        }
    }

    @Test
    public void testMakeWriterForSamExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeWriterForSamExtension", "." + SAM_TYPE.fileExtension());
        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter ignored =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpPath, (Path) null)) {}

        try (InputStream fis = Files.newInputStream(tmpPath)) {
            for (byte b : "@HD\tVN:".getBytes()) {
                Assert.assertEquals((byte) (fis.read() & 0xFF), b);
            }
        }
    }

    @Test
    public void testMakeWriterForBamExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeWriterForBamExtension", "." + BAM_TYPE.fileExtension());
        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter samFileWriter =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpPath, (Path) null)) {}

        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpPath))));
    }

    @Test
    public void testMakeWriterForCramExtension() throws IOException {
        final Path cramTmpPath =
                Files.createTempFile("testMakeWriterForCramExtension", "." + CRAM_TYPE.fileExtension());
        cramTmpPath.toFile().deleteOnExit();
        final Path refTmpPath = Files.createTempFile("testMakeWriterForCramExtension", ".fa");
        refTmpPath.toFile().deleteOnExit();
        try (SAMFileWriter ignored =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, cramTmpPath, refTmpPath)) {}

        Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(cramTmpPath))));
    }

    @Test(groups = {"defaultReference"})
    public void testMakeWriterForCramExtensionNoReference() throws IOException {
        // NOTE: This requires an environment variable that is set in the gradle file for the defaultReference test
        // group
        final Path cramTmpPath =
                Files.createTempFile("testMakeWriterForCramExtension", "." + CRAM_TYPE.fileExtension());
        cramTmpPath.toFile().deleteOnExit();
        try (SAMFileWriter samFileWriter =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, cramTmpPath, (Path) null)) {
            fillSmallBam(samFileWriter);
        }
        Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(cramTmpPath))));
    }

    @Test
    public void testMakeWriterForUpperCaseBamExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeWriterForUpperCaseBamExtension", ".BAM");
        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter ignored =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpPath, (Path) null)) {}

        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpPath))));
    }

    @Test
    public void testMakeWriterForUpperCaseCramExtension() throws IOException {
        final Path cramTmpPath = Files.createTempFile("testMakeWriterForUpperCaseCramExtension", ".CRAM");
        cramTmpPath.toFile().deleteOnExit();
        final Path refTmpPath = Files.createTempFile("testMakeWriterForUpperCaseCramExtension", ".fa");
        refTmpPath.toFile().deleteOnExit();
        try (SAMFileWriter ignored =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, cramTmpPath, refTmpPath)) {}

        Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(cramTmpPath))));
    }

    @Test
    public void testIndexOfUpperCaseBamIsNamedAsForLowerCase() throws IOException {
        final Path directory = Files.createTempDirectory("testIndexOfUpperCaseBam");
        final Path bam = directory.resolve("reads.BAM");
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        try (SAMFileWriter writer = new SAMFileWriterFactory()
                .setCreateIndex(true)
                .setCreateMd5File(false)
                .makeWriter(records.getHeader(), true, bam, (Path) null)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        // Listed rather than probed, because a probe succeeds in any case on a case-insensitive filesystem.
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            final List<String> names = new ArrayList<>();
            files.forEach(file -> names.add(file.getFileName().toString()));
            Assert.assertTrue(names.contains("reads.bai"), names.toString());
        }
        Assert.assertNotNull(SamFiles.findIndex(bam));
        IOUtil.recursiveDelete(directory);
    }

    @Test
    public void testMakeWriterForNoExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeWriterForNoExtension", "");
        Assert.assertFalse(tmpPath.getFileName().toString().contains("."));
        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter samFileWriter =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpPath, (Path) null)) {}
        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpPath))));
    }

    @Test
    public void testMakeWriterForUnknownFileExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeWriterForUnknownFileExtension", ".png");
        Assert.assertFalse(tmpPath.getFileName().toString().endsWith(SamReader.Type.CRAM_TYPE.fileExtension()));
        Assert.assertFalse(tmpPath.getFileName().toString().endsWith(SamReader.Type.SAM_TYPE.fileExtension()));
        Assert.assertFalse(tmpPath.getFileName().toString().endsWith(SamReader.Type.BAM_TYPE.fileExtension()));

        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter samFileWriter =
                new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpPath, (Path) null)) {}
        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpPath))));
    }

    @Test
    public void testMakeSamOrBamForCramExtension() throws IOException {
        final Path tmpPath = Files.createTempFile("testMakeSamOrBamForCramExtension", "." + CRAM_TYPE.fileExtension());
        tmpPath.toFile().deleteOnExit();
        try (SAMFileWriter samFileWriter =
                new SAMFileWriterFactory().makeSAMOrBAMWriter(new SAMFileHeader(), true, tmpPath)) {}

        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpPath))));
    }

    @Test
    public void testCloneKeepsTheDeflaterFactory() throws IOException {
        final AtomicInteger deflatersMade = new AtomicInteger();
        final DeflaterFactory countingFactory = new DeflaterFactory() {
            @Override
            public Deflater makeDeflater(final int compressionLevel, final boolean gzipCompatible) {
                deflatersMade.incrementAndGet();
                return super.makeDeflater(compressionLevel, gzipCompatible);
            }
        };
        final SAMFileWriterFactory clone =
                factoryWithoutIndexOrMd5().setDeflaterFactory(countingFactory).clone();

        writeRecords(clone, coordinateSortedRecords(), prepareOutputFileWithSuffix(".bam"));

        Assert.assertTrue(deflatersMade.get() > 0, "the clone did not use the configured deflater factory");
    }

    @Test
    public void testCloneKeepsTheSamFlagFieldOutput() throws IOException {
        final SAMFileWriterFactory clone = factoryWithoutIndexOrMd5()
                .setSamFlagFieldOutput(SamFlagField.HEXADECIMAL)
                .clone();
        final Path output = prepareOutputFileWithSuffix(".sam");

        writeRecords(clone, coordinateSortedRecords(), output);

        final String firstRecord = Files.readAllLines(output).stream()
                .filter(line -> !line.startsWith("@"))
                .findFirst()
                .orElseThrow();
        Assert.assertTrue(firstRecord.split("\t")[1].startsWith("0x"), firstRecord);
    }

    // SAM text written to a name ending in a block-compression extension comes out BGZF-compressed.

    private static final int BGZIP_SAM_RECORD_COUNT = 50;

    private static SAMRecordSetBuilder coordinateSortedRecords() {
        final SAMRecordSetBuilder builder = new SAMRecordSetBuilder(true, SAMFileHeader.SortOrder.coordinate);
        for (int i = 0; i < BGZIP_SAM_RECORD_COUNT; i++) {
            builder.addFrag("read" + i, i % 3, 1000 + 10 * i, false);
        }
        return builder;
    }

    private static void writeRecords(
            final SAMFileWriterFactory factory, final SAMRecordSetBuilder records, final Path path) {
        try (SAMFileWriter writer = factory.makeWriter(records.getHeader(), true, path, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }
    }

    private static SAMFileWriterFactory factoryWithoutIndexOrMd5() {
        return new SAMFileWriterFactory().setCreateIndex(false).setCreateMd5File(false);
    }

    private static List<String> readNames(final Path path) throws IOException {
        final List<String> names = new ArrayList<>();
        try (SamReader reader = SamReaderFactory.makeDefault().open(path)) {
            reader.forEach(record -> names.add(record.getReadName()));
        }
        return names;
    }

    private static String decompressedText(final Path path) throws IOException {
        try (InputStream in = new BlockCompressedInputStream(Files.newInputStream(path))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    public void testSamGzIsBgzfCompressedSamText() throws IOException {
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5(), records, output);

        Assert.assertTrue(IOUtil.isBlockCompressed(output), "not BGZF");
        final String text = decompressedText(output);
        Assert.assertTrue(text.startsWith("@HD\t"), "not SAM text: " + text.substring(0, Math.min(20, text.length())));
        Assert.assertEquals(text.lines().filter(line -> !line.startsWith("@")).count(), BGZIP_SAM_RECORD_COUNT);
    }

    @Test
    public void testSamGzEndsWithBgzfTerminatorBlock() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        Assert.assertEquals(
                BlockCompressedInputStream.checkTermination(output),
                BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK);
    }

    @Test
    public void testSamGzReadsBackAsSam() throws IOException {
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5(), records, output);

        try (SamReader reader = SamReaderFactory.makeDefault().open(output)) {
            Assert.assertEquals(reader.type(), SamReader.Type.SAM_TYPE);
            Assert.assertEquals(
                    reader.getFileHeader().getSequenceDictionary(),
                    records.getHeader().getSequenceDictionary());
        }
        final List<String> expected = new ArrayList<>();
        records.getRecords().forEach(record -> expected.add(record.getReadName()));
        Assert.assertEquals(readNames(output), expected);
    }

    @Test
    public void testSamBgzIsBgzfCompressed() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".sam.bgz");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        Assert.assertTrue(IOUtil.isBlockCompressed(output), "not BGZF");
        Assert.assertEquals(readNames(output).size(), BGZIP_SAM_RECORD_COUNT);
    }

    @Test
    public void testMakeSAMOrBAMWriterWritesSamTextForSamGz() throws IOException {
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        try (SAMFileWriter writer = factoryWithoutIndexOrMd5().makeSAMOrBAMWriter(records.getHeader(), true, output)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        Assert.assertTrue(decompressedText(output).startsWith("@HD\t"));
    }

    @Test
    public void testMakeSAMWriterCompressesForACompressedExtension() throws IOException {
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        final Path output = prepareOutputFileWithSuffix(".gz");
        try (SAMFileWriter writer = factoryWithoutIndexOrMd5().makeSAMWriter(records.getHeader(), true, output)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        Assert.assertTrue(IOUtil.isBlockCompressed(output), "not BGZF");
        Assert.assertEquals(readNames(output).size(), BGZIP_SAM_RECORD_COUNT);
    }

    @Test
    public void testPlainSamStaysUncompressed() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".sam");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        Assert.assertTrue(Files.readString(output).startsWith("@HD\t"));
    }

    @Test
    public void testUpperCaseSamGzIsBgzfCompressedSamText() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".SAM.GZ");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        Assert.assertTrue(IOUtil.isBlockCompressed(output), "not BGZF");
        Assert.assertTrue(decompressedText(output).startsWith("@HD\t"));
    }

    @Test
    public void testUpperCaseSamIsUncompressedSamText() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".SAM");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        Assert.assertTrue(Files.readString(output).startsWith("@HD\t"));
    }

    @Test
    public void testUnsortedInputIsSortedIntoTheCompressedOutput() throws IOException {
        final SAMRecordSetBuilder records = new SAMRecordSetBuilder(false, SAMFileHeader.SortOrder.coordinate);
        records.addFrag("second", 0, 5000, false);
        records.addFrag("first", 0, 100, false);
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        try (SAMFileWriter writer = factoryWithoutIndexOrMd5().makeWriter(records.getHeader(), false, output, null)) {
            records.getRecords().forEach(writer::addAlignment);
        }

        final List<String> names = readNames(output);
        Assert.assertEquals(names.size(), 2);
        Assert.assertEquals(names.get(0), "first");
    }

    @Test
    public void testAsyncWriterProducesACompleteCompressedFile() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5().setUseAsyncIo(true), coordinateSortedRecords(), output);

        Assert.assertEquals(
                BlockCompressedInputStream.checkTermination(output),
                BlockCompressedInputStream.FileTermination.HAS_TERMINATOR_BLOCK);
        Assert.assertEquals(readNames(output).size(), BGZIP_SAM_RECORD_COUNT);
    }

    @Test
    public void testMd5IsOfTheCompressedBytes() throws IOException {
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        final Path md5 = IOUtil.addExtension(output, ".md5");
        md5.toFile().deleteOnExit();
        writeRecords(factoryWithoutIndexOrMd5().setCreateMd5File(true), coordinateSortedRecords(), output);

        final Path recomputed = prepareOutputFileWithSuffix(".md5");
        try (OutputStream out = new Md5CalculatingOutputStream(OutputStream.nullOutputStream(), recomputed)) {
            out.write(Files.readAllBytes(output));
        }
        Assert.assertEquals(Files.readString(md5), Files.readString(recomputed));
    }

    @Test
    public void testHigherCompressionLevelGivesASmallerFile() throws IOException {
        final SAMRecordSetBuilder records = coordinateSortedRecords();
        final Path stored = prepareOutputFileWithSuffix(".sam.gz");
        final Path compressed = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5().setCompressionLevel(0), records, stored);
        writeRecords(factoryWithoutIndexOrMd5().setCompressionLevel(9), records, compressed);

        Assert.assertTrue(
                Files.size(compressed) < Files.size(stored),
                Files.size(compressed) + " is not below " + Files.size(stored));
    }

    @Test
    public void testSamtoolsReadsTheCompressedSam() throws IOException {
        if (!SamtoolsTestUtils.isSamtoolsAvailable()) {
            throw new SkipException("samtools not available on local device");
        }
        final Path output = prepareOutputFileWithSuffix(".sam.gz");
        writeRecords(factoryWithoutIndexOrMd5(), coordinateSortedRecords(), output);

        final String count = SamtoolsTestUtils.executeSamToolsCommand("view -c " + output.toAbsolutePath()).stdout;
        Assert.assertEquals(count.trim(), Integer.toString(BGZIP_SAM_RECORD_COUNT));
    }
}
