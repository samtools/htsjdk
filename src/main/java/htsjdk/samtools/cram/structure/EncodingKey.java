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

public enum EncodingKey {
    BF_BitFlags, AP_AlignmentPositionOffset, FP_FeaturePosition,
    FC_FeatureCode, QS_QualityScore, DL_DeletionLength, BA_Base, TN_TagNameAndType,
    NF_RecordsToNextFragment, RL_ReadLength, RG_ReadGroup, MQ_MappingQualityScore,
    RN_ReadName, NP_NextFragmentAlignmentStart, TS_InsetSize, FN_NumberOfReadFeatures,
    BS_BaseSubstitutionCode, IN_Insertion, TC_TagCount, MF_MateBitFlags,
    NS_NextFragmentReferenceSequenceID, CF_CompressionBitFlags, TV_TestMark, TM_TestMark,
    TL_TagIdList, RI_RefId, RS_RefSkip, SC_SoftClip, HC_HardClip, PD_padding, BB_bases, QQ_scores;

    public static EncodingKey byFirstTwoChars(final String chars) {
        for (final EncodingKey encodingKey : values()) {
            if (encodingKey.name().startsWith(chars))
                return encodingKey;
        }
        return null;
    }

    public static byte[] toTwoBytes(final EncodingKey key) {
        return new byte[]{(byte) key.name().charAt(0), (byte) key.name().charAt(1)};
    }
}
