package htsjdk.samtools;

import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.File;
import java.io.IOException;


class CompressedIndexFileBuffer implements IndexFileBuffer {

    private BlockCompressedInputStream mCompressedStream;

    CompressedIndexFileBuffer(File file) {
        try {
            mCompressedStream = new BlockCompressedInputStream(file);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Construction error of CSI compressed stream: " + ioe));
        }
    }

    @Override
    public void readBytes(final byte[] bytes) {
        try {
            mCompressedStream.read(bytes);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Read error in CSI compressed stream: " + ioe));
        }
    }

    @Override
    public int readInteger() {
        final byte[] intbuff = new byte[4];
        try {
            mCompressedStream.read(intbuff, 0, 4);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Read error in CSI compressed stream: " + ioe));
        }
        return((intbuff[0] & 0xFF) |
                ((intbuff[1] & 0xFF) << 8) |
                ((intbuff[2] & 0xFF) << 16) |
                ((intbuff[3] & 0xFF) << 24));
    }

    @Override
    public long readLong() {
        final long lower = readInteger();
        final long upper = readInteger();
        return ((upper << 32) | (lower & 0xFFFFFFFFL));
    }

    @Override
    public void skipBytes(final int count) {
        try {
            mCompressedStream.skip(count);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Skip error in CSI compressed stream: " + ioe));
        }
    }

    @Override
    public void seek(final int position) {
        try {
            mCompressedStream.seek(position);
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Seek error in CSI compressed stream: " + ioe));
        }
    }

    @Override
    public int position() {
        return (int)mCompressedStream.getPosition();
    }

    @Override
    public void close() {
        try {
            mCompressedStream.close();
        } catch (IOException ioe) {
            throw(new RuntimeIOException("Close error in CSI compressed stream: " + ioe));
        }
    }

}
