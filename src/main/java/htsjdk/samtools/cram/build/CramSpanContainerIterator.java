package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableStream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * An iterator of CRAM containers read from locations in {@link htsjdk.samtools.seekablestream.SeekableStream}. The locations are specified with
 * pairs of coordinates, they are basically file pointers as returned for example by {@link htsjdk.samtools.SamReader.Indexing#getFilePointerSpanningReads()}
 */
public class CramSpanContainerIterator implements Iterator<Container> {
    private final CramHeader cramHeader;
    private final SeekableStream seekableStream;
    private Iterator<Boundary> containerBoundaries;
    private Boundary currentBoundary;
    private long firstContainerOffset;

    private CramSpanContainerIterator(final SeekableStream seekableStream, final long[] coordinates) throws IOException {
        this.seekableStream = seekableStream;
        seekableStream.seek(0);
        this.cramHeader = CramIO.readCramHeader(seekableStream);
        firstContainerOffset = seekableStream.position();

        final List<Boundary> boundaries = new ArrayList<Boundary>();
        for (int i = 0; i < coordinates.length; i += 2) {
            boundaries.add(new Boundary(coordinates[i], coordinates[i + 1]));
        }

        containerBoundaries = boundaries.iterator();
        currentBoundary = containerBoundaries.next();
    }

    public static CramSpanContainerIterator fromFileSpan(final SeekableStream seekableStream, final long[] coordinates) throws IOException {
        return new CramSpanContainerIterator(seekableStream, coordinates);
    }

    @Override
    public boolean hasNext() {
        try {
            if (currentBoundary.hasNext()) return true;
            if (!containerBoundaries.hasNext()) return false;
            currentBoundary = containerBoundaries.next();
            return currentBoundary.hasNext();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Container next() {
        try {
            return currentBoundary.next();
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void remove() {
        throw new RuntimeException("Not allowed.");
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    private class Boundary {
        final long start;
        final long end;

        public Boundary(final long start, final long end) {
            this.start = start;
            this.end = end;
            if (start >= end) throw new RuntimeException("Boundary start is greater than end.");
        }

        boolean hasNext() throws IOException {
            return seekableStream.position() <= (end >> 16);
        }

        Container next() throws IOException {
            if (seekableStream.position() < (start >> 16)) seekableStream.seek(start >> 16);
            if (seekableStream.position() > (end >> 16)) throw new RuntimeException("No more containers in this boundary.");
            final long offset = seekableStream.position();
            final Container c = ContainerIO.readContainer(cramHeader.getVersion(), seekableStream);
            c.offset = offset;
            return c;
        }
    }

    public long getFirstContainerOffset() {
        return firstContainerOffset;
    }
}
