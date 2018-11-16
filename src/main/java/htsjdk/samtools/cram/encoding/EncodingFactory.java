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

import htsjdk.samtools.cram.encoding.core.BetaIntegerEncoding;
import htsjdk.samtools.cram.encoding.core.experimental.*;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanByteEncoding;
import htsjdk.samtools.cram.encoding.core.CanonicalHuffmanIntegerEncoding;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingID;

/**
 * A helper class to instantiate an appropriate {@link CRAMEncoding}
 * for a given {@link DataSeriesType} and
 * {@link CRAMEncoding}.
 * Also useful to hide encoding implementations.
 */
@SuppressWarnings("unchecked")
public class EncodingFactory {
    /**
     * Create an encoding for the data series type and encoding id.
     * @param valueType data type of the values to be produced/consumed by the encoding
     * @param id encoding id used for data serialization
     * @param params encoding initialization values
     * @param <T> encoding object type, like Integer or String.
     * @return a new encoding with the requested parameters
     */
    public static <T> CRAMEncoding<T> createEncoding(final DataSeriesType valueType,
                                                     final EncodingID id,
                                                     final byte[] params) {
        switch (valueType) {
            case BYTE:
                switch (id) {
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalByteEncoding.fromParams(params);
                    case HUFFMAN:
                        return (CRAMEncoding<T>) CanonicalHuffmanByteEncoding.fromParams(params);
                    case NULL:
                        return new NullEncoding<T>();

                    default:
                        break;
                }

                break;

            case INT:
                switch (id) {
                    case HUFFMAN:
                        return (CRAMEncoding<T>) CanonicalHuffmanIntegerEncoding.fromParams(params);
                    case NULL:
                        return new NullEncoding<T>();
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalIntegerEncoding.fromParams(params);
                    case GOLOMB:
                        return (CRAMEncoding<T>) GolombIntegerEncoding.fromParams(params);
                    case GOLOMB_RICE:
                        return (CRAMEncoding<T>) GolombRiceIntegerEncoding.fromParams(params);
                    case BETA:
                        return (CRAMEncoding<T>) BetaIntegerEncoding.fromParams(params);
                    case GAMMA:
                        return (CRAMEncoding<T>) GammaIntegerEncoding.fromParams(params);
                    case SUBEXPONENTIAL:
                        return (CRAMEncoding<T>) SubexponentialIntegerEncoding.fromParams(params);

                    default:
                        break;
                }
                break;

            case LONG:
                switch (id) {
                    case NULL:
                        return new NullEncoding<T>();
                    case GOLOMB:
                        return (CRAMEncoding<T>) GolombLongEncoding.fromParams(params);
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalLongEncoding.fromParams(params);

                    default:
                        break;
                }
                break;

            case BYTE_ARRAY:
                switch (id) {
                    case NULL:
                        return new NullEncoding<T>();
                    case BYTE_ARRAY_LEN:
                        return (CRAMEncoding<T>) ByteArrayLenEncoding.fromParams(params);
                    case BYTE_ARRAY_STOP:
                        // NOTE: this uses an external block, as mandated by the spec
                        return (CRAMEncoding<T>) ByteArrayStopEncoding.fromParams(params);
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalByteArrayEncoding.fromParams(params);

                    default:
                        break;
                }
                break;

            default:
                break;
        }

        throw new IllegalArgumentException("Encoding not found: value type="
                + valueType.name() + ", encoding id=" + id.name());
    }
}
