package htsjdk.samtools.cram;

import htsjdk.HtsjdkTest;
import htsjdk.samtools.CRAMContainerStreamWriter;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.reference.InMemoryReferenceSequenceFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import org.testng.Assert;
import org.testng.annotations.Test;

/**
 * Tests that {@link CRAMContainerStreamWriter} gives callers control over whether the output stream they
 * supplied is closed when writing finishes.  See https://github.com/samtools/htsjdk/issues/1092.
 */
public class CRAMContainerStreamWriterClosureTest extends HtsjdkTest {

    private static final String CONTIG = "1";
    private static final int REF_LENGTH = 200;

    /**
     * An output stream that records whether it has been closed and whether it has been flushed.
     *
     * <p>Both have to be tracked explicitly. {@link ByteArrayOutputStream} makes writes visible
     * immediately and inherits a no-op {@code flush()}, so the contents of the stream say nothing about
     * whether the writer actually flushed it.
     */
    private static final class CloseRecordingStream extends ByteArrayOutputStream {
        private boolean closed = false;
        private boolean flushed = false;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }
    }

    private static byte[] referenceBases() {
        final byte[] bases = new byte[REF_LENGTH];
        final byte[] pattern = "ACGTACGTAC".getBytes();
        for (int i = 0; i < REF_LENGTH; i++) {
            bases[i] = pattern[i % pattern.length];
        }
        return bases;
    }

    private static SAMFileHeader makeHeader() {
        final SAMFileHeader header = new SAMFileHeader();
        header.addSequence(new SAMSequenceRecord(CONTIG, REF_LENGTH));
        header.setSortOrder(SAMFileHeader.SortOrder.coordinate);
        return header;
    }

    private static CRAMContainerStreamWriter makeWriter(final OutputStream outputStream, final SAMFileHeader header) {
        final InMemoryReferenceSequenceFile referenceFile = new InMemoryReferenceSequenceFile();
        referenceFile.add(CONTIG, referenceBases());
        return new CRAMContainerStreamWriter(outputStream, new ReferenceSource(referenceFile), header, "test", null);
    }

    @Test
    public void testFinishDoesNotCloseStreamWhenCloseStreamIsFalse() {
        final SAMFileHeader header = makeHeader();
        final CloseRecordingStream stream = new CloseRecordingStream();

        final CRAMContainerStreamWriter writer = makeWriter(stream, header);
        writer.writeHeader(header);
        writer.finish(true, false);

        Assert.assertFalse(stream.closed, "finish(true, false) must not close a stream supplied by the caller");
        Assert.assertTrue(stream.flushed, "declining the close must still flush the stream, or data can be lost");
    }

    @Test
    public void testFinishClosesStreamWhenCloseStreamIsTrue() {
        final SAMFileHeader header = makeHeader();
        final CloseRecordingStream stream = new CloseRecordingStream();

        final CRAMContainerStreamWriter writer = makeWriter(stream, header);
        writer.writeHeader(header);
        writer.finish(true, true);

        Assert.assertTrue(stream.closed, "finish(true, true) should close the stream");
    }

    /** The single-argument overload must keep its historical behaviour of closing the stream. */
    @Test
    public void testSingleArgumentFinishStillClosesStream() {
        final SAMFileHeader header = makeHeader();
        final CloseRecordingStream stream = new CloseRecordingStream();

        final CRAMContainerStreamWriter writer = makeWriter(stream, header);
        writer.writeHeader(header);
        writer.finish(true);

        Assert.assertTrue(stream.closed, "finish(boolean) must remain backwards compatible and close the stream");
    }

    /** A caller that keeps ownership of the stream can go on using it after the writer has finished. */
    @Test
    public void testCallerCanContinueUsingStreamAfterFinishWithoutClose() throws IOException {
        final SAMFileHeader header = makeHeader();
        final CloseRecordingStream stream = new CloseRecordingStream();

        final CRAMContainerStreamWriter writer = makeWriter(stream, header);
        writer.writeHeader(header);
        writer.finish(true, false);

        final int sizeAfterFinish = stream.size();
        stream.write("trailing".getBytes());
        Assert.assertEquals(stream.size(), sizeAfterFinish + "trailing".length());
        stream.close();
    }
}
