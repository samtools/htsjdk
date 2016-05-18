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

import htsjdk.samtools.SAMException;
import htsjdk.samtools.util.CloserUtil;
import htsjdk.samtools.util.StringUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Test the indexed fasta sequence file reader.
 */
public class IndexedFastaSequenceFileTest{
    private static File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools/reference");
    private static File SEQUENCE_FILE = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.fasta");
    private static File SEQUENCE_FILE_NODICT = new File(TEST_DATA_DIR,"Homo_sapiens_assembly18.trimmed.nodict.fasta");

    private final String firstBasesOfChrM = "GATCACAGGTCTATCACCCT";
    private final String extendedBasesOfChrM = "GATCACAGGTCTATCACCCTATTAACCACTCACGGGAGCTCTCCATGCAT" +
                                               "TTGGTATTTTCGTCTGGGGGGTGTGCACGCGATAGCATTGCGAGACGCTG" +
                                               "GAGCCGGAGCACCCTATGTCGCAGTATCTGTCTTTGATTCCTGCCTCATT";
    private final String lastBasesOfChr20 = "ttgtctgatgctcatattgt";
    private final int CHR20_LENGTH = 1000000;

    @DataProvider(name="homosapiens")
    public Object[][] provideSequenceFile() throws FileNotFoundException {
        return new Object[][] { new Object[]
                { new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE_NODICT) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },
                { new IndexedFastaSequenceFile(SEQUENCE_FILE_NODICT.toPath()) }};
    }

    @DataProvider(name="comparative")
    public Object[][] provideOriginalAndNewReaders() throws FileNotFoundException {
        return new Object[][] {
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE, true),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.toPath()),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },
                new Object[] { ReferenceSequenceFileFactory.getReferenceSequenceFile(SEQUENCE_FILE.toPath(), true),
                                               new IndexedFastaSequenceFile(SEQUENCE_FILE.toPath()) },};
    }

    @Test(dataProvider="homosapiens")
    public void testOpenFile(IndexedFastaSequenceFile sequenceFile) {
        long startTime = System.currentTimeMillis();
        Assert.assertNotNull(sequenceFile);
        long endTime = System.currentTimeMillis();
        CloserUtil.close(sequenceFile);

        System.err.printf("testOpenFile runtime: %dms%n", (endTime - startTime)) ;
    }

    @Test(dataProvider="homosapiens")
    public void testFirstSequence(IndexedFastaSequenceFile sequenceFile) {
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
    public void testFirstSequenceExtended(IndexedFastaSequenceFile sequenceFile) {
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
    public void testReadStartingInCenterOfFirstLine(IndexedFastaSequenceFile sequenceFile) {
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
    public void testReadStartingInCenterOfMiddleLine(IndexedFastaSequenceFile sequenceFile) {
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
    public void testFirstCompleteContigRead(ReferenceSequenceFile originalSequenceFile, IndexedFastaSequenceFile sequenceFile) {
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
    public void testReadThroughEndOfContig(IndexedFastaSequenceFile sequenceFile) {
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
    public void testReadPastEndOfContig(IndexedFastaSequenceFile sequenceFile) {
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
    public void testLastCompleteContigRead(ReferenceSequenceFile originalSequenceFile, IndexedFastaSequenceFile sequenceFile) {
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
    public void testLastOfChr20(IndexedFastaSequenceFile sequenceFile) {
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
    public void testFirstElementOfIterator(ReferenceSequenceFile originalSequenceFile,IndexedFastaSequenceFile sequenceFile) {
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
    public void testNextElementOfIterator(ReferenceSequenceFile originalSequenceFile, IndexedFastaSequenceFile sequenceFile) {
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
    public void testReset(ReferenceSequenceFile originalSequenceFile, IndexedFastaSequenceFile sequenceFile) {
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
}
