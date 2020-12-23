package htsjdk.samtools.fastq;

import htsjdk.samtools.util.Writer;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;

/**
 * Simple interface for a class that can write out fastq records.
 *
 * @author Tim Fennell
 */
public interface FastqWriter extends Closeable, Flushable, Writer<FastqRecord> {
    void write(final FastqRecord rec);

    @Override
    void close();

    @Override
    void flush() throws IOException;
}
