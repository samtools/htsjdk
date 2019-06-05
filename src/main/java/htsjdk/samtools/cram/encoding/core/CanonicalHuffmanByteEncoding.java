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
import htsjdk.samtools.cram.structure.SliceBlocksReadStreams;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CRAMEncoding class for Huffman byte values.
 */
public final class CanonicalHuffmanByteEncoding extends CRAMEncoding<Byte> {
    private final HuffmanParams<Byte> huffmanParams;

    public CanonicalHuffmanByteEncoding(final byte[] symbols, final int[] bitLengths) {
        super(EncodingID.HUFFMAN);
        final List<Byte> symbolList = new ArrayList<>(symbols.length);
        for (final byte b : symbols) {
            symbolList.add(b);
        }
        huffmanParams = new HuffmanParams(
                symbolList,
                Arrays.stream(bitLengths).boxed().collect(Collectors.toList()));
    }

    /**
     * Create a new instance of this encoding using the (ITF8 encoded) serializedParams.
     * @param serializedParams
     * @return CanonicalHuffmanByteEncoding with parameters populated from serializedParams
     */
    public static CanonicalHuffmanByteEncoding fromSerializedEncodingParams(final byte[] serializedParams) {
        final ByteBuffer buf = ByteBuffer.wrap(serializedParams);

        final int symbolListSize = ITF8.readUnsignedITF8(buf);
        final byte[] symbols = new byte[symbolListSize];
        buf.get(symbols);

        final int codeWordLengthsSize = ITF8.readUnsignedITF8(buf);
        final int[] codeWordLengths = new int[codeWordLengthsSize];
        for (int i = 0; i < codeWordLengthsSize; i++) {
            codeWordLengths[i] = ITF8.readUnsignedITF8(buf);
        }

        return new CanonicalHuffmanByteEncoding(symbols, codeWordLengths);
    }

    @Override
    public byte[] toSerializedEncodingParams() {
        final ByteBuffer buf = ByteBuffer.allocate(ITF8.MAX_BYTES *
                (huffmanParams.getSymbols().size() + huffmanParams.getCodeWordLengths().size()));
        ITF8.writeUnsignedITF8(huffmanParams.getSymbols().size(), buf);
        for (final byte value : huffmanParams.getSymbols()) {
            buf.put(value);
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
    public CRAMCodec<Byte> buildCodec(final SliceBlocksReadStreams sliceBlocksReadStreams, final SliceBlocksWriteStreams sliceBlocksWriteStreams) {
        return new CanonicalHuffmanByteCodec(
                sliceBlocksReadStreams == null ? null : sliceBlocksReadStreams.getCoreBlockInputStream(),
                sliceBlocksWriteStreams == null ? null : sliceBlocksWriteStreams.getCoreOutputStream(),
                huffmanParams);
    }

    @Override
    public String toString() {
        return huffmanParams.toString();
    }
}
