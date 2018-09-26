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

import htsjdk.HtsjdkTest;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;

import static org.testng.Assert.assertEquals;

public class SeekableBufferedStreamTest extends HtsjdkTest {

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

    @Test
    public void testReadExactlyOneByteAtEndOfFile() throws IOException {
        try (final SeekableStream stream = new SeekableHTTPStream(new URL(BAM_URL_STRING))) {
            byte[] buff = new byte[1];
            long length = stream.length();
            stream.seek(length - 1);
            Assert.assertFalse(stream.eof());
            Assert.assertEquals(stream.read(buff), 1);
            Assert.assertTrue(stream.eof());
            Assert.assertEquals(stream.read(buff), -1);
        }
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

    @Test
    public void testSeek() throws IOException {
        final int bufferSize = 20000;
        final int filledBufferSize = bufferSize / 2;
        final int startPosition = 250000;
        final int length = 5000;

        class LimitedSeekableFileStream extends SeekableFileStream {
            LimitedSeekableFileStream(File file) throws FileNotFoundException {
                super(file);
            }
            @Override
            public int read(byte[] buffer, int offset, int length) throws IOException {
                // only return a fraction of the buffer size (this is allowed by the read contract) to ensure that
                // BufferedInputStream's internal buffer is not filled, and so some of its contents are not valid
                return super.read(buffer, offset, Math.min(length, filledBufferSize));
            }
        }

        final int[] RELATIVE_SEEK_OFFSET = new int[]{-bufferSize*2, -bufferSize, -bufferSize/2, -length, -length/2, -1,
                0, 1, length/2, length, bufferSize/2, bufferSize-1, bufferSize, bufferSize*2};

        for (final int seekOffset : RELATIVE_SEEK_OFFSET) {
            try (SeekableStream unBufferedStream = new SeekableFileStream(BAM_FILE);
                 SeekableBufferedStream bufferedStream = new SeekableBufferedStream(new LimitedSeekableFileStream(BAM_FILE), bufferSize)) {
                byte[] buffer1 = new byte[length];
                unBufferedStream.seek(startPosition);
                int bytesRead = unBufferedStream.read(buffer1, 0, length);
                Assert.assertEquals(length, bytesRead);

                byte[] buffer2 = new byte[length];
                bufferedStream.seek(startPosition);
                bytesRead = bufferedStream.read(buffer2, 0, length);
                Assert.assertEquals(length, bytesRead);

                Assert.assertEquals(buffer1, buffer2);

                unBufferedStream.seek(startPosition + seekOffset);
                bytesRead = unBufferedStream.read(buffer1, 0, length);
                Assert.assertEquals(length, bytesRead);

                Object internalBuffer = bufferedStream.bufferedStream;
                bufferedStream.seek(startPosition + seekOffset);
                bytesRead = bufferedStream.read(buffer2, 0, length);
                Assert.assertEquals(length, bytesRead);
                Object newInternalBuffer = bufferedStream.bufferedStream;
                if (seekOffset >=0 && seekOffset < filledBufferSize) {
                    Assert.assertSame(internalBuffer, newInternalBuffer,
                            "Internal buffer should have been reused for seek offset " + seekOffset);
                } else {
                    Assert.assertNotSame(internalBuffer, newInternalBuffer,
                            "Internal buffer should not have been reused for seek offset " + seekOffset);
                }

                Assert.assertEquals(buffer1, buffer2, "Error at relative seek offset " + seekOffset);
            }
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
