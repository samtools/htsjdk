/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.OutputStream;

public class DefaultBitOutputStream extends OutputStream implements BitOutputStream {

    private static final byte[] bitMasks = new byte[8];

    static {
        for (byte i = 0; i < 8; i++)
            bitMasks[i] = (byte) (~(0xFF >>> i));
    }

    private final OutputStream out;

    private int bufferByte = 0;
    private int bufferedNumberOfBits = 0;

    public DefaultBitOutputStream(OutputStream delegate) {
        this.out = delegate;
    }

    public void write(byte b) throws IOException {
        out.write((int) b);
    }

    @Override
    public void write(int value) throws IOException {
        // write(toBytes(value));
        out.write(value);
    }

    private final static byte[] toBytes(int value) {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (value >>> 24);
        bytes[1] = (byte) (value >>> 16);
        bytes[2] = (byte) (value >>> 8);
        bytes[3] = (byte) (value >>> 0);
        return bytes;
    }

    @Override
    public String toString() {
        return "DefaultBitOutputStream: "
                + BitwiseUtils.toBitString(new byte[]{(byte) bufferByte}).substring(0, bufferedNumberOfBits);
    }

    public void write(long value, int nofBitsToWrite) throws IOException {
        if (nofBitsToWrite == 0)
            return;

        if (nofBitsToWrite < 1 || nofBitsToWrite > 64)
            throw new IOException("Expecting 1 to 64 bits, got: value=" + value + ", nofBits=" + nofBitsToWrite);

        if (nofBitsToWrite <= 8)
            write((byte) value, nofBitsToWrite);
        else {
            for (int i = nofBitsToWrite - 8; i >= 0; i -= 8) {
                final byte v = (byte) (value >>> i);
                writeByte(v);
            }
            if (nofBitsToWrite % 8 != 0) {
                final byte v = (byte) value;
                write(v, nofBitsToWrite % 8);
            }
        }
    }

    void write_int_LSB_0(int value, int nofBitsToWrite) throws IOException {
        if (nofBitsToWrite == 0)
            return;

        if (nofBitsToWrite < 1 || nofBitsToWrite > 32)
            throw new IOException("Expecting 1 to 32 bits.");

        if (nofBitsToWrite <= 8)
            write((byte) value, nofBitsToWrite);
        else {
            for (int i = nofBitsToWrite - 8; i >= 0; i -= 8) {
                final byte v = (byte) (value >>> i);
                writeByte(v);
            }
            if (nofBitsToWrite % 8 != 0) {
                final byte v = (byte) value;
                write(v, nofBitsToWrite % 8);
            }
        }
    }

    public void write(int value, int nofBitsToWrite) throws IOException {
        write_int_LSB_0(value, nofBitsToWrite);
        // if (nofBitsToWrite < 1 || nofBitsToWrite > 32)
        // throw new IOException("Expecting 1 to 32 bits.");
        //
        // if (nofBitsToWrite <= 8)
        // write((byte) value, nofBitsToWrite);
        // else {
        // for (int i = 0;; i += 8) {
        // final int v = value >>> (24 - i);
        // if (i >= nofBitsToWrite) {
        // write((byte) v, i % 8);
        // break;
        // } else
        // write((byte) v, 8);
        // }
        // }
    }

    private void writeByte(int value) throws IOException {
        if (bufferedNumberOfBits == 0)
            out.write(value);
        else {
            bufferByte = ((value & 0xFF) >>> bufferedNumberOfBits) | bufferByte;
            out.write(bufferByte);
            bufferByte = (value << (8 - bufferedNumberOfBits)) & 0xFF;
        }
    }

    public void write(byte value, int nofBitsToWrite) throws IOException {
        if (nofBitsToWrite < 0 || nofBitsToWrite > 8)
            throw new IOException("Expecting 0 to 8 bits.");

        if (nofBitsToWrite == 8)
            writeByte(value);
        else {
            if (bufferedNumberOfBits == 0) {
                bufferByte = (value << (8 - nofBitsToWrite)) & 0xFF;
                bufferedNumberOfBits = nofBitsToWrite;
            } else {
                value = (byte) (value & ~bitMasks[8 - nofBitsToWrite]);
                int bits = 8 - bufferedNumberOfBits - nofBitsToWrite;
                if (bits < 0) {
                    bits = -bits;
                    bufferByte |= (value >>> bits);
                    out.write(bufferByte);
                    bufferByte = (value << (8 - bits)) & 0xFF;
                    bufferedNumberOfBits = bits;
                } else if (bits == 0) {
                    bufferByte = bufferByte | value;
                    out.write(bufferByte);
                    bufferedNumberOfBits = 0;
                } else {
                    bufferByte = bufferByte | (value << bits);
                    bufferedNumberOfBits = 8 - bits;
                }
            }
        }
    }

    @Override
    public void write(boolean bit) throws IOException {
        write(bit ? (byte) 1 : (byte) 0, 1);
    }

    public void write(boolean bit, long repeat) throws IOException {
        // optimise later:
        for (long i = 0; i < repeat; i++)
            write(bit);
    }

    @Override
    public void close() throws IOException {
        flush();
        out.close();
    }

    @Override
    public void flush() throws IOException {
        if (bufferedNumberOfBits > 0)
            out.write(bufferByte);

        bufferedNumberOfBits = 0;
        out.flush();
    }

    public OutputStream getDelegate() {
        return out;
    }

    @Override
    public int alignToByte() throws IOException {
        int bitsFlushed = bufferedNumberOfBits;

        if (bufferedNumberOfBits > 0)
            out.write(bufferByte);

        bufferedNumberOfBits = 0;
        bufferByte = 0;

        return bitsFlushed;
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

}
