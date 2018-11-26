package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;

/**
 * CSI index files produced by SAMtools are BGZF compressed by default. This class
 * adds the ability to read such files.
 */
class CompressedIndexFileBuffer implements IndexFileBuffer {

    private final BlockCompressedInputStream mCompressedStream;
    private final BinaryCodec binaryCodec;

    CompressedIndexFileBuffer(File file) {
        try {
            mCompressedStream = new BlockCompressedInputStream(file);
            binaryCodec = new BinaryCodec(mCompressedStream);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Construction error of CSI compressed stream: " + ioe));
        }
    }

    @Override
    public void readBytes(final byte[] bytes) {
        binaryCodec.readBytes(bytes);
    }

    @Override
    public int readInteger() {
        return binaryCodec.readInt();
    }

    @Override
    public long readLong() {
        return binaryCodec.readLong();
    }

    @Override
    public void skipBytes(final int count) {
        if (mCompressedStream == null) {
            throw new SAMException("Null input stream.");
        }

        try {
            mCompressedStream.skip(count);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Skip error in CSI compressed stream: " + ioe));
        }
    }

    @Override
    public void seek(final long position) {
        if (mCompressedStream == null) {
            throw new SAMException("Null input stream.");
        }

        try {
            mCompressedStream.seek(position);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Seek error in CSI compressed stream: " + ioe));
        }
    }

    @Override
    public long position() {
        if (mCompressedStream == null) {
            throw new SAMException("Null input stream.");
        }

        return mCompressedStream.getPosition();
    }

    @Override
    public void close() {
        if (mCompressedStream == null) {
            throw new SAMException("Null input stream.");
        }

        try {
            mCompressedStream.close();
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Close error in CSI compressed stream: " + ioe));
        }
    }

}
