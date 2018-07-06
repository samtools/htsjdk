package htsjdk.samtools;

import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;

public class IndexFileBufferFactory {

    public static IndexFileBuffer getBuffer(File file, boolean enableMemoryMapping) {
        boolean isCompressed;
        try {
            isCompressed = IOUtil.isBlockCompressed(file.toPath());
        } catch (IOException ioe) {
            throw (new RuntimeIOException(ioe));
        }

        return isCompressed ? new CompressedIndexFileBuffer(file) : (enableMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));
    }
}
