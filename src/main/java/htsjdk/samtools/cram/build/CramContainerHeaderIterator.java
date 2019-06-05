package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerHeader;

import java.io.InputStream;

/**
 * Iterate over CRAM containers from an input stream, and unlike {@link CramContainerIterator} only
 * the header of each container is read, rather than the whole stream. As a result, the container block
 * data is *not* populated, including the compression header block and slices.
 *
 * This class is useful when you are not interested in the contents of containers, for example when indexing container
 * start positions.
 */
public final class CramContainerHeaderIterator extends CramContainerIterator {

    public CramContainerHeaderIterator(final InputStream inputStream) {
      super(inputStream);
    }

    /**
     * Consume the entirety of the next container from the stream, but retain only the header.
     * This is intended as a performance optimization, because it does not decode block data.
     *
     * @see CramContainerIterator#containerFromStream(CountingInputStream)
     *
     * @param countingStream the {@link CountingInputStream} to read from
     * @return The next Container's header from the stream, returned as a Container.
     */
    @Override
    protected Container containerFromStream(final CountingInputStream countingStream) {
        final long byteOffset = countingStream.getCount();
        final ContainerHeader containerHeader = new ContainerHeader(getCramHeader().getCRAMVersion(), countingStream);
        InputStreamUtils.skipFully(countingStream, containerHeader.getContainerBlocksByteSize());
        return new Container(containerHeader, byteOffset);
    }

}
