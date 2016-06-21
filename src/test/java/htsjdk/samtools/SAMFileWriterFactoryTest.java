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

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.*;

public class SAMFileWriterFactoryTest {

    private static final File TEST_DATA_DIR = new File("src/test/resources/htsjdk/samtools");

    /** PIC-442Confirm that writing to a special file does not cause exception when writing additional files. */
    @Test(groups={"unix"})
    public void specialFileWriterTest() {
        createSmallBam(new File("/dev/null"));
    }

    @Test()
    public void ordinaryFileWriterTest() throws Exception {
        final File outputFile = File.createTempFile("tmp.", BamFileIoUtils.BAM_FILE_EXTENSION);
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

    @Test(description="create a BAM in memory,  should start with GZipInputStream.GZIP_MAGIC")
    public void inMemoryBam()  throws Exception  {
    	ByteArrayOutputStream os=new ByteArrayOutputStream();
    	createSmallBamToOutputStream(os,true);
    	os.flush();
    	os.close();
    	byte blob[]=os.toByteArray();
        Assert.assertTrue(blob.length > 2);
        int head = ((int) blob[0] & 0xff) | ((blob[1] << 8 ) & 0xff00 );
        Assert.assertTrue(java.util.zip.GZIPInputStream.GZIP_MAGIC == head);
    }

    @Test(description="create a SAM in memory,  should start with '@HD'")
    public void inMemorySam()  throws Exception  {
    	ByteArrayOutputStream os=new ByteArrayOutputStream();
    	createSmallBamToOutputStream(os,false);
    	os.flush();
    	os.close();
    	String sam=new String(os.toByteArray());
        Assert.assertFalse(sam.isEmpty());
        Assert.assertTrue(sam.startsWith("@HD\t"),"SAM: bad prefix");
    }

    @Test(description="Read and then write SAM to verify header attribute ordering does not change depending on JVM version")
    public void samRoundTrip()  throws Exception  {
        final File input = new File(TEST_DATA_DIR, "roundtrip.sam");

        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        final File outputFile = File.createTempFile("roundtrip-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(outputFile);
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        final SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, os);
        for (SAMRecord rec : reader) {
            writer.addAlignment(rec);
        }
        writer.close();
        os.close();

        InputStream is = new FileInputStream(input);
        String originalsam = IOUtil.readFully(is);
        is.close();

        is = new FileInputStream(outputFile);
        String writtensam = IOUtil.readFully(is);
        is.close();

        Assert.assertEquals(writtensam, originalsam);
    }

    @Test(description="Write SAM records with null SAMFileHeader")
    public void samNullHeaderRoundTrip()  throws Exception  {
        final File input = new File(TEST_DATA_DIR, "roundtrip.sam");

        final SamReader reader = SamReaderFactory.makeDefault().open(input);
        final File outputFile = File.createTempFile("nullheader-out", ".sam");
        outputFile.delete();
        outputFile.deleteOnExit();
        FileOutputStream os = new FileOutputStream(outputFile);
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        final SAMFileWriter writer = factory.makeSAMWriter(reader.getFileHeader(), false, os);
        for (SAMRecord rec : reader) {
            rec.setHeader(null);
            writer.addAlignment(rec);
        }
        writer.close();
        os.close();

        InputStream is = new FileInputStream(input);
        String originalsam = IOUtil.readFully(is);
        is.close();

        is = new FileInputStream(outputFile);
        String writtensam = IOUtil.readFully(is);
        is.close();

        Assert.assertEquals(writtensam, originalsam);
    }

    private void createSmallBam(final File outputFile) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(true);
        factory.setCreateMd5File(true);
        final SAMFileHeader header = new SAMFileHeader();
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        final SAMFileWriter writer = factory.makeBAMWriter(header, false, outputFile);
        fillSmallBam(writer);
        writer.close();
    }


