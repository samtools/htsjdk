package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.RuntimeIOException;

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

    public static CramSpanContainerIterator fromFileSpan(final SeekableStream seekableStream, final long[] coordinates) {
        try {
            return new CramSpanContainerIterator(seekableStream, coordinates);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public boolean hasNext() {
        if (currentBoundary.hasNext()) return true;
        if (!containerBoundaries.hasNext()) return false;
        currentBoundary = containerBoundaries.next();
        return currentBoundary.hasNext();
    }

    @Override
    public Container next() {
        return currentBoundary.next();
    }

    @Override
    public void remove() {
        throw new RuntimeException("Not allowed.");
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }

    private class Boundary implements Iterator<Container> {
        final long start;
        final long end;

        public Boundary(final long start, final long end) {
            this.start = start;
            this.end = end;
            if (start >= end) {
                throw new RuntimeException("Boundary start is greater than end.");
            }
        }

        @Override
        public boolean hasNext() {
            try {
                return seekableStream.position() <= (end >> 16);
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }

        @Override
        public Container next() {
            try {
                if (seekableStream.position() < (start >> 16)) {
                    seekableStream.seek(start >> 16);
                }

                if (!hasNext()) {
                    throw new RuntimeException("No more containers in this boundary.");
                }
                
                return ContainerIO.readContainer(cramHeader.getVersion(), seekableStream);
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
        }
    }

    public long getFirstContainerOffset() {
        return firstContainerOffset;
    }
}
