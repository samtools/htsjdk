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

import htsjdk.samtools.cram.encoding.CRAMCodec;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.encoding.CRAMEncoding;
import htsjdk.samtools.cram.encoding.EncodingFactory;
import htsjdk.samtools.cram.encoding.reader.DataSeriesReader;
import htsjdk.samtools.cram.structure.EncodingDescriptor;
import htsjdk.samtools.cram.structure.SliceBlocksWriteStreams;

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
    private final CRAMCodec<T> codec;

    /**
     * Initialize a Data Series writer
     *
     * @param valueType type of the data to write
     * @param encodingDecriptor encoding-specific parameters
     * @param sliceBlocksWriteStreams SliceBlocksWriteStreams
     */
    public DataSeriesWriter(final DataSeriesType valueType,
                            final EncodingDescriptor encodingDecriptor,
                            final SliceBlocksWriteStreams sliceBlocksWriteStreams) {

        final CRAMEncoding<T> encoding = EncodingFactory.createCRAMEncoding(valueType, encodingDecriptor);
        this.codec = encoding.buildWriteCodec(sliceBlocksWriteStreams);
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

