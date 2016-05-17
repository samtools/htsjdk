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

import htsjdk.samtools.cram.encoding.DataSeries;
import htsjdk.samtools.cram.encoding.DataSeriesMap;
import htsjdk.samtools.cram.encoding.DataSeriesType;
import htsjdk.samtools.cram.encoding.readfeatures.BaseQualityScore;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.HardClip;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.Padding;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.RefSkip;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.EncodingKey;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

@SuppressWarnings({"UnusedDeclaration", "WeakerAccess"})
public class Writer {

    private Charset charset = Charset.forName("UTF8");
    private boolean captureReadNames = false;

    @DataSeries(key = EncodingKey.BF_BitFlags, type = DataSeriesType.INT)
    public DataWriter<Integer> bitFlagsC;

    @DataSeries(key = EncodingKey.CF_CompressionBitFlags, type = DataSeriesType.BYTE)
    public DataWriter<Byte> compBitFlagsC;

    @DataSeries(key = EncodingKey.RL_ReadLength, type = DataSeriesType.INT)
    public DataWriter<Integer> readLengthC;

    @DataSeries(key = EncodingKey.AP_AlignmentPositionOffset, type = DataSeriesType.INT)
    public DataWriter<Integer> alStartC;

    @DataSeries(key = EncodingKey.RG_ReadGroup, type = DataSeriesType.INT)
    public DataWriter<Integer> readGroupC;

    @DataSeries(key = EncodingKey.RN_ReadName, type = DataSeriesType.BYTE_ARRAY)
    public DataWriter<byte[]> readNameC;

    @DataSeries(key = EncodingKey.NF_RecordsToNextFragment, type = DataSeriesType.INT)
    public DataWriter<Integer> distanceC;

    @DataSeriesMap(name = "TAG")
    public Map<Integer, DataWriter<byte[]>> tagValueCodecs;

    @DataSeries(key = EncodingKey.FN_NumberOfReadFeatures, type = DataSeriesType.INT)
    public DataWriter<Integer> numberOfReadFeaturesCodec;

    @DataSeries(key = EncodingKey.FP_FeaturePosition, type = DataSeriesType.INT)
    public DataWriter<Integer> featurePositionCodec;

    @DataSeries(key = EncodingKey.FC_FeatureCode, type = DataSeriesType.BYTE)
    public DataWriter<Byte> featuresCodeCodec;

