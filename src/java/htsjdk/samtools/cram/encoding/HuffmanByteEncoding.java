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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.encoding.huffint.CanonicalHuffmanByteCodec2;
import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class HuffmanByteEncoding implements Encoding<Byte> {
    public static final EncodingID ENCODING_ID = EncodingID.HUFFMAN;
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

        ByteBufferUtils.writeUnsignedITF8(values.length, buf);
        for (byte value : values)
            buf.put(value);

        ByteBufferUtils.writeUnsignedITF8(bitLengths.length, buf);
        for (int value : bitLengths)
            ByteBufferUtils.writeUnsignedITF8(value, buf);

        buf.flip();
        byte[] array = new byte[buf.limit()];
        buf.get(array);
        return array;
    }

    @Override
    public void fromByteArray(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        int size = ByteBufferUtils.readUnsignedITF8(buf);
        values = new byte[size];
        buf.get(values);

        size = ByteBufferUtils.readUnsignedITF8(buf);
        bitLengths = new int[size];
        for (int i = 0; i < size; i++)
            bitLengths[i] = ByteBufferUtils.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<Byte> buildCodec(Map<Integer, InputStream> inputMap,
                                     Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new CanonicalHuffmanByteCodec2(values, bitLengths);
    }

    public static EncodingParams toParam(byte[] bfValues, int[] bfBitLens) {
        HuffmanByteEncoding e = new HuffmanByteEncoding();
        e.values = bfValues;
        e.bitLengths = bfBitLens;
        return new EncodingParams(ENCODING_ID, e.toByteArray());
    }

}
