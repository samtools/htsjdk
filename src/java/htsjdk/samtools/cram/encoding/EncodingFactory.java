/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.structure.EncodingID;

public class EncodingFactory {

    public <T> Encoding<T> createEncoding(DataSeriesType valueType,
                                          EncodingID id) {
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
                    case SUBEXP:
                        return (Encoding<T>) new SubexpIntegerEncoding();

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

    public Encoding<byte[]> createByteArrayEncoding(EncodingID id) {
        switch (id) {
            case BYTE_ARRAY_LEN:
                return new ByteArrayLenEncoding();
            case BYTE_ARRAY_STOP:
                return new ByteArrayLenEncoding();
            case EXTERNAL:
                return new ExternalByteArrayEncoding();

            default:
                break;
        }
        return null;
    }

    public Encoding<Byte> createByteEncoding(EncodingID id) {
        switch (id) {
            case EXTERNAL:
                return new ExternalByteEncoding();

            default:
                break;
        }
        return null;
    }

    public Encoding<Integer> createIntEncoding(EncodingID id) {
        return null;
    }
}
