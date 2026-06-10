package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

class IndexFileBufferFactory {

    /**
     * Returns true if the given path is not on the default (local) file system.
     * Non-local paths (e.g., S3, GCS, HDFS, Jimfs) cannot be memory-mapped or
     * accessed via {@link java.io.RandomAccessFile} and require a stream-based fallback.
     */
    static boolean isNonLocalPath(final Path path) {
        return !path.getFileSystem().equals(FileSystems.getDefault());
    }

    /**
     * @deprecated use {@link #getBuffer(Path, boolean)} instead.
     */
    @Deprecated
    static IndexFileBuffer getBuffer(File file, boolean enableMemoryMapping) {
        return getBuffer(file.toPath(), enableMemoryMapping);
    }

    static IndexFileBuffer getBuffer(Path path, boolean enableMemoryMapping) {
        if (isNonLocalPath(path)) {
            try {
                return getBuffer(new SeekablePathStream(path));
            } catch (IOException e) {
                throw new RuntimeIOException("Failed to open stream for non-local path: " + path, e);
            }
        }

        boolean isCompressed;
        try {
            isCompressed = IOUtil.isBlockCompressed(path);
        } catch (IOException ioe) {
            throw (new RuntimeIOException(ioe));
        }

        // Local paths back the File-based buffer implementations directly.
        final File file = path.toFile();
        return isCompressed
                ? new CompressedIndexFileBuffer(file)
                : (enableMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));
    }

    static IndexFileBuffer getBuffer(SeekableStream seekableStream) {
        boolean isCompressed;
        isCompressed = IOUtil.isGZIPInputStream(seekableStream);

        return isCompressed ? new CompressedIndexFileBuffer(seekableStream) : new IndexStreamBuffer(seekableStream);
    }
}
