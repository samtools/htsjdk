package htsjdk.samtools.fastq;

import htsjdk.samtools.util.Writer;

import java.io.Closeable;

/**
 * Simple interface for a class that can write out fastq records.
 *
 * @author Tim Fennell
 */
public interface FastqWriter extends Closeable, Writer<FastqRecord> {
    void write(final FastqRecord rec);

    @Override
    void close();
}
