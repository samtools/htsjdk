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
package htsjdk.samtools.cram.encoding.reader;

import htsjdk.samtools.cram.common.IntHashMap;
import htsjdk.samtools.cram.encoding.DataSeries;
import htsjdk.samtools.cram.encoding.DataSeriesMap;
import htsjdk.samtools.cram.encoding.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.nio.charset.Charset;

public abstract class AbstractReader {
    public Charset charset = Charset.forName("UTF8");
    public boolean captureReadNames = false;
    public byte[][][] tagIdDictionary;

    @DataSeries(key = EncodingKey.BF_BitFlags, type = DataSeriesType.INT)
    public DataReader<Integer> bitFlagsC;

    @DataSeries(key = EncodingKey.CF_CompressionBitFlags, type = DataSeriesType.BYTE)
    public DataReader<Byte> compBitFlagsC;

    @DataSeries(key = EncodingKey.RL_ReadLength, type = DataSeriesType.INT)
    public DataReader<Integer> readLengthC;

    @DataSeries(key = EncodingKey.AP_AlignmentPositionOffset, type = DataSeriesType.INT)
    public DataReader<Integer> alStartC;

    @DataSeries(key = EncodingKey.RG_ReadGroup, type = DataSeriesType.INT)
    public DataReader<Integer> readGroupC;

    @DataSeries(key = EncodingKey.RN_ReadName, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> readNameC;

    @DataSeries(key = EncodingKey.NF_RecordsToNextFragment, type = DataSeriesType.INT)
    public DataReader<Integer> distanceC;

    @DataSeries(key = EncodingKey.TC_TagCount, type = DataSeriesType.BYTE)
    public DataReader<Byte> tagCountC;

    @DataSeries(key = EncodingKey.TN_TagNameAndType, type = DataSeriesType.INT)
    public DataReader<Integer> tagNameAndTypeC;

    @DataSeriesMap(name = "TAG")
    public IntHashMap<DataReader<byte[]>> tagValueCodecs;

    @DataSeries(key = EncodingKey.FN_NumberOfReadFeatures, type = DataSeriesType.INT)
    public DataReader<Integer> nfc;

    @DataSeries(key = EncodingKey.FP_FeaturePosition, type = DataSeriesType.INT)
    public DataReader<Integer> fp;

    @DataSeries(key = EncodingKey.FC_FeatureCode, type = DataSeriesType.BYTE)
    public DataReader<Byte> fc;

    @DataSeries(key = EncodingKey.BA_Base, type = DataSeriesType.BYTE)
    public DataReader<Byte> bc;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE)
    public DataReader<Byte> qc;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> qcArray;

    @DataSeries(key = EncodingKey.BS_BaseSubstitutionCode, type = DataSeriesType.BYTE)
    public DataReader<Byte> bsc;

    @DataSeries(key = EncodingKey.IN_Insertion, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> inc;

    @DataSeries(key = EncodingKey.SC_SoftClip, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> softClipCodec;

    @DataSeries(key = EncodingKey.HC_HardClip, type = DataSeriesType.INT)
    public DataReader<Integer> hardClipCodec;

    @DataSeries(key = EncodingKey.PD_padding, type = DataSeriesType.INT)
    public DataReader<Integer> paddingCodec;

    @DataSeries(key = EncodingKey.DL_DeletionLength, type = DataSeriesType.INT)
    public DataReader<Integer> dlc;

    @DataSeries(key = EncodingKey.MQ_MappingQualityScore, type = DataSeriesType.INT)
    public DataReader<Integer> mqc;

    @DataSeries(key = EncodingKey.MF_MateBitFlags, type = DataSeriesType.BYTE)
    public DataReader<Byte> mbfc;

    @DataSeries(key = EncodingKey.NS_NextFragmentReferenceSequenceID, type = DataSeriesType.INT)
    public DataReader<Integer> mrc;

    @DataSeries(key = EncodingKey.NP_NextFragmentAlignmentStart, type = DataSeriesType.INT)
    public DataReader<Integer> malsc;

    @DataSeries(key = EncodingKey.TS_InsetSize, type = DataSeriesType.INT)
    public DataReader<Integer> tsc;

    public static int detachedCount = 0;
    public int recordCounter = 0;

    @DataSeries(key = EncodingKey.TM_TestMark, type = DataSeriesType.INT)
    public DataReader<Integer> testC;

    @DataSeries(key = EncodingKey.TL_TagIdList, type = DataSeriesType.INT)
    public DataReader<Integer> tagIdListCodec;

    @DataSeries(key = EncodingKey.RI_RefId, type = DataSeriesType.INT)
    public DataReader<Integer> refIdCodec;

    @DataSeries(key = EncodingKey.RS_RefSkip, type = DataSeriesType.INT)
    public DataReader<Integer> refSkipCodec;

    public int refId;
    public SubstitutionMatrix substitutionMatrix;
    public boolean AP_delta = true;
}
