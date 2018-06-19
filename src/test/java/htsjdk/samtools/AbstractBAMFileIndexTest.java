package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.seekablestream.SeekableStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;

public class AbstractBAMFileIndexTest extends HtsjdkTest {

    private static final AbstractBAMFileIndex afi = new DiskBasedBAMFileIndex(new File("src/test/resources/htsjdk/samtools/BAMFileIndexTest/index_test.bam.bai"), null);

    /**
     * @see <a href="https://github.com/samtools/htsjdk/issues/73">https://github.com/samtools/htsjdk/issues/73</a>
     */
    @Test
    public static void avoidDataExhaustionTest() {
        final IndexStreamBuffer buffer = new IndexStreamBuffer(new SeekableStream() {
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

    @Test
    public static void testGetNumIndexLevels() {
        Assert.assertEquals(AbstractBAMFileIndex.getNumIndexLevels(), 6);
    }

    @Test
    public static void testGetFirstBinInLevelOK() {
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(0), 0);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(1), 1);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(2), 9);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(3), 73);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(4), 585);
        Assert.assertEquals(AbstractBAMFileIndex.getFirstBinInLevel(5), 4681);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetFirstBinInLevelFail() {
        AbstractBAMFileIndex.getFirstBinInLevel(6);
    }

    @Test
    public static void testGetLevelSizeOK() {
        Assert.assertEquals(afi.getLevelSize(0), 1);
        Assert.assertEquals(afi.getLevelSize(1), 8);
        Assert.assertEquals(afi.getLevelSize(2), 64);
        Assert.assertEquals(afi.getLevelSize(3), 512);
        Assert.assertEquals(afi.getLevelSize(4), 4096);
        Assert.assertEquals(afi.getLevelSize(5), 32768);
    }

    @Test (expectedExceptions = SAMException.class)
    public static void testGetLevelSizeFail() {
        afi.getLevelSize(6);
    }
}
