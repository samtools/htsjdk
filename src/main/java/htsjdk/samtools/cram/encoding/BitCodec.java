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
     * Write an object into the bit stream
     * @param bitOutputStream the output bit stream to write to
     * @param object the object to write
     * @throws IOException as per java IO contract
     */
    void write(BitOutputStream bitOutputStream, T object) throws IOException;
}
