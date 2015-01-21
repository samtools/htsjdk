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
package htsjdk.samtools.cram.mask;

import java.nio.IntBuffer;

public class FastaByteArrayMaskFactory implements ReadMaskFactory<String> {
    public static final byte DEFAULT_MASK_BYTE = 'x';
    public static final byte DEFAULT_NON_MASK_BYTE = '_';
    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private byte maskByte;
    private IntBuffer buf;

    public FastaByteArrayMaskFactory(byte maskByte, int bufSize) {
        this.maskByte = maskByte;
        this.buf = IntBuffer.allocate(bufSize);
    }

    public FastaByteArrayMaskFactory(byte maskByte) {
        this(maskByte, DEFAULT_BUFFER_SIZE);
    }

    public FastaByteArrayMaskFactory() {
        this(DEFAULT_MASK_BYTE, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public PositionMask createMask(String line) throws ReadMaskFormatException {
        if (line.length() == 0)
            return ArrayPositionMask.EMPTY_INSTANCE;

        byte[] data = line.getBytes();

        buf.clear();
        for (int i = 0; i < data.length; i++)
            if (data[i] == maskByte)
                buf.put(i + 1);
            else if (data[i] == '\n')
                throw new ReadMaskFormatException("New line not allowed inside of mask line: "
                        + line.substring(0, Math.min(10, line.length())));

        buf.flip();
        int[] array = new int[buf.limit()];
        buf.get(array);

        return new ArrayPositionMask(array);
    }
}
