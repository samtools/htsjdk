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
package htsjdk.samtools.cram.encoding.reader;

import java.io.IOException;

/**
 * A basic interface for reading data. The details of what is data and from where to read are implementation specific. Pure consumer.
 *
 * @param <T> data type of the series to be read
 */
public interface DataReader<T> {

    /**
     * Read a single object
     * @return an object or a primitive value read
     * @throws IOException as per java IO contract
     */
    T readData() throws IOException;

    /**
     * Read an array of specified length. Normally this is a byte array. The intent here is optimization: reading an array may be faster than reading elements one by one.
     * @param length the length of the array to be read
     * @return the array of objects
     * @throws IOException as per java IO contract
     */
    T readDataArray(int length) throws IOException;

}
