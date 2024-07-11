package htsjdk.samtools;

import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/**
 * Traditional implementation of BAM index file access using memory mapped files.
 */
class MemoryMappedFileBuffer implements IndexFileBuffer {
    private MappedByteBuffer mFileBuffer;

    MemoryMappedFileBuffer(final File file) {
        try(final FileChannel fileChannel = FileChannel.open(file.toPath(), StandardOpenOption.READ);) {
            mFileBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0L, fileChannel.size());
            mFileBuffer.order(ByteOrder.LITTLE_ENDIAN);
        } catch (final IOException exc) {
            throw new RuntimeIOException(exc.getMessage(), exc);
        }
    }

    @Override
    public void readBytes(final byte[] bytes) {
        mFileBuffer.get(bytes);
    }

    @Override
    public int readInteger() {
        return mFileBuffer.getInt();
    }

    @Override
    public long readLong() {
        return mFileBuffer.getLong();
    }

    @Override
    public void skipBytes(final int count) {
        mFileBuffer.position(mFileBuffer.position() + count);
    }

    @Override
    public void seek(final long position) {
        mFileBuffer.position(Math.toIntExact(position));
    }

    @Override
    public long position() {
        return mFileBuffer.position();
    }

    @Override
    public void close() {
        mFileBuffer = null;
    }
}
