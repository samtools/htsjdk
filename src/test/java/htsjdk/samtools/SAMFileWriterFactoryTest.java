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

import htsjdk.samtools.util.IOUtil;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;


public class SAMFileWriterFactoryTest {

    private static final File TEST_DATA_DIR = new File("testdata/htsjdk/samtools");

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
   
   private void fillSmallBam(SAMFileWriter writer) {
       final SAMRecordSetBuilder builder = new SAMRecordSetBuilder();
       builder.addUnmappedFragment("HiMom!");
       for (final SAMRecord rec: builder.getRecords()) writer.addAlignment(rec);
   }    
   
}
