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
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getContainerBlocksByteSize(), fullContainer.getContainerHeader().getContainerBlocksByteSize());
            Assert.assertEquals(headerOnlyContainer.getReferenceContext(), fullContainer.getReferenceContext());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getAlignmentStart(), fullContainer.getContainerHeader().getAlignmentStart());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getAlignmentSpan(), fullContainer.getContainerHeader().getAlignmentSpan());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getRecordCount(), fullContainer.getContainerHeader().getRecordCount());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getGlobalRecordCounter(), fullContainer.getContainerHeader().getGlobalRecordCounter());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getBaseCount(), fullContainer.getContainerHeader().getBaseCount());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getBlockCount(), fullContainer.getContainerHeader().getBlockCount());
            //TODO:this needs to be Arrays.equals ?
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getLandmarks(), fullContainer.getContainerHeader().getLandmarks());
            Assert.assertEquals(headerOnlyContainer.getContainerHeader().getChecksum(), fullContainer.getContainerHeader().getChecksum());
            Assert.assertEquals(headerOnlyContainer.getContainerByteOffset(), fullContainer.getContainerByteOffset());
            // unpopulated fields
            Assert.assertNull(headerOnlyContainer.getCompressionHeader());
            Assert.assertTrue(headerOnlyContainer.getSlices().isEmpty());
            // try to read a container from the offset to check it's correct
            try (SeekableFileStream seekableFileStream = new SeekableFileStream(cramFile)) {
                final long byteOffset = headerOnlyContainer.getContainerByteOffset();
                seekableFileStream.seek(headerOnlyContainer.getContainerByteOffset());
                Container container = new Container(actualHeader.getVersion(), seekableFileStream, byteOffset);
                Assert.assertEquals(container.getContainerHeader().getAlignmentStart(), fullContainer.getContainerHeader().getAlignmentStart());
                Assert.assertEquals(container.getContainerHeader().getAlignmentSpan(), fullContainer.getContainerHeader().getAlignmentSpan());
                Assert.assertEquals(container.getContainerHeader().getRecordCount(), fullContainer.getContainerHeader().getRecordCount());
                Assert.assertEquals(container.getContainerHeader().getChecksum(), fullContainer.getContainerHeader().getChecksum());
            }
        }
    }
}
