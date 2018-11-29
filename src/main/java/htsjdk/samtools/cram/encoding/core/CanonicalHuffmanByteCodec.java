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

import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanByteHelper;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;

/**
 * Encode Bytes using the Canonical Huffman Codec.
 * https://en.wikipedia.org/wiki/Canonical_Huffman_code
 */
class CanonicalHuffmanByteCodec extends CoreCodec<Byte> {
    private final HuffmanByteHelper helper;

    /**
     * Construct a Canonical Huffman Codec for Byte data
     *
     * @param coreBlockInputStream the input bitstream to read from
     * @param coreBlockOutputStream the output bitstream to write to
     * @param values the alphabet (provided as Integers)
     * @param bitLengths the number of bits of symbol's huffman code
     */
    public CanonicalHuffmanByteCodec(final BitInputStream coreBlockInputStream,
                                     final BitOutputStream coreBlockOutputStream,
                                     final byte[] values, final int[] bitLengths) {
        super(coreBlockInputStream, coreBlockOutputStream);
        helper = new HuffmanByteHelper(values, bitLengths);
    }

    @Override
    public Byte read() {
        try {
            return helper.read(coreBlockInputStream);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void write(final Byte value) {
        try {
            helper.write(coreBlockOutputStream, value);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public Byte read(final int length) {
        throw new RuntimeException("Not implemented");
    }
}