    @DataSeries(key = EncodingKey.BA_Base, type = DataSeriesType.BYTE)
    public DataWriter<Byte> baseCodec;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE)
    public DataWriter<Byte> qualityScoreCodec;

    @DataSeries(key = EncodingKey.QS_QualityScore, type = DataSeriesType.BYTE_ARRAY)
    public DataWriter<byte[]> qualityScoreArrayCodec;

    @DataSeries(key = EncodingKey.BS_BaseSubstitutionCode, type = DataSeriesType.BYTE)
    public DataWriter<Byte> baseSubstitutionCodeCodec;

    @DataSeries(key = EncodingKey.IN_Insertion, type = DataSeriesType.BYTE_ARRAY)
    public DataWriter<byte[]> insertionCodec;

    @DataSeries(key = EncodingKey.SC_SoftClip, type = DataSeriesType.BYTE_ARRAY)
    public DataWriter<byte[]> softClipCodec;

    @DataSeries(key = EncodingKey.HC_HardClip, type = DataSeriesType.INT)
    public DataWriter<Integer> hardClipCodec;

    @DataSeries(key = EncodingKey.PD_padding, type = DataSeriesType.INT)
    public DataWriter<Integer> paddingCodec;

    @DataSeries(key = EncodingKey.DL_DeletionLength, type = DataSeriesType.INT)
    public DataWriter<Integer> deletionLengthCodec;

    @DataSeries(key = EncodingKey.MQ_MappingQualityScore, type = DataSeriesType.INT)
    public DataWriter<Integer> mappingQualityScoreCodec;

    @DataSeries(key = EncodingKey.MF_MateBitFlags, type = DataSeriesType.BYTE)
    public DataWriter<Byte> mateBitFlagsCodec;

    @DataSeries(key = EncodingKey.NS_NextFragmentReferenceSequenceID, type = DataSeriesType.INT)
    public DataWriter<Integer> nextFragmentReferenceSequenceIDCodec;

    @DataSeries(key = EncodingKey.NP_NextFragmentAlignmentStart, type = DataSeriesType.INT)
    public DataWriter<Integer> nextFragmentAlignmentStart;

    @DataSeries(key = EncodingKey.TS_InsetSize, type = DataSeriesType.INT)
    public DataWriter<Integer> templateSize;

    @DataSeries(key = EncodingKey.TL_TagIdList, type = DataSeriesType.INT)
    public DataWriter<Integer> tagIdListCodec;

    @DataSeries(key = EncodingKey.RI_RefId, type = DataSeriesType.INT)
    public DataWriter<Integer> refIdCodec;

    @DataSeries(key = EncodingKey.RS_RefSkip, type = DataSeriesType.INT)
    public DataWriter<Integer> refSkipCodec;

    public int refId;
    public SubstitutionMatrix substitutionMatrix;
    public boolean AP_delta = true;

    public static int detachedCount = 0;

    public void write(final CramCompressionRecord r) throws IOException {
        bitFlagsC.writeData(r.flags);
        compBitFlagsC.writeData(r.getCompressionFlags());
        if (refId == -2)
            refIdCodec.writeData(r.sequenceId);

        readLengthC.writeData(r.readLength);

        if (AP_delta)
            alStartC.writeData(r.alignmentDelta);
        else
            alStartC.writeData(r.alignmentStart);

        readGroupC.writeData(r.readGroupID);

        if (isCaptureReadNames()) {
            readNameC.writeData(r.readName.getBytes(charset));
        }

        // mate record:
        if (r.isDetached()) {
            mateBitFlagsCodec.writeData(r.getMateFlags());
            if (!isCaptureReadNames())
                readNameC.writeData(r.readName.getBytes(charset));

            nextFragmentReferenceSequenceIDCodec.writeData(r.mateSequenceID);
            nextFragmentAlignmentStart.writeData(r.mateAlignmentStart);
            templateSize.writeData(r.templateSize);

            detachedCount++;
        } else if (r.isHasMateDownStream())
            distanceC.writeData(r.recordsToNextFragment);

        // tag records:
        tagIdListCodec.writeData(r.tagIdsIndex.value);
        if (r.tags != null) {
            for (int i = 0; i < r.tags.length; i++) {
                final DataWriter<byte[]> writer = tagValueCodecs.get(r.tags[i].keyType3BytesAsInt);
                writer.writeData(r.tags[i].getValueAsByteArray());
            }
        }

        if (!r.isSegmentUnmapped()) {
            // writing read features:
            numberOfReadFeaturesCodec.writeData(r.readFeatures.size());
            int prevPos = 0;
            for (final ReadFeature f : r.readFeatures) {
                featuresCodeCodec.writeData(f.getOperator());
                switch (f.getOperator()) {
                    case Substitution.operator:
                        break;

                    default:
                        break;
                }

                featurePositionCodec.writeData(f.getPosition() - prevPos);
                prevPos = f.getPosition();

                switch (f.getOperator()) {
                    case ReadBase.operator:
                        final ReadBase rb = (ReadBase) f;
                        baseCodec.writeData(rb.getBase());
                        qualityScoreCodec.writeData(rb.getQualityScore());
                        break;
                    case Substitution.operator:
                        final Substitution sv = (Substitution) f;
                        if (sv.getCode() < 0)
                            baseSubstitutionCodeCodec.writeData(substitutionMatrix.code(sv.getReferenceBase(), sv.getBase()));
                        else
                            baseSubstitutionCodeCodec.writeData(sv.getCode());
                        // baseSubstitutionCodec.writeData((byte) sv.getBaseChange().getChange());
                        break;
                    case Insertion.operator:
                        final Insertion iv = (Insertion) f;
                        insertionCodec.writeData(iv.getSequence());
                        break;
                    case SoftClip.operator:
                        final SoftClip fv = (SoftClip) f;
                        softClipCodec.writeData(fv.getSequence());
                        break;
                    case HardClip.operator:
                        final HardClip hv = (HardClip) f;
                        hardClipCodec.writeData(hv.getLength());
                        break;
                    case Padding.operator:
                        final Padding pv = (Padding) f;
                        paddingCodec.writeData(pv.getLength());
                        break;
                    case Deletion.operator:
                        final Deletion dv = (Deletion) f;
                        deletionLengthCodec.writeData(dv.getLength());
                        break;
                    case RefSkip.operator:
                        final RefSkip rsv = (RefSkip) f;
                        refSkipCodec.writeData(rsv.getLength());
                        break;
                    case InsertBase.operator:
                        final InsertBase ib = (InsertBase) f;
                        baseCodec.writeData(ib.getBase());
                        break;
                    case BaseQualityScore.operator:
                        final BaseQualityScore bqs = (BaseQualityScore) f;
                        qualityScoreCodec.writeData(bqs.getQualityScore());
                        break;
                    default:
                        throw new RuntimeException("Unknown read feature operator: " + (char) f.getOperator());
                }
            }

            // mapping quality:
            mappingQualityScoreCodec.writeData(r.mappingQuality);
            if (r.isForcePreserveQualityScores()) {
                qualityScoreArrayCodec.writeData(r.qualityScores);
            }
        } else {
            if (!r.isUnknownBases())
                for (final byte b : r.readBases)
                    baseCodec.writeData(b);
            if (r.isForcePreserveQualityScores()) {
                qualityScoreArrayCodec.writeData(r.qualityScores);
            }
        }
    }

    public boolean isCaptureReadNames() {
        return captureReadNames;
    }

    public void setCaptureReadNames(final boolean captureReadNames) {
        this.captureReadNames = captureReadNames;
    }
}
