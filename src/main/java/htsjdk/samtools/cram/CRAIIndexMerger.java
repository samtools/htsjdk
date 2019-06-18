package htsjdk.samtools.cram;

import htsjdk.samtools.IndexMerger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

public class CRAIIndexMerger extends IndexMerger<CRAIIndex> {

  private GZIPOutputStream compressedOut;
  private long offset;

  public CRAIIndexMerger(final OutputStream out, final long headerLength) throws IOException {
    super(out, headerLength);
    this.compressedOut = new GZIPOutputStream(new BufferedOutputStream(out));
    this.offset = headerLength;
  }

  @Override
  public void processIndex(CRAIIndex index, long partLength) {
    index.getCRAIEntries()
        .forEach(e -> shift(e, offset).writeToStream(compressedOut));
    offset += partLength;
  }

  private static CRAIEntry shift(CRAIEntry entry, long offset) {
    return new CRAIEntry(entry.getSequenceId(), entry.getAlignmentStart(), entry.getAlignmentSpan(), entry.getContainerStartByteOffset() + offset, entry.getSliceByteOffsetFromCompressionHeaderStart(), entry.getSliceByteSize());
  }

  @Override
  public void finish(long dataFileLength) throws IOException {
    compressedOut.flush();
    compressedOut.close();
  }
}
