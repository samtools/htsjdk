package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Iterator;

/**
 * An iterator of CRAM containers read from an {@link java.io.InputStream}.
 */
public class CramContainerIterator implements Iterator<Container>, Closeable {
    private CramHeader cramHeader;
    private final SAMFileHeader samFileHeader;
    private final CountingInputStream countingInputStream;

    private Container nextContainer;
    private boolean eof = false;

    public CramContainerIterator(final InputStream inputStream) {
        this.countingInputStream = new CountingInputStream(inputStream);
        cramHeader = CramIO.readCramHeader(countingInputStream);
        samFileHeader = Container.readSAMFileHeaderContainer(cramHeader.getCRAMVersion(), countingInputStream, null);
    }

    private void readNextContainer() {
        nextContainer = containerFromStream(countingInputStream);

        if (nextContainer.isEOF()) {
            eof = true;
            nextContainer = null;
        }
    }

    /**
     * Consume the entirety of the next container from the stream.
     *
     * @see CramContainerIterator#containerFromStream(CountingInputStream)
     *
     * @param countingStream the {@link CountingInputStream} to read from
     * @return The next Container from the stream.
     */
    protected Container containerFromStream(final CountingInputStream countingStream) {
        final long containerByteOffset = countingStream.getCount();
        return new Container(cramHeader.getCRAMVersion(), countingStream, containerByteOffset);
    }

    @Override
    public boolean hasNext() {
        if (eof) {
            return false;
        }

        if (nextContainer == null) {
            readNextContainer();
        }

        // readNextContainer() may set eof
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

    public SAMFileHeader getSamFileHeader() {
        return samFileHeader;
    }

    @Override
    public void close() {
        nextContainer = null;
        cramHeader = null;
        countingInputStream.close();
    }
}
