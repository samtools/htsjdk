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
            Assert.assertEquals(headerOnlyContainer.getContainerBlocksByteSize(), fullContainer.getContainerBlocksByteSize());
            Assert.assertEquals(headerOnlyContainer.getReferenceContext(), fullContainer.getReferenceContext());
            Assert.assertEquals(headerOnlyContainer.getAlignmentStart(), fullContainer.getAlignmentStart());
            Assert.assertEquals(headerOnlyContainer.getAlignmentSpan(), fullContainer.getAlignmentSpan());
            Assert.assertEquals(headerOnlyContainer.getNofRecords(), fullContainer.getNofRecords());
            Assert.assertEquals(headerOnlyContainer.getGlobalRecordCounter(), fullContainer.getGlobalRecordCounter());
            Assert.assertEquals(headerOnlyContainer.getBases(), fullContainer.getBases());
            Assert.assertEquals(headerOnlyContainer.getBlockCount(), fullContainer.getBlockCount());
            Assert.assertEquals(headerOnlyContainer.getLandmarks(), fullContainer.getLandmarks());
            Assert.assertEquals(headerOnlyContainer.getChecksum(), fullContainer.getChecksum());
            Assert.assertEquals(headerOnlyContainer.getByteOffset(), fullContainer.getByteOffset());
            // unpopulated fields
            Assert.assertNull(headerOnlyContainer.getBlocks());
            Assert.assertNull(headerOnlyContainer.getCompressionHeader());
            Assert.assertNull(headerOnlyContainer.getSlices());
            // try to read a container from the offset to check it's correct
            try (SeekableFileStream seekableFileStream = new SeekableFileStream(cramFile)) {
                seekableFileStream.seek(headerOnlyContainer.getByteOffset());
                Container container = ContainerIO.readContainer(actualHeader.getVersion(), seekableFileStream);
                Assert.assertEquals(container.getAlignmentStart(), fullContainer.getAlignmentStart());
                Assert.assertEquals(container.getAlignmentSpan(), fullContainer.getAlignmentSpan());
                Assert.assertEquals(container.getNofRecords(), fullContainer.getNofRecords());
                Assert.assertEquals(container.getChecksum(), fullContainer.getChecksum());
            }
        }
    }
}
