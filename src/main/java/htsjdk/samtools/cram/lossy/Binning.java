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
package htsjdk.samtools.cram.lossy;

class Binning {

    // @formatter:off
    // NCBI binning scheme:
    // Low High Value
    // 0 0 0
    // 1 1 1
    // 2 2 2
    // 3 14 9
    // 15 19 17
    // 20 24 22
    // 25 29 28
    // 30 noLimit 35
    // @formatter:on
    public static byte[] NCBI_BINNING_MATRIX = new byte[]{
            // @formatter:off
            0, 1, 2, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 17, 17, 17, 17, 17,
            22, 22, 22, 22,
            22,
            28,
            28,
            28,
            28,
            28,
            // @formatter:on
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35, 35,
            35};

    // @formatter:off

    // Illumina binning scheme:
    // 2-9 6
    // 10-19 15
    // 20-24 22
    // 25-29 27
    // 30-34 33
    // 35-39 37
    // greater than or equal to 40 40
    // @formatter:on
    public static final byte[] ILLUMINA_BINNING_MATRIX = new byte[]{
            // @formatter:off
            0, 1, 6, 6, 6, 6, 6, 6, 6, 6, 15, 15, 15, 15, 15, 15, 15, 15, 15,
            15, 22, 22, 22, 22, 22, 27, 27, 27, 27, 27, 33, 33, 33, 33, 33, 37,
            37, 37, 37, 37, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40,
            40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40,
            40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40,
            40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40, 40,
            40, 40, 40, 40, 40, 40};
    // @formatter:on

}
