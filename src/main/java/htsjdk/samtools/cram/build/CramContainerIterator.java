package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Iterator;

/**
 * An iterator of CRAM containers read from an {@link java.io.InputStream}.
 */
public class CramContainerIterator implements Iterator<Container>, Closeable {
    private CramHeader cramHeader;
    private final SAMFileHeader samFileHeader;
    // lets the iterator look one byte past an EOF container without counting it as read
    private final PushbackInputStream pushbackInputStream;
    private final CountingInputStream countingInputStream;

    private Container nextContainer;
    private boolean eof = false;

    public CramContainerIterator(final InputStream inputStream) {
        this.pushbackInputStream = new PushbackInputStream(inputStream, 1);
        this.countingInputStream = new CountingInputStream(pushbackInputStream);
        cramHeader = CramIO.readCramHeader(countingInputStream);
        samFileHeader = Container.readSAMFileHeaderContainer(cramHeader.getCRAMVersion(), countingInputStream, null);
    }

    /**
     * Reads the next container, skipping any EOF container that more bytes follow: samtools cat before 1.13 left
     * each input's EOF container in its output, and htslib reads past them.
     */
    private void readNextContainer() {
        nextContainer = containerFromStream(countingInputStream);

        while (nextContainer.isEOF()) {
            if (isAtEndOfStream()) {
                eof = true;
                nextContainer = null;
                return;
            }
            nextContainer = containerFromStream(countingInputStream);
        }
    }

    private boolean isAtEndOfStream() {
        try {
            final int next = pushbackInputStream.read();
            if (next == -1) {
                return true;
            }
            pushbackInputStream.unread(next);
            return false;
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
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
