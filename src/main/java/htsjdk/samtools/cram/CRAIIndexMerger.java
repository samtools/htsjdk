package htsjdk.samtools.cram;

import htsjdk.samtools.IndexMerger;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Merges CRAM index files for (headerless) parts of a CRAM file into a single index file.
 *
 * A partitioned CRAM is a directory containing the following files:
 * <ol>
 *     <li>A file named <i>header</i> containing all header bytes (CRAM header and CRAM container containing the BAM header).</li>
 *     <li>Zero or more files named <i>part-00000</i>, <i>part-00001</i>, ... etc, containing CRAM containers.</li>
 *     <li>A file named <i>terminator</i> containing a CRAM end-of-file marker container.</li>
 * </ol>
 *
 * If an index is required, a CRAM index can be generated for each (headerless) part file. These files
 * should be named <i>.part-00000.crai</i>, <i>.part-00001.crai</i>, ... etc. Note the leading <i>.</i> to make the files hidden.
 *
 * This format has the following properties:
 *
 * <ul>
 *     <li>Parts and their indexes may be written in parallel, since one part file can be written independently of the others.</li>
 *     <li>A CRAM file can be created from a partitioned CRAM file by merging all the non-hidden files (<i>header</i>, <i>part-00000</i>, <i>part-00001</i>, ..., <i>terminator</i>).</li>
 *     <li>A CRAM index can be created from a partitioned CRAM file by merging all of the hidden files with a <i>.crai</i> suffix. Note that this is <i>not</i> a simple file concatenation operation. See {@link CRAIIndexMerger}.</li>
 * </ul>
 *
 */
public final class CRAIIndexMerger extends IndexMerger<CRAIIndex> {

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
