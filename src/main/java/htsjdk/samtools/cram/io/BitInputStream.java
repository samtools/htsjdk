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
package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An interface to describe the requirements for reading bit data as opposed to bytes. Implementors must keep track of the amount of data
 * read similar to {@link InputStream} concept.
 */
public interface BitInputStream {

    /**
     * Reads a single bit from the stream.
     *
     * @throws IOException as per streaming contract in java.
     */
    boolean readBit() throws IOException;

    /**
     * Read specified number of bits from the stream. The bits are return in an integer value.
     *
     * @param length number of bits to read
     * @throws IOException as per streaming contract in java.
     */
    int readBits(int length) throws IOException;

    /**
     * Read specified number of bits from the stream. The bits are return in a long value.
     *
     * @param length number of bits to read
     * @throws IOException as per streaming contract in java.
     */
    long readLongBits(int length) throws IOException;
}
