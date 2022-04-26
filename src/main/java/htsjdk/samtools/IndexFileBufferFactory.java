package htsjdk.samtools;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;

class IndexFileBufferFactory {

    static IndexFileBuffer getBuffer(File file, boolean enableMemoryMapping) {
        boolean isCompressed;
        try {
            isCompressed = IOUtil.isBlockCompressed(file.toPath());
        } catch (IOException ioe) {
            throw (new RuntimeIOException(ioe));
        }

        return isCompressed ? new CompressedIndexFileBuffer(file) : (enableMemoryMapping ? new MemoryMappedFileBuffer(file) : new RandomAccessFileBuffer(file));
    }

    static IndexFileBuffer getBuffer(SeekableStream seekableStream) {
        boolean isCompressed;
        isCompressed = IOUtil.isGZIPInputStream(seekableStream);

        return isCompressed ?
                new CompressedIndexFileBuffer(seekableStream) :
                new IndexStreamBuffer(seekableStream);
    }
}
