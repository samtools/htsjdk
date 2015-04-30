package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * An iterator of CRAM containers read from an {@link java.io.InputStream}.
 */
public class CramContainerIterator implements Iterator<Container> {
    private CramHeader cramHeader;
    private InputStream inputStream;
    private Container nextContainer;
    private boolean eof = false;
    private long offset = 0;

    public CramContainerIterator(final InputStream inputStream) throws IOException {
        cramHeader = CramIO.readCramHeader(inputStream);
        this.inputStream = inputStream;
    }

    void readNextContainer() {
        try {
            final CountingInputStream cis = new CountingInputStream(inputStream);
            nextContainer = ContainerIO.readContainer(cramHeader.getVersion(), cis);
            final long containerSizeInBytes = cis.getCount();

            nextContainer.offset = offset;
            offset += containerSizeInBytes;
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
