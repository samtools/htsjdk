package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;
import java.util.Iterator;

/**
 * Iterate over CRAM containers from an input stream, and unlike {@link CramContainerIterator} only
 * the header of each container is read, rather than the whole stream. As a result, the container block
 * data is *not* populated, including the compression header block and slices.
 *
 * This class is useful when you are not interested in the contents of containers, for example when indexing container
 * start positions.
 */
public class CramContainerHeaderIterator implements Iterator<Container> {
  private CramHeader cramHeader;
  private SeekableStream inputStream;
  private Container nextContainer;
  private boolean eof = false;
  private long offset = 0;

  public CramContainerHeaderIterator(final SeekableStream inputStream) throws IOException {
    cramHeader = CramIO.readCramHeader(inputStream);
    offset = inputStream.position();
    this.inputStream = inputStream;
  }

  private void readNextContainer() {
    try {
      nextContainer = ContainerIO.readContainerHeader(cramHeader.getVersion().major, inputStream);
      nextContainer.offset = offset;

      long pos = inputStream.position();
      final long containerSizeInBytes =
          (pos - offset) + nextContainer.containerByteSize; // containerByteSize excludes header
      offset += containerSizeInBytes;
      InputStreamUtils.skipFully(inputStream, nextContainer.containerByteSize);
    } catch (final IOException e) {
      throw new RuntimeException(e);
    }

    if (nextContainer.isEOF()) {
      eof = true;
      nextContainer = null;
    }
  }

  @Override
  public boolean hasNext() {
    if (eof) return false;
    if (nextContainer == null) readNextContainer();
    return !eof;
  }

  @Override
  public Container next() {
    final Container result = nextContainer;
    nextContainer = null;
    return result;
  }

  @Override
  public void remove() {
    throw new RuntimeException("Read only iterator.");
  }

  public CramHeader getCramHeader() {
    return cramHeader;
  }

  public void close() {
    nextContainer = null;
    cramHeader = null;
    //noinspection EmptyCatchBlock
    try {
      inputStream.close();
    } catch (final Exception e) {
    }
  }
}
