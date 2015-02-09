package htsjdk.samtools.cram.build;

import htsjdk.samtools.cram.io.CountingInputStream;
import htsjdk.samtools.cram.structure.Container;
import htsjdk.samtools.cram.structure.ContainerIO;
import htsjdk.samtools.cram.structure.CramHeader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * Created by vadim on 06/02/2015.
 */
public class CramContainerIterator implements Iterator<Container> {

    private CramHeader cramHeader;
    private InputStream inputStream;
    private Container nextContainer;
    private boolean eof = false;
    private long offset = 0 ;

    public CramContainerIterator(final InputStream inputStream) throws IOException {
        cramHeader = CramIO.readCramHeader(inputStream);
        this.inputStream = inputStream;
    }

    public CramContainerIterator(final CramHeader cramHeader, final InputStream inputStream, long offset) throws IOException {
        this.cramHeader = cramHeader ;
        this.inputStream = inputStream;
        this.offset = offset ;
    }

    protected void readNextContainer() {
        try {
            CountingInputStream cis = new CountingInputStream(inputStream) ;
            nextContainer = ContainerIO.readContainer(cramHeader.getVersion(), cis);
            long containerSizeInBytes = cis.getCount() ;

            nextContainer.offset = offset ;
            offset += containerSizeInBytes ;
        } catch (IOException e) {
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
        return nextContainer;
    }

    @Override
    public void remove() {
        throw new RuntimeException("Read only iterator.");
    }

    public CramHeader getCramHeader() {
        return cramHeader;
    }
}
