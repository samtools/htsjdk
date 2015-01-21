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

public class IntegerListMaskFactory implements ReadMaskFactory<String> {
    public static final String DEFAULT_DEMLIITER = " ";
    public static final int DEFAULT_BUFFER_SIZE = 1024;

    private String delimiter;
    private IntBuffer buf;

    public IntegerListMaskFactory(String delimiter, int bufSize) {
        if (delimiter == null)
            throw new NullPointerException("Delimiter is null.");
        if (bufSize < 1)
            throw new IllegalAccessError("Buffer size must be greater then 0.");

        this.delimiter = delimiter;
        this.buf = IntBuffer.allocate(bufSize);
    }

    public IntegerListMaskFactory(String delimiter) {
        this(delimiter, DEFAULT_BUFFER_SIZE);
    }

    public IntegerListMaskFactory() {
        this(DEFAULT_DEMLIITER, DEFAULT_BUFFER_SIZE);
    }

    @Override
    public PositionMask createMask(String line) throws ReadMaskFormatException {
        if (line.length() == 0)
            return ArrayPositionMask.EMPTY_INSTANCE;

        buf.clear();
        try {
            for (String chunk : line.split(delimiter))
                buf.put(Integer.valueOf(chunk));
        } catch (NumberFormatException e) {
            throw new ReadMaskFormatException(
                    "Expecting integers in " + line.substring(0, Math.min(10, line.length())), e);
        }
        buf.flip();
        int[] array = new int[buf.limit()];
        buf.get(array);
        PositionMask mask = new ArrayPositionMask(array);

        return mask;
    }

}
