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

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;


/**
 * An interface to describe the requirements for writing out bits as opposed to bytes. Implementors must keep track of the amount of data
 * written similar to {@link OutputStream} concept and provide flush/close functionality accordingly.
 */
public interface BitOutputStream extends Closeable, Flushable {

    /**
     * Write specified number of bits supplied in the integer value. The method is naturally limited to 32 bits max.
     * @param bitContainer an integer containing the bits to be written out
     * @param nofBits the number of bits to written out, minimum 0, maximum 32.
     * @throws IOException as per streaming contract in java.
     */
    void write(int bitContainer, int nofBits) throws IOException;


    /**
     * Write specified number of bits supplied in the long value. The method is naturally limited to 64 bits max.
     * @param bitContainer an integer containing the bits to be written out
     * @param nofBits the number of bits to written out, minimum 0, maximum 64.
     * @throws IOException as per streaming contract in java.
     */
    void write(long bitContainer, int nofBits) throws IOException;


    /**
     * Write specified number of bits supplied in the byte value. The method is naturally limited to 8 bits max.
     * @param bitContainer an integer containing the bits to be written out
     * @param nofBits the number of bits to written out, minimum 0, maximum 8.
     * @throws IOException as per streaming contract in java.
     */
    void write(byte bitContainer, int nofBits) throws IOException;


    /**
     * Write a single bit specified in the boolean argument.
     * @param bit emit 1 if true, 0 otherwise.
     * @throws IOException as per streaming contract in java.
     */
    void write(boolean bit) throws IOException;

    /**
     * Write a single bit specified in the boolean argument repeatedly.
     * @param bit emit 1 if true, 0 otherwise.
     * @param repeat the number of bits to emit.
     * @throws IOException as per streaming contract in java.
     */
    void write(boolean bit, long repeat) throws IOException;
}
