package htsjdk.samtools.cram.build;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMTestUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.seekablestream.SeekableMemoryStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.testng.Assert;
import org.testng.annotations.Test;

public class CramSpanContainerIteratorTest extends HtsjdkTest {

    @Test
    public void anOpenEndedSpanRunsPastAnInternalEofContainerToTheEndOfTheStream() throws IOException {
        final byte[] cram = CRAMTestUtils.cramWithAnInternalEofContainer();
        final long firstContainer;
        try (CramContainerIterator containers = new CramContainerIterator(new ByteArrayInputStream(cram))) {
            Assert.assertTrue(containers.hasNext());
            firstContainer = containers.next().getContainerByteOffset();
        }
        try (CramSpanContainerIterator span = CramSpanContainerIterator.fromFileSpan(
                new SeekableMemoryStream(cram, "internalEof.cram"),
                new long[] {firstContainer << 16, Long.MAX_VALUE})) {
            int dataContainers = 0;
            while (span.hasNext()) {
                final Container container = span.next();
                if (!container.isEOF()) dataContainers++;
            }
            Assert.assertEquals(dataContainers, 2);
        }
    }
}
