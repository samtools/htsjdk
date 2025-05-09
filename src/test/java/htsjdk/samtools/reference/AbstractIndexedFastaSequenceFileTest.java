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

package htsjdk.samtools.reference;

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import htsjdk.HtsjdkTest;
import htsjdk.io.HtsPath;
import htsjdk.io.IOPath;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.GZIIndex;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.Interval;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.samtools.util.StringUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Test the indexed fasta sequence file reader.
 */
public class AbstractIndexedFastaSequenceFileTest extends HtsjdkTest {
    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");
    private static final File SEQUENCE_FILE = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.fasta");
    private static final File SEQUENCE_FILE_INDEX = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.fasta.fai");
    private static final File SEQUENCE_FILE_BGZ = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.fasta.gz");
    private static final File SEQUENCE_FILE_GZI = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.fasta.gz.gzi");
    private static final File SEQUENCE_FILE_NODICT = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.nodict.fasta");
    private static final Path HEADER_WITH_WHITESPACE = new File(TEST_DATA_DIR,"header_with_white_space.fasta").toPath();
    private static final Path HEADER_WITH_EXTRA_WHITESPACE = new File(TEST_DATA_DIR,"header_with_extra_white_space.fasta").toPath();
    private static final Path CRLF = new File(TEST_DATA_DIR,"crlf.fasta").toPath();
    private static final Path HEADER_WITH_WHITESPACE_index = AbstractIndexedFastaSequenceFile.findFastaIndex(HEADER_WITH_WHITESPACE);
    private static final Path CRLF_index = AbstractIndexedFastaSequenceFile.findFastaIndex(CRLF);

    private final String firstBasesOfChrM = "GATCACAGGTCTATCACCCT";
    private final String extendedBasesOfChrM = "GATCACAGGTCTATCACCCTATTAACCACTCACGGGAGCTCTCCATGCAT" +
                                               "TTGGTATTTTCGTCTGGGGGGTGTGCACGCGATAGCATTGCGAGACGCTG" +
                                               "GAGCCGGAGCACCCTATGTCGCAGTATCTGTCTTTGATTCCTGCCTCATT";
    private final String lastBasesOfChr20 = "ttgtctgatgctcatattgt";
    private final int CHR20_LENGTH = 1000000;

    @DataProvider(name="mismatched_indexes")
    public Object[][] provideMismatchedIndexes() {
        return new Object[][]{
                new Object[]{HEADER_WITH_WHITESPACE,CRLF_index},
                new Object[]{CRLF,HEADER_WITH_WHITESPACE_index}
        };
    }


