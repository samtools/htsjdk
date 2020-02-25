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
package htsjdk.samtools.cram.encoding.core;

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanParams;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.structure.EncodingID;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * CRAMEncoding class for Huffman integer values.
 */
public final class CanonicalHuffmanIntegerEncoding extends CRAMEncoding<Integer> {
    private final HuffmanParams<Integer> huffmanParams;

    // Unlike the others, this is public because ByteArrayLenEncoding uses it to create an
    // encoding for the length of the byte array.
    public CanonicalHuffmanIntegerEncoding(final int[] symbols, final int[] bitLengths) {
        super(EncodingID.HUFFMAN);
        huffmanParams = new HuffmanParams(
                Arrays.stream(symbols).boxed().collect(Collectors.toList()),
                Arrays.stream(bitLengths).boxed().collect(Collectors.toList()));
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     *
     * @param serializedParams
     * @return CanonicalHuffmanIntegerEncoding with parameters populated from serializedParams
     */
    public static CanonicalHuffmanIntegerEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buf = ByteBuffer.wrap(serializedParams);

        final int valueSize = ITF8.readUnsignedITF8(buf);
        final int[] values = new int[valueSize];
        for (int i = 0; i < valueSize; i++) {
            values[i] = ITF8.readUnsignedITF8(buf);
        }

        final int lengthSize = ITF8.readUnsignedITF8(buf);
        final int[] bitLengths = new int[lengthSize];
        for (int i = 0; i < lengthSize; i++) {
            bitLengths[i] = ITF8.readUnsignedITF8(buf);
        }

        return new CanonicalHuffmanIntegerEncoding(values, bitLengths);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteBuffer buf = ByteBuffer.allocate(ITF8.MAX_BYTES *
                (huffmanParams.getSymbols().size() + huffmanParams.getCodeWordLengths().size()));

        ITF8.writeUnsignedITF8(huffmanParams.getSymbols().size(), buf);
        for (final int value : huffmanParams.getSymbols()) {
            ITF8.writeUnsignedITF8(value, buf);
        }

        ITF8.writeUnsignedITF8(huffmanParams.getCodeWordLengths().size(), buf);
        for (final int value : huffmanParams.getCodeWordLengths()) {
            ITF8.writeUnsignedITF8(value, buf);
        }

        buf.flip();
        final byte[] array = new byte[buf.limit()];
        buf.get(array);

        return array;
    }

    @Override
    public CRAMCodec<Integer> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams,
                                         final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new CanonicalHuffmanIntegerCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                huffmanParams);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalHuffmanIntegerEncoding that = (CanonicalHuffmanIntegerEncoding) o;
        return huffmanParams.equals(that.huffmanParams);
    }

    @Override
    public int hashCode() { return huffmanParams.hashCode(); }

    @Override
    public String toString() {
        return huffmanParams.toString();
    }
}
