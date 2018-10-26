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

import htsjdk.samtools.cram.encoding.BitCodec;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.encoding.Encoding;
import htsjdk.samtools.cram.encoding.EncodingFactory;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * A CRAM Data Series reader for a particular Encoding, DataSeriesType and associated parameters
 *
 * @param <T> data type of the series to be read.
 */
public class DataSeriesReader<T> {
    private final BitCodec<T> codec;
    private final BitInputStream bitInputStream;

    /**
     * Initialize a Data Series reader
     *
     * @param valueType type of the data to read
     * @param params encoding-specific parameters
     * @param bitInputStream Core data block bit stream, to be read by non-external Encodings
     * @param inputMap External data block byte stream map, to be read by external Encodings
     */
    public DataSeriesReader(final DataSeriesType valueType,
                            final EncodingParams params,
                            final BitInputStream bitInputStream,
                            final Map<Integer, InputStream> inputMap) {

        final EncodingFactory f = new EncodingFactory();
        final Encoding<T> encoding = f.createEncoding(valueType, params.id);
        if (encoding == null) {
            throw new IllegalArgumentException("Encoding not found: value type="
                    + valueType.name() + ", encoding id=" + params.id.name());
        }

        encoding.fromByteArray(params.params);

        this.codec = encoding.buildCodec(inputMap, null);
        this.bitInputStream = bitInputStream;
    }

    /**
     * Read a single object
     * @return an object or a primitive value read
     * @throws IOException as per java IO contract
     */
    public T readData() throws IOException {
        return codec.read(bitInputStream);
    }

    /**
     * Read an array of specified length. Normally this is a byte array. The intent here is optimization: reading an array may be faster than reading elements one by one.
     * @param length the length of the array to be read
     * @return the array of objects
     * @throws IOException as per java IO contract
     */
    public T readDataArray(final int length) throws IOException {
        return codec.read(bitInputStream, length);
    }

}
