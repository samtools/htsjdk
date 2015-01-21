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

import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.io.ExposedByteArrayOutputStream;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;


public class HuffmanEncoding implements Encoding<Integer> {
    public static final EncodingID ENCODING_ID = EncodingID.HUFFMAN;
    private int[] bitLengths;
    private int[] values;

    public HuffmanEncoding() {
    }

    @Override
    public EncodingID id() {
        return ENCODING_ID;
    }

    @Override
    public byte[] toByteArray() {
        ByteBuffer buf = ByteBuffer.allocate(1024);
        ByteBufferUtils.writeUnsignedITF8(values.length, buf);
        for (int value : values)
            ByteBufferUtils.writeUnsignedITF8(value, buf);

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
        values = new int[size];

        for (int i = 0; i < size; i++)
            values[i] = ByteBufferUtils.readUnsignedITF8(buf);

        size = ByteBufferUtils.readUnsignedITF8(buf);
        bitLengths = new int[size];
        for (int i = 0; i < size; i++)
            bitLengths[i] = ByteBufferUtils.readUnsignedITF8(buf);
    }

    @Override
    public BitCodec<Integer> buildCodec(Map<Integer, InputStream> inputMap,
                                        Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new CanonicalHuffmanIntegerCodec(values, bitLengths);
    }

    public static EncodingParams toParam(int[] bfValues, int[] bfBitLens) {
        HuffmanEncoding e = new HuffmanEncoding();
        e.values = bfValues;
        e.bitLengths = bfBitLens;
        return new EncodingParams(ENCODING_ID, e.toByteArray());
    }

}
