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
package htsjdk.samtools.cram.encoding.writer;

import java.io.IOException;

/**
 * A basic interface defining a writer. It can ... write!
 * In terms of CRAM this is an abstraction to get rid off implementation details like what is data and where to write to. Pure consumer.
 * Note: the interface does not have writeArray method like it's counterpart {@link htsjdk.samtools.cram.encoding.reader.DataReader} because
 * array length is known when writing, therefore the same interface can be used both for single objects and arrays.
 *
 * @param <T> data type of the series to be written.
 */
public interface DataWriter<T> {

    /**
     * Write some data out.
     *
     * @param value data to be written
     * @return number of bits written
     * @throws IOException as per java IO contract
     */
    @SuppressWarnings("UnusedReturnValue")
    long writeData(T value) throws IOException;
}
