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

import htsjdk.samtools.cram.encoding.core.*;
import htsjdk.samtools.cram.encoding.core.experimental.*;
import htsjdk.samtools.cram.encoding.external.*;
import htsjdk.samtools.cram.structure.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingDescriptor;
import htsjdk.samtools.cram.structure.EncodingID;

/**
 * A helper class to choose and instantiate an appropriate {@link CRAMEncoding} given a {@link DataSeriesType} and
 * an {@link EncodingDescriptor}.
 */
public class EncodingFactory {

    /**
     * Use the data series value type and EncodingDescriptor to instantiate a corresponding CRAMEncoding of the correct
     * (generic) type.
     * @param valueType
     * @param encodingDescriptor
     * @param <T>
     * @return
     */
    public static <T> CRAMEncoding<T> createCRAMEncoding(
            final DataSeriesType valueType,
            final EncodingDescriptor encodingDescriptor) {
        return createCRAMEncoding(
                valueType,
                encodingDescriptor.getEncodingID(),
                encodingDescriptor.getEncodingParameters());
    }

    /**
     * Create an encoding of the correct type for the data series type and encoding id and params.
     * @param valueType data type of the values to be produced/consumed by the encoding
     * @param encodingID encoding id used for data serialization
     * @param params encoding initialization values
     * @param <T> encoding object type, like Integer or String.
     * @return a new encoding with the requested parameters
     */
    public static <T> CRAMEncoding<T> createCRAMEncoding(final DataSeriesType valueType,
                                                         final EncodingID encodingID,
                                                         final byte[] params) {
        switch (valueType) {
            case BYTE:
                switch (encodingID) {
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalByteEncoding.fromSerializedEncodingParams(params);
                    case HUFFMAN:
                        return (CRAMEncoding<T>) CanonicalHuffmanByteEncoding.fromSerializedEncodingParams(params);
                }
                break;

            case INT:
                switch (encodingID) {
                    case HUFFMAN:
                        return (CRAMEncoding<T>) CanonicalHuffmanIntegerEncoding.fromSerializedEncodingParams(params);
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalIntegerEncoding.fromSerializedEncodingParams(params);
                    case GOLOMB:
                        return (CRAMEncoding<T>) GolombIntegerEncoding.fromSerializedEncodingParams(params);
                    case GOLOMB_RICE:
                        return (CRAMEncoding<T>) GolombRiceIntegerEncoding.fromSerializedEncodingParams(params);
                    case BETA:
                        return (CRAMEncoding<T>) BetaIntegerEncoding.fromSerializedEncodingParams(params);
                    case GAMMA:
                        return (CRAMEncoding<T>) GammaIntegerEncoding.fromSerializedEncodingParams(params);
                    case SUBEXPONENTIAL:
                        return (CRAMEncoding<T>) SubexponentialIntegerEncoding.fromSerializedEncodingParams(params);
                }

            case LONG:
                switch (encodingID) {
                    case GOLOMB:
                        return (CRAMEncoding<T>) GolombLongEncoding.fromSerializedEncodingParams(params);
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalLongEncoding.fromSerializedEncodingParams(params);
                }

            case BYTE_ARRAY:
                switch (encodingID) {
                    case BYTE_ARRAY_LEN:
                        return (CRAMEncoding<T>) ByteArrayLenEncoding.fromSerializedEncodingParams(params);
                    case BYTE_ARRAY_STOP:
                        // NOTE: this uses an external block, as mandated by the spec
                        return (CRAMEncoding<T>) ByteArrayStopEncoding.fromSerializedEncodingParams(params);
                    case EXTERNAL:
                        return (CRAMEncoding<T>) ExternalByteArrayEncoding.fromSerializedEncodingParams(params);
                }
        }

        throw new IllegalArgumentException("Encoding not found: value type="
                + valueType.name() + ", encoding id=" + encodingID.name());
    }
}