    @DataProvider(name="homosapiens")
    public Object[][] provideSequenceFile() throws FileNotFoundException {
        return new Object[][] { new Object[]
                { new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE_NODICT) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE_NODICT.toPath()) },
                { new BlockCompressedIndexedFastaSequenceFile(SEQUENCE_FILE_BGZ.toPath())}};
    }

    @DataProvider(name="comparative")
    public Object[][] provideOriginalAndNewReaders() throws FileNotFoundException {
        GZIIndex gziIndex;
        try {
            gziIndex = GZIIndex.loadIndex(SEQUENCE_FILE_GZI.toPath());
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
        return new Object[][] {
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE, true),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.toPath()),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.toPath(), true),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(
                        SEQUENCE_FILE_BGZ),
                                               new BlockCompressedIndexedFastaSequenceFile(
                                                       SEQUENCE_FILE_BGZ.toPath()) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(
                        SEQUENCE_FILE_BGZ, true),
                                               new BlockCompressedIndexedFastaSequenceFile(
                                                       SEQUENCE_FILE_BGZ.toPath()) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE_BGZ),
                                                new BlockCompressedIndexedFastaSequenceFile(
                                                        SEQUENCE_FILE_BGZ.getAbsolutePath(), new SeekableFileStream(SEQUENCE_FILE_BGZ),
                                                        new FastaSequenceIndex(new FileInputStream(SEQUENCE_FILE_INDEX)), null,
                                                        gziIndex) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.getAbsolutePath(),
                                                       new SeekableFileStream(SEQUENCE_FILE), new FastaSequenceIndex(new FileInputStream(SEQUENCE_FILE_INDEX))),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.getAbsolutePath(), new SeekableFileStream(SEQUENCE_FILE),
                                                       new FastaSequenceIndex(new FileInputStream(SEQUENCE_FILE_INDEX)), null) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.getAbsolutePath(),
                                                       new SeekableFileStream(SEQUENCE_FILE), new FastaSequenceIndex(new FileInputStream(SEQUENCE_FILE_INDEX)), null, true),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.getAbsolutePath(), new SeekableFileStream(SEQUENCE_FILE),
                                                       new FastaSequenceIndex(new FileInputStream(SEQUENCE_FILE_INDEX)), null) },
        };
    }

    @Test(dataProvider="homosapiens")
    public void testOpenFile(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        Assert.assertNotNull(sequenceFile);
        long endTime = System.currentTimeMillis();
        CloserUtil.close(sequenceFile);

        System.err.printf("testOpenFile runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens")
    public void testFirstSequence(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",1,firstBasesOfChrM.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),firstBasesOfChrM,"First n bases of chrM are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testFirstSequence runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens")
    public void testSubsequenceAtLocatable(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt(new Interval("chrM",1,firstBasesOfChrM.length()));
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),firstBasesOfChrM,"First n bases of chrM are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testSubsequenceAtLocatable runtime: %dms%n", (endTime - startTime)) ;
    }
    
    @Test(dataProvider="homosapiens")
    public void testFirstSequenceExtended(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",1,extendedBasesOfChrM.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),extendedBasesOfChrM,"First n bases of chrM are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testFirstSequenceExtended runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens")
    public void testReadStartingInCenterOfFirstLine(AbstractIndexedFastaSequenceFile sequenceFile) {
        final int bytesToChopOff = 5;
        String truncated = extendedBasesOfChrM.substring(bytesToChopOff);

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",
                                                                   bytesToChopOff + 1,
                                                                   bytesToChopOff + truncated.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),truncated,"First n bases of chrM are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testReadStartingInCenterOfFirstLine runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens")
    public void testReadStartingInCenterOfMiddleLine(AbstractIndexedFastaSequenceFile sequenceFile) {
        final int bytesToChopOff = 120;
        String truncated = extendedBasesOfChrM.substring(bytesToChopOff);

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chrM",
                                                                   bytesToChopOff + 1,
                                                                   bytesToChopOff + truncated.length());
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),truncated,"First n bases of chrM are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testReadStartingInCenterOfMiddleLine runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="comparative")
    public void testFirstCompleteContigRead(ReferenceSequenceFile originalSequenceFile, AbstractIndexedFastaSequenceFile sequenceFile) {
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSequence("chrM");
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),StringUtil.bytesToString(expectedSequence.getBases()),"chrM is incorrect");

        CloserUtil.close(originalSequenceFile);
        CloserUtil.close(sequenceFile);

        System.err.printf("testFirstCompleteContigRead runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens",expectedExceptions=SAMException.class)
    public void testReadThroughEndOfContig(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        try {
            sequenceFile.getSubsequenceAt("chrM",16500,16600);
        }
        finally {
            long endTime = System.currentTimeMillis();

            CloserUtil.close(sequenceFile);

            System.err.printf("testReadThroughEndOfContig runtime: %dms%n", (endTime - startTime)) ;
        }
    }

    @Test(dataProvider="homosapiens",expectedExceptions=SAMException.class)
    public void testReadPastEndOfContig(AbstractIndexedFastaSequenceFile sequenceFile) {
         long startTime = System.currentTimeMillis();
         try {
             sequenceFile.getSubsequenceAt("chrM",16800,16900);
         }
         finally {
             long endTime = System.currentTimeMillis();

             CloserUtil.close(sequenceFile);

             System.err.printf("testReadPastEndOfContig runtime: %dms%n", (endTime - startTime)) ;
         }
     }

    @Test(dataProvider="comparative")
    public void testLastCompleteContigRead(ReferenceSequenceFile originalSequenceFile, AbstractIndexedFastaSequenceFile sequenceFile) {
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();
        while( !expectedSequence.getName().equals("chr20") )
            expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSequence("chr20");
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chr20","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),1,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),StringUtil.bytesToString(expectedSequence.getBases()),"chrX_random is incorrect");

        CloserUtil.close(originalSequenceFile);
        CloserUtil.close(sequenceFile);

        System.err.printf("testLastCompleteContigRead runtime: %dms%n", (endTime - startTime)) ;
    }


    @Test(dataProvider="homosapiens")
    public void testLastOfChr20(AbstractIndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.getSubsequenceAt("chr20",
                                                                   CHR20_LENGTH - lastBasesOfChr20.length()+1,
                                                                   CHR20_LENGTH);
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chr20","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),1,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),lastBasesOfChr20,"First n bases of chr1 are incorrect");

        CloserUtil.close(sequenceFile);

        System.err.printf("testFirstOfChr1 runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="comparative")
    public void testFirstElementOfIterator(ReferenceSequenceFile originalSequenceFile, AbstractIndexedFastaSequenceFile sequenceFile) {
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(), "chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(), 0,"Sequence contig index is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),StringUtil.bytesToString(expectedSequence.getBases()),"chrM is incorrect");

        CloserUtil.close(originalSequenceFile);
        CloserUtil.close(sequenceFile);

        System.err.printf("testFirstElementOfIterator runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="comparative")
    public void testNextElementOfIterator(ReferenceSequenceFile originalSequenceFile, AbstractIndexedFastaSequenceFile sequenceFile) {
        // Skip past the first one and load the second one.
        originalSequenceFile.nextSequence();
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        sequenceFile.nextSequence();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chr20","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),1,"Sequence contig index is not correct");
        Assert.assertEquals(sequence.length(),expectedSequence.length(),"Sequence size is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),StringUtil.bytesToString(expectedSequence.getBases()),"chr1 is incorrect");

        CloserUtil.close(originalSequenceFile);
        CloserUtil.close(sequenceFile);

        System.err.printf("testNextElementOfIterator runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="comparative")
    public void testReset(ReferenceSequenceFile originalSequenceFile, AbstractIndexedFastaSequenceFile sequenceFile) {
        // Skip past the first one and load the second one.
        ReferenceSequence expectedSequence = originalSequenceFile.nextSequence();

        long startTime = System.currentTimeMillis();
        sequenceFile.nextSequence();
        sequenceFile.nextSequence();
        sequenceFile.reset();
        ReferenceSequence sequence = sequenceFile.nextSequence();
        long endTime = System.currentTimeMillis();

        Assert.assertEquals(sequence.getName(),"chrM","Sequence contig is not correct");
        Assert.assertEquals(sequence.getContigIndex(),0,"Sequence contig index is not correct");
        Assert.assertEquals(sequence.length(),expectedSequence.length(), "Sequence size is not correct");
        Assert.assertEquals(StringUtil.bytesToString(sequence.getBases()),StringUtil.bytesToString(expectedSequence.getBases()),"chrM is incorrect");

        CloserUtil.close(originalSequenceFile);
        CloserUtil.close(sequenceFile);

        System.err.printf("testReset runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(expectedExceptions = FileNotFoundException.class)
    public void testMissingFile() throws Exception {
        new IndexedFastaSequenceFile(new File(TEST_DATA_DIR, "non-existent.fasta"));
        Assert.fail("FileNotFoundException should have been thrown");
    }

    @Test(expectedExceptions = SAMException.class)
    public void testBadInputForIndexedFastaSequenceFile() throws Exception {
        new IndexedFastaSequenceFile(SEQUENCE_FILE_BGZ);
    }

    @Test(expectedExceptions = SAMException.class)
    public void testBadInputForBlockCompressedIndexedFastaSequenceFile() throws Exception {
        new BlockCompressedIndexedFastaSequenceFile(SEQUENCE_FILE.toPath());
    }

    @Test
    public void testCanCreateBlockCompressedIndexedWithSpecifiedGZIAndDict() throws IOException {
        final Path moved = Files.createTempFile("moved", ".fasta.gz");
        Files.copy(SEQUENCE_FILE_BGZ.toPath(), moved, StandardCopyOption.REPLACE_EXISTING);
        IOUtil.deleteOnExit(moved);
        try (ReferenceSequenceFile withNoAdacentIndex = new BlockCompressedIndexedFastaSequenceFile(moved, new FastaSequenceIndex(SEQUENCE_FILE_INDEX), GZIIndex.loadIndex(SEQUENCE_FILE_GZI.toPath()));
             ReferenceSequenceFile withFilesAdjacent = new BlockCompressedIndexedFastaSequenceFile(SEQUENCE_FILE_BGZ.toPath())) {
            Assert.assertEquals(withNoAdacentIndex.getSubsequenceAt("chrM", 100, 1000).getBases(),
                    withFilesAdjacent.getSubsequenceAt("chrM", 100, 1000).getBases());
        }
    }

    //test for IndexedFastaSequenceFile (non-gzipped)
    @Test
    public void testIndexedFastaSequenceFileFromNio() throws IOException {
        final String dataDir = "src/test/resources/htsjdk/samtools/reference";
        final IOPath fastaFile = new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta").getAbsolutePath());
        final IOPath indexFile = new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.fai").getAbsolutePath());;

        // move everything to a jimfs NIO file system so that each file is in a separate directory so it is in
        // a directory by itself, so we can catch any downstream code that makes assumptions that the index
        // files are siblings of the fasta in the same directory
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {

            // move the fasta
            final Path fastaDir = jimfs.getPath("fastaDir");
            final Path nioFastaDir = Files.createDirectory(fastaDir);
            Assert.assertEquals(nioFastaDir, fastaDir);
            final IOPath remoteFasta = copyToRemotePath(fastaFile, nioFastaDir);

            // move the index file into a separate dir
            final Path indexDir = jimfs.getPath("indexDir");
            final Path nioIndexDir = Files.createDirectory(indexDir);
            Assert.assertEquals(nioIndexDir, indexDir);
            final IOPath remoteIndex = copyToRemotePath(indexFile, nioIndexDir);

            final FastaSequenceIndex fsi = new FastaSequenceIndex(remoteIndex.toPath());
            final IndexedFastaSequenceFile ifsf = new IndexedFastaSequenceFile(remoteFasta, null, fsi);
            final ReferenceSequence rs = ifsf.getSubsequenceAt("chrM", 4, 10);
            Assert.assertEquals(rs.getBaseString(), "CACAGGT");
        }
    }

    private static IOPath copyToRemotePath(IOPath file, Path remoteDir) throws IOException {
        return new HtsPath(
                Files.copy(
                        file.toPath(),
                        remoteDir.resolve(file.getBaseName().get() + file.getExtension().get())).toUri().toString());
    }

    @Test
    public void testBlockCompressedIndexedFastaSequenceFileFromNio() throws IOException {
        final String dataDir = "src/test/resources/htsjdk/samtools/reference";
        final IOPath fastaFile = new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.gz").getAbsolutePath());
        final IOPath indexFile = new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.fai").getAbsolutePath());;
        final IOPath gziIndexFile = new HtsPath(new File(dataDir, "Homo_sapiens_assembly18.trimmed.fasta.gz.gzi").getAbsolutePath());;

        // move everything to a jimfs NIO file system so that each file is in a separate directory so it is in
        // a directory by iteself, so we can catch any downstream code that makes assumptions that the index
        // files are siblings of the fasta in the same directory
        try (final FileSystem jimfs = Jimfs.newFileSystem(Configuration.unix())) {

            // move the fasta
            final Path fastaDir = jimfs.getPath("fastaDir");
            final Path nioFastaDir = Files.createDirectory(fastaDir);
            Assert.assertEquals(nioFastaDir, fastaDir);
            final IOPath remoteFasta = copyToRemotePath(fastaFile, nioFastaDir);

            // move the index file into a completely separate dir
            final Path indexDir = jimfs.getPath("indexDir");
            final Path nioIndexDir = Files.createDirectory(indexDir);
            Assert.assertEquals(nioIndexDir, indexDir);
            final IOPath remoteIndex = copyToRemotePath(indexFile, nioFastaDir);

            // move the optional gzi index ito yet another separate dir
            final Path gziDir = jimfs.getPath("gziDir");
            final Path nioGZIDir = Files.createDirectory(gziDir);
            Assert.assertEquals(nioGZIDir, gziDir);
            final IOPath remoteGZI = copyToRemotePath(gziIndexFile, nioGZIDir);

            final FastaSequenceIndex fsi = new FastaSequenceIndex(remoteIndex.toPath());
            final BlockCompressedIndexedFastaSequenceFile ifsf = new BlockCompressedIndexedFastaSequenceFile(
                    remoteFasta,
                    null,
                    fsi,
                    GZIIndex.loadIndex(remoteGZI.toPath()));
            final ReferenceSequence rs = ifsf.getSubsequenceAt("chrM", 4, 10);
            Assert.assertEquals(rs.getBaseString(), "CACAGGT");
        }
    }

    @Test(expectedExceptions = IllegalArgumentException.class,
            dataProvider = "mismatched_indexes")
    public void testWrongIndex(Path fasta, Path index) {
        try (IndexedFastaSequenceFile indexedFastaSequenceFile = new IndexedFastaSequenceFile(fasta, new FastaSequenceIndex(index))) {

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }    
    
    @Test
    public void testExtraWhitespace() {
        try (IndexedFastaSequenceFile indexedFastaSequenceFile = 
                     new IndexedFastaSequenceFile(HEADER_WITH_EXTRA_WHITESPACE, 
                             new FastaSequenceIndex(HEADER_WITH_WHITESPACE_index))) {

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
