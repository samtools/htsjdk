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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static htsjdk.samtools.SamReader.Type.*;

@SuppressWarnings("EmptyTryBlock")
public class SAMFileWriterFactoryTest extends HtsjdkTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    /**
     * PIC-442Confirm that writing to a special file does not cause exception when writing additional files.
     */
    @Test(groups = {"unix"})
    public void specialFileWriterTest() {
        createSmallBam(new File("/dev/null"));
    }

    @Test()
    public void ordinaryFileWriterTest() throws Exception {
        final File outputFile = File.createTempFile("tmp.", FileExtensions.BAM);
        outputFile.delete();
        outputFile.deleteOnExit();
        createSmallBam(outputFile);
        final File indexFile = SamFiles.findIndex(outputFile);
        indexFile.deleteOnExit();
        final File md5File = new File(outputFile.getParent(), outputFile.getName() + ".md5");
        md5File.deleteOnExit();
        Assert.assertTrue(outputFile.length() > 0);
        Assert.assertTrue(indexFile.length() > 0);
        Assert.assertTrue(md5File.length() > 0);
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

    @Test(description = "Read and then write SAM to verify header attribute ordering does not change depending on JVM version")
    public void samRoundTrip() throws Exception {
        final File input = new File(TEST_DATA_DIR, "roundtrip.sam");

        final File outputFile = File.createTempFile("roundtrip-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
             SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, new FileOutputStream(outputFile))) {
            for (SAMRecord rec : reader) {
                writer.addAlignment(rec);
            }
        }

        final String originalsam;
        try (InputStream is = new FileInputStream(input)) {
            originalsam = IOUtil.readFully(is);
        }

        final String writtenSam;
        try (InputStream is = new FileInputStream(outputFile)) {
            writtenSam = IOUtil.readFully(is);
        }

        Assert.assertEquals(writtenSam, originalsam);
    }

    @Test(description = "Write SAM records with null SAMFileHeader")
    public void samNullHeaderRoundTrip() throws Exception {
        final File input = new File(TEST_DATA_DIR, "roundtrip.sam");

        final File outputFile = File.createTempFile("nullheader-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        try (SamReader reader = SamReaderFactory.makeDefault().open(input);
             SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, new FileOutputStream(outputFile))) {
            for (SAMRecord rec : reader) {
                rec.setHeader(null);
                writer.addAlignment(rec);
            }
        }

        final String originalsam;
        try (final InputStream is = new FileInputStream(input)) {
            originalsam = IOUtil.readFully(is);
        }

        final String writtenSam;
        try (final InputStream is = new FileInputStream(outputFile)) {
            writtenSam = IOUtil.readFully(is);
        }

        Assert.assertEquals(writtenSam, originalsam);
    }

    private void createSmallBam(final File outputFile) {
        createSmallBam(outputFile.toPath());
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

        try (SAMFileWriter writer = (binary ?
                factory.makeBAMWriter(header, false, outputStream) :
                factory.makeSAMWriter(header, false, outputStream)
        )) {
            fillSmallBam(writer);
        }
    }

    @Test(description = "check that factory settings are propagated towriter")
    public void testFactorySettings() throws Exception {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(false);
        factory.setCreateMd5File(false);
        final File wontBeUsed = new File("wontBeUsed.tmp");
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

    private File prepareOutputFileWithSuffix(final String suffix) throws IOException {
        final File outputFile = File.createTempFile("tmp.", suffix);
        outputFile.delete();
        outputFile.deleteOnExit();
        return outputFile;
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

    private void verifyWriterOutput(final File outputFile, final ReferenceSource refSource, final int nRecs, final boolean verifySupplementalFiles) throws IOException {
        if (outputFile.getName().endsWith(SamReader.Type.CRAM_TYPE.fileExtension())) {
            Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(outputFile))));
        }

        if (outputFile.getName().endsWith(SamReader.Type.BAM_TYPE.fileExtension())) {
            Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(outputFile))));
        }

        if (outputFile.getName().endsWith(SamReader.Type.SAM_TYPE.fileExtension())) {
            byte[] head = new byte[3];
            new DataInputStream(new FileInputStream(outputFile)).readFully(head);
            Assert.assertEquals("@HD".getBytes(), head);
        }

        if (verifySupplementalFiles) {
            final File indexFile = SamFiles.findIndex(outputFile);
            indexFile.deleteOnExit();
            final File md5File = new File(outputFile.getParent(), outputFile.getName() + ".md5");
            md5File.deleteOnExit();
            Assert.assertTrue(indexFile.length() > 0);
            Assert.assertTrue(md5File.length() > 0);
        }

        SamReaderFactory factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT);
        if (refSource != null) {
            factory.referenceSource(refSource);
        }
        try (final SamReader reader = factory.open(outputFile)) {

            final SAMRecordIterator it = reader.iterator();
            int count = 0;
            for (; it.hasNext(); it.next()) {
                count++;
            }
            Assert.assertTrue(count == nRecs);
        }
    }

    private void verifyWriterOutput(Path output, ReferenceSource refSource, int nRecs, boolean verifySupplementalFiles) throws IOException {
        if (verifySupplementalFiles) {
            final Path index = SamFiles.findIndex(output);
            final Path md5File = IOUtil.addExtension(output, ".md5");
            Assert.assertTrue(Files.size(index) > 0);
            Assert.assertTrue(Files.size(md5File) > 0);
        }

        SamReaderFactory factory = SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT);
        if (refSource != null) {
            factory.referenceSource(refSource);
        }
        SamReader reader = factory.open(output);
        SAMRecordIterator it = reader.iterator();
        int count = 0;
        for (; it.hasNext(); it.next()) {
            count++;
        }

        Assert.assertTrue(count == nRecs);
    }

    @DataProvider(name = "bamOrCramWriter")
    public Object[][] bamOrCramWriter() {
        return new Object[][]{
                {SamReader.Type.SAM_TYPE.fileExtension(),},
                {SamReader.Type.BAM_TYPE.fileExtension(),},
                {SamReader.Type.CRAM_TYPE.fileExtension()}
        };
    }

    @Test(dataProvider = "bamOrCramWriter")
    public void testMakeWriter(final String extension) throws Exception {
        final File outputFile = prepareOutputFileWithSuffix("." + extension);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputFile, referenceFile)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, !SamReader.Type.SAM_TYPE.fileExtension().equals(extension));
    }

    @Test(dataProvider = "bamOrCramWriter")
    public void testMakeWriterPath(String extension) throws Exception {
        try (FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {
            Path outputPath = jimfs.getPath("testMakeWriterPath" + extension);
            Files.deleteIfExists(outputPath);
            final SAMFileHeader header = new SAMFileHeader();
            final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
            final Path referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta").toPath();

            int nRecs;
            try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputPath, referenceFile)) {
                nRecs = fillSmallBam(samWriter);
            }
            verifyWriterOutput(outputPath, new ReferenceSource(referenceFile), nRecs, true);
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
            Files.copy(new File(TEST_DATA_DIR, referenceName).toPath(), referencePath);

            int nRecs;
            try (SAMFileWriter samWriter = factory.makeWriter(header, false, outputPath, referencePath)) {
                nRecs = fillSmallBam(samWriter);
            }
            verifyWriterOutput(outputPath, new ReferenceSource(referencePath), nRecs, true);
        }
    }

    @Test
    public void testMakeCRAMWriterWithOptions() throws Exception {
        final File outputFile = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, false, outputFile, referenceFile)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, true);
    }

    // throws an exception since no reference is provided
    @Test(expectedExceptions = IllegalStateException.class)
    public void testMakeCRAMWriterWithNoReference() throws Exception {
        final File outputFile = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);

        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, false, outputFile, null)) {
            fillSmallBam(samWriter);
        }
    }

    @Test
    public void testMakeCRAMWriterIgnoresOptions() throws Exception {
        final File outputFile = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        // Note: does not honor factory settings for CREATE_MD5 or CREATE_INDEX.
        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, new FileOutputStream(outputFile), referenceFile)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, false);
    }

    @Test
    public void testMakeCRAMWriterPresortedDefault() throws Exception {
        final File outputFile = prepareOutputFileWithSuffix("." + FileExtensions.CRAM);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        // Defaults to preSorted==true
        final int nRecs;
        try (SAMFileWriter samWriter = factory.makeCRAMWriter(header, outputFile, referenceFile)) {
            nRecs = fillSmallBam(samWriter);
        }

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, true);
    }

    @Test
    public void testAsync() throws IOException {
        final SAMFileWriterFactory builder = new SAMFileWriterFactory();

        final File outputFile = prepareOutputFileWithSuffix(FileExtensions.BAM);
        final SAMFileHeader header = new SAMFileHeader();
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        try (SAMFileWriter writer = builder.makeWriter(header, false, outputFile, referenceFile)) {
            Assert.assertEquals(writer instanceof AsyncSAMFileWriter, Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS, "testAsync default");
        }

        try (SAMFileWriter writer = builder.setUseAsyncIo(true).makeWriter(header, false, outputFile, referenceFile)) {
            Assert.assertTrue(writer instanceof AsyncSAMFileWriter, "testAsync option=set");
        }

        try (SAMFileWriter writer = builder.setUseAsyncIo(false).makeWriter(header, false, outputFile, referenceFile)) {
            Assert.assertFalse(writer instanceof AsyncSAMFileWriter, "testAsync option=unset");
        }
    }

    @Test
    public void testMakeWriterForSamExtension() throws IOException {
        final File tmpFile = File.createTempFile("testMakeWriterForSamExtension", "." + SAM_TYPE.fileExtension());
        tmpFile.deleteOnExit();
        try (SAMFileWriter ignored = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpFile, null)) {}

        try (FileInputStream fis = new FileInputStream(tmpFile)) {
            for (byte b : "@HD\tVN:".getBytes()) {
                Assert.assertEquals((byte) (fis.read() & 0xFF), b);
            }
        }
    }

    @Test
    public void testMakeWriterForBamExtension() throws IOException {
        final File tmpFile = File.createTempFile("testMakeWriterForBamExtension", "." + BAM_TYPE.fileExtension());
        tmpFile.deleteOnExit();
        try (SAMFileWriter samFileWriter = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpFile, null)) {}

        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpFile))));
    }

    @Test
    public void testMakeWriterForCramExtension() throws IOException {
        final File cramTmpFile = File.createTempFile("testMakeWriterForCramExtension", "." + CRAM_TYPE.fileExtension());
        cramTmpFile.deleteOnExit();
        final File refTmpFile = File.createTempFile("testMakeWriterForCramExtension", ".fa");
        refTmpFile.deleteOnExit();
        try(SAMFileWriter ignored = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, cramTmpFile, refTmpFile)) {}

        Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(cramTmpFile))));
    }

    @Test(groups = {"defaultReference"})
    public void testMakeWriterForCramExtensionNoReference() throws IOException {

        final File cramTmpFile = File.createTempFile("testMakeWriterForCramExtension", "." + CRAM_TYPE.fileExtension());
        cramTmpFile.deleteOnExit();
        try (SAMFileWriter samFileWriter = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, cramTmpFile, null)) {
            fillSmallBam(samFileWriter);
        }
        Assert.assertTrue(SamStreams.isCRAMFile(new BufferedInputStream(new SeekableFileStream(cramTmpFile))));
    }

    @Test
    public void testMakeWriterForNoExtension() throws IOException {
        final File tmpFile = File.createTempFile("testMakeWriterForNoExtension", "");
        Assert.assertFalse(tmpFile.getName().contains("."));
        tmpFile.deleteOnExit();
        try(SAMFileWriter samFileWriter = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpFile, null)) {}
        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpFile))));
    }

    @Test
    public void testMakeWriterForUnknownFileExtension() throws IOException {
        final File tmpFile = File.createTempFile("testMakeWriterForUnknownFileExtension", ".png");
        Assert.assertFalse(tmpFile.getName().endsWith(SamReader.Type.CRAM_TYPE.fileExtension()));
        Assert.assertFalse(tmpFile.getName().endsWith(SamReader.Type.SAM_TYPE.fileExtension()));
        Assert.assertFalse(tmpFile.getName().endsWith(SamReader.Type.BAM_TYPE.fileExtension()));

        tmpFile.deleteOnExit();
        try(SAMFileWriter samFileWriter = new SAMFileWriterFactory().makeWriter(new SAMFileHeader(), true, tmpFile, null)){}
        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpFile))));
    }

    @Test
    public void testMakeSamOrBamForCramExtension() throws IOException {
        final File tmpFile = File.createTempFile("testMakeSamOrBamForCramExtension", "." + CRAM_TYPE.fileExtension());
        tmpFile.deleteOnExit();
        try(SAMFileWriter samFileWriter = new SAMFileWriterFactory().makeSAMOrBAMWriter(new SAMFileHeader(), true, tmpFile)){}

        Assert.assertTrue(SamStreams.isBAMFile(new BufferedInputStream(new SeekableFileStream(tmpFile))));
    }
}
