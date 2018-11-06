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
package htsjdk.samtools.cram.structure;

/**
 * Encoding ID as defined in the CRAM specs. These are basically ways to serialize a data series.
 */
public enum EncodingID {
    /**
     * "Do nothing" encoding. Should throw an exception when trying reading or writing with this encoding.
     */
    NULL(0),
    /**
     * Shove the data into a byte array for compressing later with a generic compressor like GZIP.
     */
    EXTERNAL(1),
    /**
     * 'naf said: http://en.wikipedia.org/wiki/Golomb_coding
     */
    GOLOMB(2),
    /**
     * http://en.wikipedia.org/wiki/Huffman_coding
     */
    HUFFMAN(3),
    /**
     * A byte array serialized as [length][elements]
     */
    BYTE_ARRAY_LEN(4),
    /**
     * A byte array serialized as [elements][stop]
     */
    BYTE_ARRAY_STOP(5),
    /**
     * http://en.wikipedia.org/wiki/Beta_Code
     */
    BETA(6),
    /**
     * Subexponential codes, see the CRAM specs for details.
     */
    SUBEXPONENTIAL(7),
    /**
     * A variant of GOLOMB encoding: http://en.wikipedia.org/wiki/Golomb_coding
     */
    GOLOMB_RICE(8),
    /**
     * http://en.wikipedia.org/wiki/Elias_gamma_coding
     */
    GAMMA(9);

    private final int specId;

    /**
     * The encodings specified by Section 3 of the CRAM spec
     * @param id the number assigned to each encoding in the CRAM spec
     */
    EncodingID(final int id) {
        specId = id;
    }

    public int getSpecId() {
        return specId;
    }
}
