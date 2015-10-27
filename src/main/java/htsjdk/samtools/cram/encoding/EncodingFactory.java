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

import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanByteEncoding;
import htsjdk.samtools.cram.encoding.huffman.codec.HuffmanIntegerEncoding;
import htsjdk.samtools.cram.structure.EncodingID;

/**
 * A helper class to instantiate an appropriate {@link htsjdk.samtools.cram.encoding.Encoding}
 * for a given {@link htsjdk.samtools.cram.encoding.DataSeriesType} and
 * {@link htsjdk.samtools.cram.encoding.Encoding}.
 * Also useful to hide encoding implementations.
 */
@SuppressWarnings("unchecked")
public class EncodingFactory {

    /**
     * Create an encoding for the data series type and encoding id.
     * @param valueType data type of the values to be produced/consumed by the encoding
     * @param id encoding id used for data serialization
     * @param <T> encoding object type, like Integer or String.
     * @return a new encoding with the requested parameters
     */
    public <T> Encoding<T> createEncoding(final DataSeriesType valueType,
                                          final EncodingID id) {
        switch (valueType) {
            case BYTE:
                switch (id) {
                    case EXTERNAL:
                        return (Encoding<T>) new ExternalByteEncoding();
                    case HUFFMAN:
                        return (Encoding<T>) new HuffmanByteEncoding();
                    case NULL:
                        return new NullEncoding<T>();

                    default:
                        break;
                }

                break;

            case INT:
                switch (id) {
                    case HUFFMAN:
                        return (Encoding<T>) new HuffmanIntegerEncoding();
                    case NULL:
                        return new NullEncoding<T>();
                    case EXTERNAL:
                        return (Encoding<T>) new ExternalIntegerEncoding();
                    case GOLOMB:
                        return (Encoding<T>) new GolombIntegerEncoding();
                    case GOLOMB_RICE:
                        return (Encoding<T>) new GolombRiceIntegerEncoding();
                    case BETA:
                        return (Encoding<T>) new BetaIntegerEncoding();
                    case GAMMA:
                        return (Encoding<T>) new GammaIntegerEncoding();
                    case SUBEXPONENTIAL:
                        return (Encoding<T>) new SubexponentialIntegerEncoding();

                    default:
                        break;
                }
                break;

            case LONG:
                switch (id) {
                    case NULL:
                        return new NullEncoding<T>();
                    case GOLOMB:
                        return (Encoding<T>) new GolombLongEncoding();
                    case EXTERNAL:
                        return (Encoding<T>) new ExternalLongEncoding();

                    default:
                        break;
                }
                break;

            case BYTE_ARRAY:
                switch (id) {
                    case NULL:
                        return new NullEncoding<T>();
                    case BYTE_ARRAY_LEN:
                        return (Encoding<T>) new ByteArrayLenEncoding();
                    case BYTE_ARRAY_STOP:
                        return (Encoding<T>) new ByteArrayStopEncoding();
                    case EXTERNAL:
                        return (Encoding<T>) new ExternalByteArrayEncoding();

                    default:
                        break;
                }
                break;

            default:
                break;
        }

        return null;
    }
}
