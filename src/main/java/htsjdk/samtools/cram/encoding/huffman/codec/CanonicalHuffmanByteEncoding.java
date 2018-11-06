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

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

public class CanonicalHuffmanByteEncoding extends Encoding<Byte> {
    private final byte[] values;
    private final int[] bitLengths;

    private CanonicalHuffmanByteEncoding(final byte[] values, final int[] bitLengths) {
        super(EncodingID.HUFFMAN);
        this.values = values;
        this.bitLengths = bitLengths;
    }

    public static CanonicalHuffmanByteEncoding fromParams(final byte[] data) {
        final ByteBuffer buf = ByteBuffer.wrap(data);

        final int valueSize = ITF8.readUnsignedITF8(buf);
        final byte[] values = new byte[valueSize];
        buf.get(values);

        final int lengthSize = ITF8.readUnsignedITF8(buf);
        final int[] bitLengths = new int[lengthSize];
        for (int i = 0; i < lengthSize; i++) {
            bitLengths[i] = ITF8.readUnsignedITF8(buf);
        }

        return new CanonicalHuffmanByteEncoding(values, bitLengths);
    }

    @Override
    public byte[] toByteArray() {
        final ByteBuffer buf = ByteBuffer.allocate(ITF8.MAX_BYTES * (values.length + bitLengths.length));

        ITF8.writeUnsignedITF8(values.length, buf);
        for (final byte value : values) {
            buf.put(value);
        }

        ITF8.writeUnsignedITF8(bitLengths.length, buf);
        for (final int value : bitLengths) {
            ITF8.writeUnsignedITF8(value, buf);
        }

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    @Override
    public BitCodec<Byte> buildCodec(final Map<Integer, InputStream> inputMap,
                                     final Map<Integer, ExposedByteArrayOutputStream> outputMap) {
        return new CanonicalHuffmanByteCodec(values, bitLengths);
    }
}
