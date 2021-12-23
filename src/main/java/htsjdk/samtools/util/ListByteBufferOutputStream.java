package htsjdk.samtools.util;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Growable byte buffer backed by a list of byte arrays, which can
 * be used to buffer data without reallocating an underlying array.
 * Once data is accumulated, it can either be retrieved by converting
 * into a byte[] for interfaces that require a contiguous block of bytes,
 * or written directly to an OutputStream to avoid array copies.
 */
public class ListByteBufferOutputStream extends OutputStream {

    private final int blockSize;
    private final ArrayList<byte[]> blocks;
    private byte[] currentBlock;
    private int nextBlockIndex;
    private int nextBytePosition;
    private int size;

    public ListByteBufferOutputStream(final int blockSize) {
        this.blockSize = blockSize;
        blocks = new ArrayList<>();
        nextBlockIndex = 0;
        advanceBlock();
        size = 0;
    }

    @Override
    public void write(final int b) {
        if (nextBytePosition == blockSize) {
            advanceBlock();
        }
        currentBlock[nextBytePosition++] = (byte) b;
        size++;
    }

    public void write(final byte b, final int nCopies) {
        assert nCopies >= 0;

        int bytesRemaining = nCopies;
        while (bytesRemaining > 0) {
            if (nextBytePosition == blockSize) {
                advanceBlock();
            }
            final int toIndex = Math.min(nextBytePosition + bytesRemaining, blockSize);
            Arrays.fill(currentBlock, nextBytePosition, toIndex, b);
            bytesRemaining -= toIndex - nextBytePosition;
            nextBytePosition = toIndex;
        }
        size += nCopies;
    }

    @Override
    public void write(final byte[] b) {
        write(b, 0, b.length);
    }

    @Override
    public void write(final byte[] b, int off, final int len) {
        assert b != null;
        assert off >= 0;
        assert len >= 0;
        assert off + len <= b.length;

        int bytesRemaining = len;
        while (bytesRemaining > 0) {
            if (nextBytePosition == blockSize) {
                advanceBlock();
            }
            final int lengthToWrite = Math.min(bytesRemaining, blockSize - nextBytePosition);
            System.arraycopy(b, off, currentBlock, nextBytePosition, lengthToWrite);
            nextBytePosition += lengthToWrite;
            off += lengthToWrite;
            bytesRemaining -= lengthToWrite;
        }
        size += len;
    }

    public int size() {
        return size;
    }

    public void writeTo(final OutputStream out) throws IOException {
        for (final byte[] b : blocks) {
            if (b == currentBlock) {
                out.write(b, 0, nextBytePosition);
                break;
            } else {
                out.write(b);
            }
        }
    }

    public byte[] toByteArray() {
        final byte[] bytes = new byte[size];
        final ByteBuffer buff = ByteBuffer.wrap(bytes);
        for (final byte[] b : blocks) {
            if (b == currentBlock) {
                buff.put(b, 0, nextBytePosition);
                break;
            } else {
                buff.put(b);
            }
        }
        return bytes;
    }

    public void reset() {
        currentBlock = blocks.get(0);
        nextBytePosition = 0;
        nextBlockIndex = 1;
        size = 0;
    }

    public void clear() {
        reset();
        // blocks always has at least 1 element
        blocks.subList(1, blocks.size()).clear();
    }

    private void advanceBlock() {
        if (nextBlockIndex == blocks.size()) {
            // Need to add a new block
            currentBlock = new byte[blockSize];
            blocks.add(currentBlock);
        } else {
            // Reuse old block
            currentBlock = blocks.get(nextBlockIndex);
        }
        nextBytePosition = 0;
        nextBlockIndex++;
    }
}
