package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramContainerHeaderIterator;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableFileStream;
import htsjdk.samtools.util.Iterables;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CramContainerHeaderIteratorTest extends HtsjdkTest {
    @Test
    public void test() throws IOException {
        final File cramFile = new File("src/test/resources/htsjdk/samtools/cram/SM-74NEG-v1-chr20-downsampled.deduplicated.cram");
        CramHeader expectedHeader;
        List<Container> fullContainers;
        try (SeekableFileStream seekableFileStream = new SeekableFileStream(cramFile)) {
            CramContainerIterator iterator = new CramContainerIterator(seekableFileStream);
            expectedHeader = iterator.getCramHeader();
            fullContainers = Iterables.slurp(iterator);
        }
        CramHeader actualHeader;
        List<Container> headerOnlyContainers;
        try (SeekableFileStream seekableFileStream = new SeekableFileStream(cramFile)) {
            CramContainerHeaderIterator iterator = new CramContainerHeaderIterator(seekableFileStream);
            actualHeader = iterator.getCramHeader();
            headerOnlyContainers = Iterables.slurp(iterator);
        }
        Assert.assertEquals(actualHeader, expectedHeader);
        Assert.assertEquals(fullContainers.size(), headerOnlyContainers.size());
        for (int i = 0; i < fullContainers.size(); i++) {
            Container fullContainer = fullContainers.get(i);
            Container headerOnlyContainer = headerOnlyContainers.get(i);
            Assert.assertEquals(headerOnlyContainer.containerByteSize, fullContainer.containerByteSize);
            Assert.assertEquals(headerOnlyContainer.sequenceId, fullContainer.sequenceId);
            Assert.assertEquals(headerOnlyContainer.alignmentStart, fullContainer.alignmentStart);
            Assert.assertEquals(headerOnlyContainer.alignmentSpan, fullContainer.alignmentSpan);
            Assert.assertEquals(headerOnlyContainer.nofRecords, fullContainer.nofRecords);
            Assert.assertEquals(headerOnlyContainer.globalRecordCounter, fullContainer.globalRecordCounter);
            Assert.assertEquals(headerOnlyContainer.bases, fullContainer.bases);
            Assert.assertEquals(headerOnlyContainer.blockCount, fullContainer.blockCount);
            Assert.assertEquals(headerOnlyContainer.landmarks, fullContainer.landmarks);
            Assert.assertEquals(headerOnlyContainer.checksum, fullContainer.checksum);
            // unpopulated fields
            Assert.assertNull(headerOnlyContainer.blocks);
            Assert.assertNull(headerOnlyContainer.header);
            Assert.assertNull(headerOnlyContainer.slices);
        }
    }
}
