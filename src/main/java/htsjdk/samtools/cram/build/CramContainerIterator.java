package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.common.Version;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.InputStream;
import java.util.Iterator;

/**
 * An iterator of CRAM containers read from an {@link java.io.InputStream}.
 */
public class CramContainerIterator implements Iterator<Container> {
    private CramHeader cramHeader;
    private CountingInputStream countingInputStream;
    private Container nextContainer;
    private boolean eof = false;
    private long offset = 0;

    public CramContainerIterator(final InputStream inputStream) {
        this.countingInputStream = new CountingInputStream(inputStream);
        cramHeader = CramIO.readCramHeader(countingInputStream);
        this.offset = countingInputStream.getCount();
    }

    void readNextContainer() {
        nextContainer = containerFromStream(cramHeader.getVersion(), countingInputStream);
        final long containerSizeInBytes = countingInputStream.getCount() - offset;

        nextContainer.offset = offset;
        offset += containerSizeInBytes;

        if (nextContainer.isEOF()) {
            eof = true;
            nextContainer = null;
        }
    }

    /**
     * Consume the entirety of the next container from the stream.
     * @param cramVersion
     * @param countingStream
     * @return The next Container from the stream.
     */
    protected Container containerFromStream(final Version cramVersion, final CountingInputStream countingStream) {
        return ContainerIO.readContainer(cramHeader.getVersion(), countingStream);
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
            countingInputStream.close();
        } catch (final Exception e) {
        }
    }
}
