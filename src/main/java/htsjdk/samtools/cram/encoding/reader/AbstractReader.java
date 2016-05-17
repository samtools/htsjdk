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

import htsjdk.samtools.cram.common.IntHashMap;
import htsjdk.samtools.cram.encoding.DataSeries;
import htsjdk.samtools.cram.encoding.DataSeriesMap;
import htsjdk.samtools.cram.encoding.DataSeriesType;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.nio.charset.Charset;

@SuppressWarnings({"WeakerAccess", "UnusedDeclaration"})
public abstract class AbstractReader {
    final Charset charset = Charset.forName("UTF8");
    public boolean captureReadNames = false;
    public byte[][][] tagIdDictionary;

    @DataSeries(key = EncodingKey.BF_BitFlags, type = DataSeriesType.INT)
    public DataReader<Integer> bitFlagsCodec;

    @DataSeries(key = EncodingKey.CF_CompressionBitFlags, type = DataSeriesType.BYTE)
    public DataReader<Byte> compressionBitFlagsCodec;

    @DataSeries(key = EncodingKey.RL_ReadLength, type = DataSeriesType.INT)
    public DataReader<Integer> readLengthCodec;

    @DataSeries(key = EncodingKey.AP_AlignmentPositionOffset, type = DataSeriesType.INT)
    public DataReader<Integer> alignmentStartCodec;

    @DataSeries(key = EncodingKey.RG_ReadGroup, type = DataSeriesType.INT)
    public DataReader<Integer> readGroupCodec;

    @DataSeries(key = EncodingKey.RN_ReadName, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> readNameCodec;

    @DataSeries(key = EncodingKey.NF_RecordsToNextFragment, type = DataSeriesType.INT)
    public DataReader<Integer> distanceToNextFragmentCodec;

    @DataSeriesMap(name = "TAG")
    public IntHashMap<DataReader<byte[]>> tagValueCodecs;

    @DataSeries(key = EncodingKey.FN_NumberOfReadFeatures, type = DataSeriesType.INT)
    public DataReader<Integer> numberOfReadFeaturesCodec;

    @DataSeries(key = EncodingKey.FP_FeaturePosition, type = DataSeriesType.INT)
    public DataReader<Integer> readFeaturePositionCodec;

    @DataSeries(key = EncodingKey.FC_FeatureCode, type = DataSeriesType.BYTE)
    public DataReader<Byte> readFeatureCodeCodec;

    @DataSeries(key = EncodingKey.BA_Base, type = DataSeriesType.BYTE)
    public DataReader<Byte> baseCodec;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE)
    public DataReader<Byte> qualityScoreCodec;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> qualityScoresCodec;

    @DataSeries(key = EncodingKey.BS_BaseSubstitutionCode, type = DataSeriesType.BYTE)
    public DataReader<Byte> baseSubstitutionCodec;

    @DataSeries(key = EncodingKey.IN_Insertion, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> insertionCodec;

    @DataSeries(key = EncodingKey.SC_SoftClip, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> softClipCodec;

    @DataSeries(key = EncodingKey.HC_HardClip, type = DataSeriesType.INT)
    public DataReader<Integer> hardClipCodec;

    @DataSeries(key = EncodingKey.PD_padding, type = DataSeriesType.INT)
    public DataReader<Integer> paddingCodec;

    @DataSeries(key = EncodingKey.DL_DeletionLength, type = DataSeriesType.INT)
    public DataReader<Integer> deletionLengthCodec;

    @DataSeries(key = EncodingKey.MQ_MappingQualityScore, type = DataSeriesType.INT)
    public DataReader<Integer> mappingScoreCodec;

    @DataSeries(key = EncodingKey.MF_MateBitFlags, type = DataSeriesType.BYTE)
    public DataReader<Byte> mateBitFlagCodec;

    @DataSeries(key = EncodingKey.NS_NextFragmentReferenceSequenceID, type = DataSeriesType.INT)
    public DataReader<Integer> mateReferenceIdCodec;

    @DataSeries(key = EncodingKey.NP_NextFragmentAlignmentStart, type = DataSeriesType.INT)
    public DataReader<Integer> mateAlignmentStartCodec;

    @DataSeries(key = EncodingKey.TS_InsetSize, type = DataSeriesType.INT)
    public DataReader<Integer> insertSizeCodec;

    @DataSeries(key = EncodingKey.TL_TagIdList, type = DataSeriesType.INT)
    public DataReader<Integer> tagIdListCodec;

    @DataSeries(key = EncodingKey.RI_RefId, type = DataSeriesType.INT)
    public DataReader<Integer> refIdCodec;

    @DataSeries(key = EncodingKey.RS_RefSkip, type = DataSeriesType.INT)
    public DataReader<Integer> refSkipCodec;

    @DataSeries(key = EncodingKey.BB_bases, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> basesCodec;

    @DataSeries(key = EncodingKey.QQ_scores, type = DataSeriesType.BYTE_ARRAY)
    public DataReader<byte[]> scoresCodec;

    public int refId;
    SubstitutionMatrix substitutionMatrix;
    public boolean APDelta = true;

    static int detachedCount = 0;
    int recordCounter = 0;
}
