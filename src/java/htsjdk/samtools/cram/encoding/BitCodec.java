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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;

import java.io.IOException;

/**
 * An interface that defines requirements for serializing/deserializing objects into and from a bit stream.
 *
 * @param <T> data series type to be read or written
 * @noinspection UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier, UnnecessaryInterfaceModifier
 */
public interface BitCodec<T> {

    /**
     * Read a single object from the bit stream.
     *
     * @param bitInputStream the bit input stream to rad from
     * @return an object from the stream
     * @throws IOException as per java IO contract
     */
    T read(BitInputStream bitInputStream) throws IOException;

    /**
     * Read a array of specified length from the bit stream.
     *
     * @param bitInputStream the bit input stream to rad from
     * param valueLen the number of elements to read
     * @return an object from the stream
     * @throws IOException as per java IO contract
     */
    T read(BitInputStream bitInputStream, int valueLen) throws IOException;

    /**
     * Read a array of specified length from the bit stream into a given byte array.
     * This method is a way to optimize byte array IO operations by bypassing abstraction. Leaky, I know.
     *
     * @param bitInputStream the bit input stream to rad from
     * @param array the array to read into
     * @param offset offset in the array
     * @param valueLen number of elements to read
     * @throws IOException as per java IO contract
     */
    void readInto(BitInputStream bitInputStream, byte[] array, int offset,
                  int valueLen) throws IOException;

    /**
     * Skip the next object in the bit stream.
     * @param bitInputStream the bit stream to operate on
     * @throws IOException as per java IO contract
     */
    void skip(BitInputStream bitInputStream) throws IOException;

    /**
     * Skip the next length objects in the bit stream.
     * @param bitInputStream the bit stream to operate on
     * @param length the number of objects to skip
     *
     * @throws IOException as per java IO contract
     */
    void skip(BitInputStream bitInputStream, int length) throws IOException;

    /**
     * Write an object into the bit stream
     * @param bitOutputStream the output bit stream to write to
     * @param object the object to write
     * @return the number of bits written out
     * @throws IOException as per java IO contract
     */
    long write(BitOutputStream bitOutputStream, T object) throws IOException;

    /**
     * Calculate the number of bits that the object would take in bit serialized form.
     * @param object an object
     * @return the number of bits
     */
    long numberOfBits(T object);

}
