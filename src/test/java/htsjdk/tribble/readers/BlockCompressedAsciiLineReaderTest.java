package htsjdk.tribble.readers;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.util.BlockCompressedFilePointerUtil;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

public class BlockCompressedAsciiLineReaderTest extends HtsjdkTest {

    private static final String sentinelLine = "Sentinel line";

    @Test
    public void testLineReaderPosition() throws IOException {
        final File multiBlockFile = File.createTempFile("BlockCompressedAsciiLineReaderTest", ".gz");
        multiBlockFile.deleteOnExit();

        // write a file that has more than a single compressed block
        final long expectedFinalLineOffset = populateMultiBlockCompressedFile(multiBlockFile);

        try (final BlockCompressedInputStream bcis = new BlockCompressedInputStream(multiBlockFile);
            final BlockCompressedAsciiLineReader asciiLineReader = new BlockCompressedAsciiLineReader(bcis))
        {
            String line = null;
            long actualFinalLineOffset = -1;

            do {
                actualFinalLineOffset = asciiLineReader.getPosition();
                line = asciiLineReader.readLine();
            } while (line != null && !line.equals(sentinelLine));

            // test that we read the sentinel line; its at the expected offset, and that offset
            // represents a virtual file pointer
            Assert.assertNotNull(line);
            Assert.assertEquals(line, sentinelLine);
            Assert.assertEquals(expectedFinalLineOffset, actualFinalLineOffset);
            Assert.assertTrue(BlockCompressedFilePointerUtil.getBlockAddress(actualFinalLineOffset) != 0);
        }
    }

    @Test(expectedExceptions = UnsupportedOperationException.class)
    public void testRejectPositionalInputStream() throws IOException {
        final File multiBlockFile = File.createTempFile("BlockCompressedAsciiLineReaderTest", ".gz");
        multiBlockFile.deleteOnExit();
        populateMultiBlockCompressedFile(multiBlockFile);

        try (final BlockCompressedInputStream bcis = new BlockCompressedInputStream(multiBlockFile);
             final BlockCompressedAsciiLineReader asciiLineReader = new BlockCompressedAsciiLineReader(bcis)) {
            asciiLineReader.readLine(new PositionalBufferedStream(new ByteArrayInputStream(new byte[1100])));
        }
    }

    // Populate a block compressed file so that has more than a single compressed block
    private long populateMultiBlockCompressedFile(final File tempBlockCompressedFile) throws IOException {
        long sentinelLineOffset = -1;

        try (BlockCompressedOutputStream bcos = new BlockCompressedOutputStream(tempBlockCompressedFile)) {
            // write lines until we exceed the size of the first block (block address != 0)
            do {
                bcos.write("Write this line enough times to exceed the size or a compressed block\n".getBytes());
            } while (BlockCompressedFilePointerUtil.getBlockAddress(bcos.getFilePointer()) == 0);

            sentinelLineOffset = bcos.getFilePointer();

            // write a terminating line that is guaranteed to not be in the first block
            bcos.write(sentinelLine.getBytes());
        }

        return sentinelLineOffset;
    }
}
