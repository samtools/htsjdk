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

import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanCanoncialCodeGenerator;
import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanParams;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

/**
 * Encode Bytes using the Canonical Huffman Codec.
 * https://en.wikipedia.org/wiki/Canonical_Huffman_code
 */
final class CanonicalHuffmanByteCodec extends CoreCodec<Byte> {
    private final HuffmanCanoncialCodeGenerator<Byte> helper;

    /**
     * Construct a Canonical Huffman Codec for Byte data
     *
     * @param coreBlockInputStream the input bitstream to read from
     * @param coreBlockOutputStream the output bitstream to write to
     * @param huffmanParams HuffmanByteParams for this codex
     */
    CanonicalHuffmanByteCodec(final BitInputStream coreBlockInputStream,
                                     final BitOutputStream coreBlockOutputStream,
                                     final HuffmanParams<Byte> huffmanParams) {
        super(coreBlockInputStream, coreBlockOutputStream);
        helper = new HuffmanCanoncialCodeGenerator(huffmanParams);
    }

    @Override
    public Byte read() {
        return helper.read(coreBlockInputStream);
    }

    @Override
    public void write(final Byte value) {
        helper.write(coreBlockOutputStream, value);
    }

    @Override
    public Byte read(final int length) {
        throw new RuntimeException("read(length) only applicable array codecs");
    }
}