   private void createSmallBamToOutputStream(final OutputStream outputStream,boolean binary) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(false);
        factory.setCreateMd5File(false);
        final SAMFileHeader header = new SAMFileHeader();
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        final SAMFileWriter writer = (binary?
        			factory.makeBAMWriter(header, false, outputStream):
        			factory.makeSAMWriter(header, false, outputStream)
        			);
        fillSmallBam(writer);
        writer.close();
    }
   
   private int fillSmallBam(SAMFileWriter writer) {
       final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
       builder.addUnmappedFragment("HiMom!");
       int numRecs = builder.getRecords().size();
       for (final SAMRecord rec: builder.getRecords()) {
           writer.addAlignment(rec);
       }
       return numRecs;
    }

    private File prepareOutputFile(String extension) throws IOException {
        final File outputFile = File.createTempFile("tmp.", extension);
        outputFile.delete();
        outputFile.deleteOnExit();
        return outputFile;
    }

    //  Create a writer factory that creates and index and md5 file and set the header to coord sorted
    private SAMFileWriterFactory createWriterFactoryWithOptions(SAMFileHeader header) {
        final SAMFileWriterFactory factory = new SAMFileWriterFactory();
        factory.setCreateIndex(true);
        factory.setCreateMd5File(true);
        // index only created if coordinate sorted
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        header.addSequence(new SAMSequenceRecord("chr1", 123));
        header.addReadGroup(new SAMReadGroupRecord("1"));
        return factory;
    }

    private void verifyWriterOutput(File outputFile, ReferenceSource refSource, int nRecs, boolean verifySupplementalFiles) {
        if (verifySupplementalFiles) {
            final File indexFile = SamFiles.findIndex(outputFile);
            indexFile.deleteOnExit();
            final File md5File = new File(outputFile.getParent(), outputFile.getName() + ".md5");
            md5File.deleteOnExit();
            Assert.assertTrue(indexFile.length() > 0);
            Assert.assertTrue(md5File.length() > 0);
        }

        SamReaderFactory factory =  SamReaderFactory.makeDefault().validationStringency(ValidationStringency.LENIENT);
        if (refSource != null) {
            factory.referenceSource(refSource);
        }
        SamReader reader = factory.open(outputFile);
        SAMRecordIterator it = reader.iterator();
        int count = 0;
        for (; it.hasNext(); it.next()) {
            count++;
        }

        Assert.assertTrue(count == nRecs);
    }

    @DataProvider(name="bamOrCramWriter")
    public Object[][] bamOrCramWriter() {
        return new Object[][] {
                { BamFileIoUtils.BAM_FILE_EXTENSION, },
                { CramIO.CRAM_FILE_EXTENSION }
        };
    }

    @Test(dataProvider="bamOrCramWriter")
    public void testMakeWriter(String extension) throws Exception {
        final File outputFile = prepareOutputFile(extension);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        final SAMFileWriter samWriter = factory.makeWriter(header, false, outputFile, referenceFile);
        int nRecs = fillSmallBam(samWriter);
        samWriter.close();

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, true);
    }

    @Test
    public void testMakeCRAMWriterWithOptions() throws Exception {
        final File outputFile = prepareOutputFile(CramIO.CRAM_FILE_EXTENSION);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        final SAMFileWriter samWriter = factory.makeCRAMWriter(header, false, outputFile, referenceFile);
        int nRecs = fillSmallBam(samWriter);
        samWriter.close();

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, true);
    }

    @Test
    public void testMakeCRAMWriterIgnoresOptions() throws Exception {
        final File outputFile = prepareOutputFile(CramIO.CRAM_FILE_EXTENSION);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        // Note: does not honor factory settings for CREATE_MD5 or CREATE_INDEX.
        final SAMFileWriter samWriter = factory.makeCRAMWriter(header, new FileOutputStream(outputFile), referenceFile);
        int nRecs = fillSmallBam(samWriter);
        samWriter.close();

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, false);
    }

    @Test
    public void testMakeCRAMWriterPresortedDefault() throws Exception {
        final File outputFile = prepareOutputFile(CramIO.CRAM_FILE_EXTENSION);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        // Defaults to preSorted==true
        final SAMFileWriter samWriter = factory.makeCRAMWriter(header, outputFile, referenceFile);
        int nRecs = fillSmallBam(samWriter);
        samWriter.close();

        verifyWriterOutput(outputFile, new ReferenceSource(referenceFile), nRecs, true);
    }

    @Test
    public void testAsync() throws IOException {
        final SAMFileWriterFactory builder = new SAMFileWriterFactory();

        final File outputFile = prepareOutputFile(BamFileIoUtils.BAM_FILE_EXTENSION);
        final SAMFileHeader header = new SAMFileHeader();
        final SAMFileWriterFactory factory = createWriterFactoryWithOptions(header);
        final File referenceFile = new File(TEST_DATA_DIR, "hg19mini.fasta");

        SAMFileWriter writer = builder.makeWriter(header, false, outputFile, referenceFile);
        Assert.assertEquals(writer instanceof AsyncSAMFileWriter, Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS, "testAsync default");

        writer = builder.setUseAsyncIo(true).makeWriter(header, false, outputFile, referenceFile);
        Assert.assertTrue(writer instanceof AsyncSAMFileWriter, "testAsync option=set");

        writer = builder.setUseAsyncIo(false).makeWriter(header, false, outputFile, referenceFile);
        Assert.assertFalse(writer instanceof AsyncSAMFileWriter, "testAsync option=unset");
    }
}
