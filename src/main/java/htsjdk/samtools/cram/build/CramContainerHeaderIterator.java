package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerHeaderIO;

import java.io.InputStream;

/**
 * Iterate over CRAM containers from an input stream, and unlike {@link CramContainerIterator} only
 * the header of each container is read, rather than the whole stream. As a result, the container block
 * data is *not* populated, including the compression header block and slices.
 *
 * This class is useful when you are not interested in the contents of containers, for example when indexing container
 * start positions.
 */
public class CramContainerHeaderIterator extends CramContainerIterator {

    public CramContainerHeaderIterator(final InputStream inputStream) {
      super(inputStream);
    }

    /**
     * Consume the entirety of the next container from the stream, but retain only the header.
     * This is intended as a performance optimization, because it does not decode block data.
     *
     * @see CramContainerIterator#containerFromStream(Version, CountingInputStream)
     *
     * @param cramVersion the expected CRAM version of the stream
     * @param countingStream the {@link CountingInputStream} to read from
     * @return The next Container's header from the stream, returned as a Container.
     */
    @Override
    protected Container containerFromStream(final Version cramVersion, final CountingInputStream countingStream) {
        final Container container = ContainerHeaderIO.readContainerHeader(cramVersion.major, countingStream);
        InputStreamUtils.skipFully(countingStream, container.containerByteSize);
        return container;
    }

}
