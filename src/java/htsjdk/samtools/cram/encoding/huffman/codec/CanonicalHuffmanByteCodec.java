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

import htsjdk.samtools.cram.encoding.AbstractBitCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;

class CanonicalHuffmanByteCodec extends AbstractBitCodec<Byte> {
    private final HuffmanByteHelper helper;

    /*
     * values[]: the alphabet (provided as Integers) bitLengths[]: the number of
     * bits of symbol's huffman code
     */
    public CanonicalHuffmanByteCodec(final byte[] values, final int[] bitLengths) {
        helper = new HuffmanByteHelper(values, bitLengths);
    }

    @Override
    public Byte read(final BitInputStream bitInputStream) throws IOException {
        return helper.read(bitInputStream);
    }

    @Override
    public long write(final BitOutputStream bitOutputStream, final Byte object) throws IOException {
        return helper.write(bitOutputStream, object);
    }

    @Override
    public long numberOfBits(final Byte object) {
        final HuffmanBitCode bitCode;
        try {
            //noinspection SuspiciousMethodCalls
            bitCode = helper.codes.get(object);
            return bitCode.bitLength;
        } catch (final NullPointerException e) {
            throw new RuntimeException("Value " + object + " not found.", e);
        }
    }

    @Override
    public Byte read(final BitInputStream bitInputStream, final int length) throws IOException {
        throw new RuntimeException("Not implemented");
    }

    @Override
    public void readInto(final BitInputStream bitInputStream, final byte[] array, final int offset,
                         final int valueLen) throws IOException {
        for (int i = 0; i < valueLen; i++)
            array[offset + i] = helper.read(bitInputStream);
    }
}
