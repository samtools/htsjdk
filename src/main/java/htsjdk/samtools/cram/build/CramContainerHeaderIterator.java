package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;

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

    protected Container containerFromStream(final Version cramVersion, final CountingInputStream countingStream) {
        final Container container = ContainerIO.readContainerHeader(cramVersion.major, countingStream);
        InputStreamUtils.skipFully(countingStream, container.containerByteSize);
        return container;
    }

}
