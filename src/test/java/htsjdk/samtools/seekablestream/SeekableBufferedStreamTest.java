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

package htsjdk.samtools.seekablestream;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import static org.testng.Assert.assertEquals;

public class SeekableBufferedStreamTest {

//    private final File BAM_INDEX_FILE = new File("testdata/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai");
    private final File BAM_FILE = new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam");
    private final String BAM_URL_STRING = "http://broadinstitute.github.io/picard/testdata/index_test.bam";
    private static File TestFile = new File("src/test/resources/htsjdk/samtools/seekablestream/megabyteZeros.dat");

    /**
     * Test reading across a buffer boundary (buffer size is 512000).   The test first reads a range of
     * bytes using an unbuffered stream file stream,  then compares this to results from a buffered http stream.
     *
     * @throws IOException
     */
    @Test
    public void testRandomRead() throws IOException {

        int startPosition = 500000;
        int length = 50000;

        byte[] buffer1 = new byte[length];
        SeekableStream unBufferedStream = new SeekableFileStream(BAM_FILE);
        unBufferedStream.seek(startPosition);
        int bytesRead = unBufferedStream.read(buffer1, 0, length);
        assertEquals(length, bytesRead);

        byte[] buffer2 = new byte[length];
        SeekableStream bufferedStream = new SeekableBufferedStream(new SeekableHTTPStream(new URL(BAM_URL_STRING)));
        bufferedStream.seek(startPosition);
        bytesRead = bufferedStream.read(buffer2, 0, length);
        assertEquals(length, bytesRead);

        assertEquals(buffer1, buffer2);
    }

    /**
     * Test an attempt to read past the end of the file.  The test file is 594,149 bytes in length.  The test
     * attempts to read a 1000 byte block starting at position 594000.  A correct result would return 149 bytes.
     *
     * @throws IOException
     */
    @Test
    public void testEOF() throws IOException {

        int remainder = 149;
        long fileLength = BAM_FILE.length();
        long startPosition = fileLength - remainder;
        int length = 1000;


        byte[] buffer = new byte[length];
        SeekableStream bufferedStream = new SeekableBufferedStream(new SeekableHTTPStream(new URL(BAM_URL_STRING)));
        bufferedStream.seek(startPosition);
        int bytesRead = bufferedStream.read(buffer, 0, length);
        assertEquals(remainder, bytesRead);

        // Subsequent reads should return -1
        bytesRead = bufferedStream.read(buffer, 0, length);
        assertEquals(-1, bytesRead);
    }

    @Test
    public void testSkip() throws IOException {
        final int[] BUFFER_SIZES = new int[]{8, 96, 1024, 8*1024, 16*1024, 96*1024, 48*1024};

        for (final int bufferSize : BUFFER_SIZES) {
            final SeekableBufferedStream in1 = new SeekableBufferedStream(new SeekableFileStream(BAM_FILE), bufferSize);
            final SeekableBufferedStream in2 = new SeekableBufferedStream(new SeekableFileStream(BAM_FILE), bufferSize);

            final int SIZE = 10000;
            final byte[] bytes1 = new byte[SIZE];
            final byte[] bytes2 = new byte[SIZE];

            reallyRead(bytes1, in1);
            reallyRead(bytes1, in1);
            in1.skip(bytes1.length);
            reallyRead(bytes1, in1);

            reallyRead(bytes2, in2);
            reallyRead(bytes2, in2);
            in2.seek(bytes2.length * 3);
            reallyRead(bytes2, in2);

            in1.close();
            in2.close();

            Assert.assertEquals(bytes1, bytes2, "Error at buffer size " + bufferSize);
        }
    }

    private int reallyRead(final byte[] bytes, final SeekableBufferedStream in) throws IOException {
        int read = 0, total = 0;
        do {
            read = in.read(bytes, total, bytes.length-total);
            total += read;
        } while (total != bytes.length && read > 0);

        return total;
    }


    @Test
    public void testDivisableReads()throws IOException{

        testReadsLength(1);
        testReadsLength(2);
        testReadsLength(4);
        testReadsLength(5);
        testReadsLength(10);
        testReadsLength(20);
        testReadsLength(50);
        testReadsLength(100);

    }

    private void testReadsLength(final int length) throws IOException {

        final int BUFFERED_STREAM_BUFFER_SIZE = 100;
        final byte buffer[]=new byte[BUFFERED_STREAM_BUFFER_SIZE*10];
        final SeekableFileStream fileStream = new SeekableFileStream(TestFile);
        final SeekableBufferedStream  bufferedStream = new SeekableBufferedStream(fileStream,BUFFERED_STREAM_BUFFER_SIZE);

        for( int i=0; i<10*BUFFERED_STREAM_BUFFER_SIZE/length ; ++i ){
            assertEquals(bufferedStream.read(buffer, 0, length), length);
        }
    }

}
