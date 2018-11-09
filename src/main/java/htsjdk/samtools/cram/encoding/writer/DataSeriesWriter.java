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

import htsjdk.samtools.cram.encoding.CramCodec;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.encoding.CramEncoding;
import htsjdk.samtools.cram.encoding.EncodingFactory;
import htsjdk.samtools.cram.encoding.reader.DataSeriesReader;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.structure.EncodingParams;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * <p>A CRAM Data Series writer for a particular Encoding, DataSeriesType and associated parameters</p>
 * <p>
 * Note: the class does not have a writeArray method like its counterpart {@link DataSeriesReader} because
 * array length is known when writing, therefore the same interface can be used both for single objects and arrays.
 * </p>
 *
 * @param <T> data type of the series to be written.
 */
public class DataSeriesWriter<T> {
    private final CramCodec<T> codec;

    /**
     * Initialize a Data Series writer
     *
     * @param valueType type of the data to write
     * @param params encoding-specific parameters
     * @param bitOutputStream Core data block bit stream, to be written by non-external Encodings
     * @param outputMap External data block byte stream map, to be written by external Encodings
     */
    public DataSeriesWriter(final DataSeriesType valueType,
                            final EncodingParams params,
                            final BitOutputStream bitOutputStream,
                            final Map<Integer, ByteArrayOutputStream> outputMap) {

        final CramEncoding<T> encoding = EncodingFactory.createEncoding(valueType, params.id, params.params);

        this.codec = encoding.buildWriteCodec(bitOutputStream, outputMap);
    }

    /**
     * Write out a single value or an array, depending on the Encoding.
     *
     * @param value data to be written
     */
    void writeData(final T value) {
        codec.write(value);
    }

}

