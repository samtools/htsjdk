package htsjdk.samtools.fastq;

import htsjdk.samtools.Defaults;
import java.io.File;
import java.nio.file.Path;

/**
 * Factory class for creating FastqWriter objects.
 *
 * @author Tim Fennell
 */
public class FastqWriterFactory {
    boolean useAsyncIo = Defaults.USE_ASYNC_IO_WRITE_FOR_SAMTOOLS;
    boolean createMd5 = Defaults.CREATE_MD5;

    /** Sets whether or not to use async io (i.e. a dedicated thread per writer. */
    public void setUseAsyncIo(final boolean useAsyncIo) {
        this.useAsyncIo = useAsyncIo;
    }

    /** If true, compute MD5 and write appropriately-named file when file is closed. */
    public void setCreateMd5(final boolean createMd5) {
        this.createMd5 = createMd5;
    }

    /**
     * Creates a {@link FastqWriter} that writes to the given path.
     *
     * @param out the path to write the FASTQ data to
     * @return a {@link FastqWriter}, wrapped for asynchronous writing if {@link #useAsyncIo} is set
     */
    public FastqWriter newWriter(final Path out) {
        // BasicFastqWriter is still File-based (migrated separately); convert here to keep the tree compiling.
        final FastqWriter writer = new BasicFastqWriter(out.toFile(), createMd5);
        if (useAsyncIo) {
            return new AsyncFastqWriter(writer, AsyncFastqWriter.DEFAULT_QUEUE_SIZE);
        } else {
            return writer;
        }
    }

    /**
     * Creates a {@link FastqWriter} that writes to the given file.
     *
     * @deprecated since 5.0; use {@link #newWriter(Path)} instead.
     */
    @Deprecated
    public FastqWriter newWriter(final File out) {
        return newWriter(out.toPath());
    }
}
