package htsjdk.samtools;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.cram.build.CramContainerHeaderIterator;
import htsjdk.samtools.cram.build.CramContainerIterator;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
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
        final File cramFile = new File("src/test/resources/htsjdk/samtools/cram/NA12878.20.21.1-100.100-SeqsPerSlice.0-unMapped.cram");
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
            Assert.assertEquals(headerOnlyContainer.containerBlocksByteSize, fullContainer.containerBlocksByteSize);
            Assert.assertEquals(headerOnlyContainer.getReferenceContext(), fullContainer.getReferenceContext());
            Assert.assertEquals(headerOnlyContainer.alignmentStart, fullContainer.alignmentStart);
            Assert.assertEquals(headerOnlyContainer.alignmentSpan, fullContainer.alignmentSpan);
            Assert.assertEquals(headerOnlyContainer.nofRecords, fullContainer.nofRecords);
            Assert.assertEquals(headerOnlyContainer.globalRecordCounter, fullContainer.globalRecordCounter);
            Assert.assertEquals(headerOnlyContainer.bases, fullContainer.bases);
            Assert.assertEquals(headerOnlyContainer.blockCount, fullContainer.blockCount);
            Assert.assertEquals(headerOnlyContainer.landmarks, fullContainer.landmarks);
            Assert.assertEquals(headerOnlyContainer.checksum, fullContainer.checksum);
            Assert.assertEquals(headerOnlyContainer.byteOffset, fullContainer.byteOffset);
            // unpopulated fields
            Assert.assertNull(headerOnlyContainer.blocks);
            Assert.assertNull(headerOnlyContainer.compressionHeader);
            Assert.assertNull(headerOnlyContainer.getSlices());
            // try to read a container from the offset to check it's correct
            try (SeekableFileStream seekableFileStream = new SeekableFileStream(cramFile)) {
                seekableFileStream.seek(headerOnlyContainer.byteOffset);
                Container container = ContainerIO.readContainer(actualHeader.getVersion(), seekableFileStream);
                Assert.assertEquals(container.alignmentStart, fullContainer.alignmentStart);
                Assert.assertEquals(container.alignmentSpan, fullContainer.alignmentSpan);
                Assert.assertEquals(container.nofRecords, fullContainer.nofRecords);
                Assert.assertEquals(container.checksum, fullContainer.checksum);
            }
        }
    }
}
