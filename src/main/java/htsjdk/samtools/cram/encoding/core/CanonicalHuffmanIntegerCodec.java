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

import htsjdk.samtools.cram.encoding.core.huffmanUtils.HuffmanIntHelper;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;

class CanonicalHuffmanIntegerCodec extends CoreBitCodec<Integer> {
    private final HuffmanIntHelper helper;

    /*
     * values[]: the alphabet (provided as Integers) bitLengths[]: the number of
     * bits of symbol's huffman code
     */
    public CanonicalHuffmanIntegerCodec(final BitInputStream coreBlockInputStream,
                                        final BitOutputStream coreBlockOutputStream,
                                        final int[] values, final int[] bitLengths) {
        super(coreBlockInputStream, coreBlockOutputStream);
        helper = new HuffmanIntHelper(values, bitLengths);
    }

    @Override
    public Integer read() {
        try {
            return helper.read(coreBlockInputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void write(final Integer value) {
        try {
            helper.write(coreBlockOutputStream, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer read(final int length) {
        throw new RuntimeException("Not implemented");
    }
}
