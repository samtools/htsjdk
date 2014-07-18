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
package htsjdk.samtools.cram.encoding.huffint;

import htsjdk.samtools.cram.encoding.AbstractBitCodec;
import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

public class CanonicalHuffmanByteCodec2 extends AbstractBitCodec<Byte> {
	private final HelperByte helper;

	/*
	 * values[]: the alphabet (provided as Integers) bitLengths[]: the number of
	 * bits of symbil's huffman code
	 */
	public CanonicalHuffmanByteCodec2(byte[] values, int[] bitLengths) {
		helper = new HelperByte(values, bitLengths);
	}

	@Override
	public Byte read(BitInputStream bis) throws IOException {
		return helper.read(bis);
	}

	@Override
	public long write(BitOutputStream bos, Byte object) throws IOException {
		return helper.write(bos, object);
	}

	@Override
	public long numberOfBits(Byte object) {
		HuffmanBitCode bitCode;
		try {
			bitCode = helper.codes.get(object);
			return bitCode.bitLentgh;
		} catch (NullPointerException e) {
			throw new RuntimeException("Value " + object + " not found.", e);
		}
	}

	@Override
	public Byte read(BitInputStream bis, int len) throws IOException {
		throw new RuntimeException("Not implemented");
	}

	@Override
	public void readInto(BitInputStream bis, byte[] array, int offset,
			int valueLen) throws IOException {
		for (int i = 0; i < valueLen; i++)
			array[offset + i] = helper.read(bis);
	}
}
