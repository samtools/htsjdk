package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.annotations.Test;

import java.io.IOException;

public class AbstractBAMFileIndexTest {

    /**
     * @see <a href="https://github.com/samtools/htsjdk/issues/73">https://github.com/samtools/htsjdk/issues/73</a>
     */
    @Test
    public static void avoidDataExhaustionTest() {
        final AbstractBAMFileIndex.IndexStreamBuffer buffer = new AbstractBAMFileIndex.IndexStreamBuffer(new SeekableStream() {
            @Override
            public long length() {
                return 0;
            }

            @Override
            public long position() throws IOException {
                return 0;
            }

            @Override
            public void seek(final long position) throws IOException {

            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                return 2; // This is the important line; pretend we feed 2 bytes at a time, which is fewer than any downstream calls ultimately request
            }

            @Override
            public void close() throws IOException {

            }

            @Override
            public boolean eof() throws IOException {
                return false;
            }

            @Override
            public String getSource() {
                return null;
            }

            @Override
            public int read() throws IOException {
                return 0;
            }
        });

        // Ensure these throw no exceptions
        buffer.readLong();
        buffer.readInteger();
        buffer.readBytes(new byte[10000]);
    }
}