/**
 * ****************************************************************************
 * Copyright 2013 EMBL-EBI
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package htsjdk.samtools.cram.encoding.huffman.codec;

import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class HuffmanByteEncoding implements Encoding<Byte> {
    private static final EncodingID ENCODING_ID = EncodingID.HUFFMAN;
    private int[] bitLengths;
    private byte[] values;
    private ByteBuffer buf = ByteBuffer.allocate(1024);

    public HuffmanByteEncoding() {
    }

    @Override
    public EncodingID id() {
        return ENCODING_ID;
    }

    @Override
    public byte[] toByteArray() {
        buf.clear();
        if (buf.capacity() < values.length * 8)
            buf = ByteBuffer.allocate(values.length * 8);

        ITF8.writeUnsignedITF8(values.length, buf);
        for (final byte value : values)
            buf.put(value);

        ITF8.writeUnsignedITF8(bitLengths.length, buf);
        for (final int value : bitLengths)
            ITF8.writeUnsignedITF8(value, buf);

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);
        return array;
    }

    @Override
    public void fromByteArray(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);
        int size = ITF8.readUnsignedITF8(buf);
        values = new byte[size];
        buf.get(values);

        size = ITF8.readUnsignedITF8(buf);
        bitLengths = new int[size];
        for (int i = 0; i < size; i++)
            bitLengths[i] = ITF8.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<Byte> buildCodec(final Map<Integer, InputStream> inputMap,
                                     final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new CanonicalHuffmanByteCodec(values, bitLengths);
    }

    public static EncodingParams toParam(final byte[] bfValues, final int[] bfBitLens) {
        final HuffmanByteEncoding e = new HuffmanByteEncoding();
        e.values = bfValues;
        e.bitLengths = bfBitLens;
        return new EncodingParams(ENCODING_ID, e.toByteArray());
    }

}
