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
package htsjdk.samtools.util;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.FileTruncatedException;
import htsjdk.samtools.util.zip.DeflaterFactory;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.Deflater;

public class BlockCompressedOutputStreamTest extends HtsjdkTest {

    private static final String HTSJDK_TRIBBLE_RESOURCES = "src/test/resources/htsjdk/tribble/";

    @Test
    public void testBasic() throws Exception {
        final File f = File.createTempFile("BCOST.", ".gz");
        f.deleteOnExit();
        final List<String> linesWritten = new ArrayList<>();
        System.out.println("Creating file " + f);
        final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(f);
        String s = "Hi, Mom!\n";
        bcos.write(s.getBytes());
        linesWritten.add(s);
        s = "Hi, Dad!\n";
        bcos.write(s.getBytes());
        linesWritten.add(s);
        bcos.flush();
        final StringBuilder sb = new StringBuilder(BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE * 2);
        s = "1234567890123456789012345678901234567890123456789012345678901234567890\n";
        while (sb.length() <= BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE) {
            sb.append(s);
            linesWritten.add(s);
        }
        bcos.write(sb.toString().getBytes());
        bcos.close();
        final BlockCompressedInputStream bcis = new BlockCompressedInputStream(f);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(bcis));
        String line;
        for(int i = 0; (line = reader.readLine()) != null; ++i) {
            Assert.assertEquals(line + "\n", linesWritten.get(i));
        }
        bcis.close();
        final BlockCompressedInputStream bcis2 = new BlockCompressedInputStream(f);
        int available = bcis2.available();
        Assert.assertFalse(bcis2.endOfBlock(), "Should not be at end of block");
        Assert.assertTrue(available > 0);
        byte[] buffer = new byte[available];
        Assert.assertEquals(bcis2.read(buffer), available, "Should read to end of block");
        Assert.assertTrue(bcis2.endOfBlock(), "Should be at end of block");
        bcis2.close();
        Assert.assertEquals(bcis2.read(buffer), -1, "Should be end of file");
    }

    @DataProvider(name = "seekReadExceptionsData")
    private Object[][] seekReadExceptionsData()
    {
        return new Object[][]{
                {HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.truncated.gz", FileTruncatedException.class,
                        BlockCompressedInputStream.PREMATURE_END_MSG + System.getProperty("user.dir") + "/" +
                                HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.truncated.gz", true, false, false, 0},
                {HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.truncated.hdr.gz", IOException.class,
                        BlockCompressedInputStream.INCORRECT_HEADER_SIZE_MSG + System.getProperty("user.dir") + "/" +
                                HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.truncated.hdr.gz", true, false, false, 0},
                {HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.gz", IOException.class,
                        BlockCompressedInputStream.CANNOT_SEEK_STREAM_MSG, false, true, false, 0},
                {HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.gz", IOException.class,
                        BlockCompressedInputStream.CANNOT_SEEK_CLOSED_STREAM_MSG, false, true, true, 0},
                {HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.gz", IOException.class,
                        BlockCompressedInputStream.INVALID_FILE_PTR_MSG + 1000 + " for " + System.getProperty("user.dir") + "/" +
                                HTSJDK_TRIBBLE_RESOURCES + "vcfexample.vcf.gz", true, true, false, 1000 }
        };
    }

    @Test(dataProvider = "seekReadExceptionsData")
    public void testSeekReadExceptions(final String filePath, final Class c, final String msg, final boolean isFile, final boolean isSeek, final boolean isClosed,
                                       final int pos) throws Exception {

        final BlockCompressedInputStream bcis = isFile ?
                new BlockCompressedInputStream(new File(filePath)) :
                new BlockCompressedInputStream(new FileInputStream(filePath));

        if ( isClosed ) {
            bcis.close();
        }

        boolean haveException = false;
        try {
            if ( isSeek ) {
                bcis.seek(pos);
            } else {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(bcis));
                reader.readLine();
            }
        } catch (final Exception e) {
            if ( e.getClass().equals(c) ) {
                haveException = true;
                Assert.assertEquals(e.getMessage(), msg);
            }
        }

        if ( !haveException ) {
            Assert.fail("Expected " + c.getSimpleName());
        }
    }

    @Test public void testOverflow() throws Exception {
        final File f = File.createTempFile("BCOST.", ".gz");
        f.deleteOnExit();
        final List<String> linesWritten = new ArrayList<>();
        System.out.println("Creating file " + f);
        final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(f);
        Random r = new Random(15555);
        final int INPUT_SIZE = 64 * 1024;
        byte[] input = new byte[INPUT_SIZE];
        r.nextBytes(input);
        bcos.write(input);
        bcos.close();

        final BlockCompressedInputStream bcis = new BlockCompressedInputStream(f);
        byte[] output = new byte[INPUT_SIZE];
        int len;
        int i = 0;
        while ((len = bcis.read(output, 0, output.length)) != -1) {
            for (int j = 0; j < len; j++) {
               Assert.assertEquals(output[j], input[i++]);
            }
        }
        Assert.assertEquals(i, INPUT_SIZE);
        bcis.close();
    }

    // PIC-393 exception closing BGZF stream opened to /dev/null
    // I don't think this will work on Windows, because /dev/null doesn't work
    @Test(groups = "broken")
    public void testDevNull() throws Exception {
        final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream("/dev/null");
        bcos.write("Hi, Mom!".getBytes());
        bcos.close();
    }

    @Test
    public void testCustomDeflater() throws Exception {
        final File f = File.createTempFile("testCustomDeflater.", ".gz");
        f.deleteOnExit();
        System.out.println("Creating file " + f);

        final int[] deflateCalls = {0}; //Note: using and array is a HACK to fool the compiler

        class MyDeflater extends Deflater{
            MyDeflater(int level, boolean gzipCompatible){
                super(level, gzipCompatible);
            }
            @Override
            public int deflate(byte[] b, int off, int len) {
                deflateCalls[0]++;
                return super.deflate(b, off, len);
            }

        }
        final DeflaterFactory myDeflaterFactory= new DeflaterFactory(){
            @Override
            public Deflater makeDeflater(final int compressionLevel, final boolean gzipCompatible) {
                return new MyDeflater(compressionLevel, gzipCompatible);
            }
        };
        final List<String> linesWritten = new ArrayList<>();
        final BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(f, 5, myDeflaterFactory);
        String s = "Hi, Mom!\n";
        bcos.write(s.getBytes()); //Call 1
        linesWritten.add(s);
        s = "Hi, Dad!\n";
        bcos.write(s.getBytes()); //Call 2
        linesWritten.add(s);
        bcos.flush();
        final StringBuilder sb = new StringBuilder(BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE * 2);
        s = "1234567890123456789012345678901234567890123456789012345678901234567890\n";
        while (sb.length() <= BlockCompressedStreamConstants.DEFAULT_UNCOMPRESSED_BLOCK_SIZE) {
            sb.append(s);
            linesWritten.add(s);
        }
        bcos.write(sb.toString().getBytes()); //Call 3
        bcos.close();
        final BlockCompressedInputStream bcis = new BlockCompressedInputStream(f);
        final BufferedReader reader = new BufferedReader(new InputStreamReader(bcis));
        String line;
        for(int i = 0; (line = reader.readLine()) != null; ++i) {
            Assert.assertEquals(line + "\n", linesWritten.get(i));
        }
        bcis.close();
        Assert.assertEquals(deflateCalls[0], 3, "deflate calls");
        Assert.assertEquals(reader.readLine(), null);
    }
}
