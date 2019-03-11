package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.CRAIEntry;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.zip.GZIPOutputStream;

public class SamIndexesTest extends HtsjdkTest {

    @Test
    public void testEmptyBai() throws IOException {
        final File baiFile = File.createTempFile("test", ".bai");
        baiFile.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(baiFile);
        fos.write(SamIndexes.BAI.magic);
        fos.close();


        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(SamIndexes.BAI.magic);
        baos.close();

        final InputStream inputStream = SamIndexes.asBaiStreamOrNull(new ByteArrayInputStream(baos.toByteArray()), null);
        for (final byte b : SamIndexes.BAI.magic) {
            Assert.assertEquals(inputStream.read(), 0xFF & b);
        }
    }

    @Test
    public void testEmptyCsi() throws IOException {
        final File csiFile = File.createTempFile("test", ".csi");
        csiFile.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(csiFile);
        fos.write(SamIndexes.CSI.magic);
        fos.close();


        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        baos.write(SamIndexes.CSI.magic);
        baos.close();

        final InputStream inputStream = SamIndexes.asBaiStreamOrNull(new ByteArrayInputStream(baos.toByteArray()), null);
        for (final byte b : SamIndexes.CSI.magic) {
            Assert.assertEquals(inputStream.read(), 0xFF & b);
        }
    }


    @Test(expectedExceptions = NullPointerException.class)
    public void testCraiRequiresDictionary() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.close();

        SamIndexes.asBaiStreamOrNull(new ByteArrayInputStream(baos.toByteArray()), null);
    }

    @Test
    public void testEmptyCraiReadAsBai() throws IOException {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final GZIPOutputStream gos = new GZIPOutputStream(baos);
        gos.close();

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));
        final InputStream inputStream = SamIndexes.asBaiStreamOrNull(new ByteArrayInputStream(baos.toByteArray()), dictionary);
        for (final byte b : SamIndexes.BAI.magic) {
            Assert.assertEquals(inputStream.read(), 0xFF & b);
        }
    }

    @Test
    public void testCraiInMemory() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final CRAIEntry entry = new CRAIEntry(0, 1, 2, 5, 3, 4);
        final CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(baos, header, Collections.singleton(entry));
        indexer.finish();
        baos.close();

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final InputStream baiStream = SamIndexes.asBaiStreamOrNull(new ByteArrayInputStream(baos.toByteArray()), dictionary);
        Assert.assertNotNull(baiStream);

        baos = new ByteArrayOutputStream();
        IOUtil.copyStream(baiStream, baos);
        final CachingBAMFileIndex bamIndex = new CachingBAMFileIndex(new SeekableMemoryStream(baos.toByteArray(), null), dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(entry.getSequenceId(), entry.getAlignmentStart(), entry.getAlignmentStart());
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.getContainerStartByteOffset());
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }

    @Test
    public void testCraiFromFile() throws IOException {
        final File file = File.createTempFile("test", ".crai");
        file.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(file);

        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final CRAIEntry entry = new CRAIEntry(0, 1, 2, 5, 3, 4);
        final CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(fos, header, Collections.singleton(entry));
        indexer.finish();
        fos.close();

        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final SeekableStream baiStream = SamIndexes.asBaiSeekableStreamOrNull(new SeekableFileStream(file), dictionary);
        Assert.assertNotNull(baiStream);

        final CachingBAMFileIndex bamIndex = new CachingBAMFileIndex(baiStream, dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(entry.getSequenceId(), entry.getAlignmentStart(), entry.getAlignmentStart());
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.getContainerStartByteOffset());
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testOpenIndexFileAsBaiOrNull_NPE() throws IOException {
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));
        Assert.assertNull(SamIndexes.openIndexFileAsBaiOrNull(null, dictionary));
    }

    @Test
    public void testOpenIndexFileAsBaiOrNull_ReturnsNull() throws IOException {
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));
        File file = File.createTempFile("test", ".notbai");
        file.deleteOnExit();
        Assert.assertNull(SamIndexes.openIndexFileAsBaiOrNull(file, dictionary));
        file.delete();

        file = File.createTempFile("test", ".notcrai");
        file.deleteOnExit();
        Assert.assertNull(SamIndexes.openIndexFileAsBaiOrNull(file, dictionary));
        file.delete();
    }

    @Test
    public void testOpenIndexUrlAsBaiOrNull() throws IOException {
        final SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
        dictionary.addSequence(new SAMSequenceRecord("1", 100));

        final File file = File.createTempFile("test", ".crai");
        file.deleteOnExit();
        final FileOutputStream fos = new FileOutputStream(file);
        SAMFileHeader header = new SAMFileHeader();
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        final CRAIEntry entry = new CRAIEntry(0, 1, 2, 5, 3, 4);
        final CRAMCRAIIndexer indexer = new CRAMCRAIIndexer(fos, header, Collections.singleton(entry));
        indexer.finish();
        fos.close();

        final InputStream baiStream = SamIndexes.openIndexUrlAsBaiOrNull(file.toURI().toURL(), dictionary);
        Assert.assertNotNull(baiStream);

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        IOUtil.copyStream(baiStream, baos);
        final CachingBAMFileIndex bamIndex = new CachingBAMFileIndex(new SeekableMemoryStream(baos.toByteArray(), null), dictionary);
        final BAMFileSpan span = bamIndex.getSpanOverlapping(entry.getSequenceId(), entry.getAlignmentStart(), entry.getAlignmentStart());
        Assert.assertNotNull(span);
        final long[] coordinateArray = span.toCoordinateArray();
        Assert.assertEquals(coordinateArray.length, 2);
        Assert.assertEquals(coordinateArray[0] >> 16, entry.getContainerStartByteOffset());
        Assert.assertEquals(coordinateArray[1] & 0xFFFF, 1);
    }
}
