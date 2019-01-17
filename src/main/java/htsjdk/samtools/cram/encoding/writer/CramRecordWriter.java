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

import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.structure.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CramRecordWriter {
    private final DataSeriesWriter<Integer> bitFlagsC;
    private final DataSeriesWriter<Byte> compBitFlagsC;
    private final DataSeriesWriter<Integer> readLengthC;
    private final DataSeriesWriter<Integer> alStartC;
    private final DataSeriesWriter<Integer> readGroupC;
    private final DataSeriesWriter<byte[]> readNameC;
    private final DataSeriesWriter<Integer> distanceC;
    private final Map<Integer, DataSeriesWriter<byte[]>> tagValueCodecs;
    private final DataSeriesWriter<Integer> numberOfReadFeaturesCodec;
    private final DataSeriesWriter<Integer> featurePositionCodec;
    private final DataSeriesWriter<Byte> featuresCodeCodec;
    private final DataSeriesWriter<Byte> baseCodec;
    private final DataSeriesWriter<Byte> qualityScoreCodec;
    private final DataSeriesWriter<byte[]> qualityScoreArrayCodec;
    private final DataSeriesWriter<Byte> baseSubstitutionCodeCodec;
    private final DataSeriesWriter<byte[]> insertionCodec;
    private final DataSeriesWriter<byte[]> softClipCodec;
    private final DataSeriesWriter<Integer> hardClipCodec;
    private final DataSeriesWriter<Integer> paddingCodec;
    private final DataSeriesWriter<Integer> deletionLengthCodec;
    private final DataSeriesWriter<Integer> mappingQualityScoreCodec;
    private final DataSeriesWriter<Byte> mateBitFlagsCodec;
    private final DataSeriesWriter<Integer> nextFragmentReferenceSequenceIDCodec;
    private final DataSeriesWriter<Integer> nextFragmentAlignmentStart;
    private final DataSeriesWriter<Integer> templateSize;
    private final DataSeriesWriter<Integer> tagIdListCodec;
    private final DataSeriesWriter<Integer> refIdCodec;
    private final DataSeriesWriter<Integer> refSkipCodec;

    private final Charset charset = Charset.forName("UTF8");

    private final boolean captureReadNames;
    private final int refId;
    private final SubstitutionMatrix substitutionMatrix;
    private final boolean AP_delta;
    
    private final Map<DataSeries, EncodingParams> encodingMap;
    private final BitOutputStream coreBlockOutputStream;
    private final Map<Integer, ByteArrayOutputStream> externalBlockOutputMap;

    /**
     * Initializes a Cram Record Writer
     *
     * @param coreOutputStream Core data block bit stream, to be written by non-external Encodings
     * @param externalOutputMap External data block byte stream map, to be written by external Encodings
     * @param header the associated Cram Compression Header
     * @param refId the reference sequence ID to assign to these records
     */
    public CramRecordWriter(final BitOutputStream coreOutputStream,
                            final Map<Integer, ByteArrayOutputStream> externalOutputMap,
                            final CompressionHeader header,
                            final int refId) {
        this.captureReadNames = header.readNamesIncluded;
        this.refId = refId;
        this.substitutionMatrix = header.substitutionMatrix;
        this.AP_delta = header.APDelta;

        this.encodingMap = header.encodingMap;
        this.coreBlockOutputStream = coreOutputStream;
        this.externalBlockOutputMap = externalOutputMap;

        bitFlagsC =                             createDataWriter(DataSeries.BF_BitFlags);
        compBitFlagsC =                         createDataWriter(DataSeries.CF_CompressionBitFlags);
        readLengthC =                           createDataWriter(DataSeries.RL_ReadLength);
        alStartC =                              createDataWriter(DataSeries.AP_AlignmentPositionOffset);
        readGroupC =                            createDataWriter(DataSeries.RG_ReadGroup);
        readNameC =                             createDataWriter(DataSeries.RN_ReadName);
        distanceC =                             createDataWriter(DataSeries.NF_RecordsToNextFragment);
        numberOfReadFeaturesCodec =             createDataWriter(DataSeries.FN_NumberOfReadFeatures);
        featurePositionCodec =                  createDataWriter(DataSeries.FP_FeaturePosition);
        featuresCodeCodec =                     createDataWriter(DataSeries.FC_FeatureCode);
        baseCodec =                             createDataWriter(DataSeries.BA_Base);
        qualityScoreCodec =                     createDataWriter(DataSeries.QS_QualityScore);
        baseSubstitutionCodeCodec =             createDataWriter(DataSeries.BS_BaseSubstitutionCode);
        insertionCodec =                        createDataWriter(DataSeries.IN_Insertion);
        softClipCodec =                         createDataWriter(DataSeries.SC_SoftClip);
        hardClipCodec =                         createDataWriter(DataSeries.HC_HardClip);
        paddingCodec =                          createDataWriter(DataSeries.PD_padding);
        deletionLengthCodec =                   createDataWriter(DataSeries.DL_DeletionLength);
        mappingQualityScoreCodec =              createDataWriter(DataSeries.MQ_MappingQualityScore);
        mateBitFlagsCodec =                     createDataWriter(DataSeries.MF_MateBitFlags);
        nextFragmentReferenceSequenceIDCodec =  createDataWriter(DataSeries.NS_NextFragmentReferenceSequenceID);
        nextFragmentAlignmentStart =            createDataWriter(DataSeries.NP_NextFragmentAlignmentStart);
        templateSize =                          createDataWriter(DataSeries.TS_InsertSize);
        tagIdListCodec =                        createDataWriter(DataSeries.TL_TagIdList);
        refIdCodec =                            createDataWriter(DataSeries.RI_RefId);
        refSkipCodec =                          createDataWriter(DataSeries.RS_RefSkip);

        // special case: re-encodes QS as a byte array
        qualityScoreArrayCodec = new DataSeriesWriter<>(DataSeriesType.BYTE_ARRAY, header.encodingMap.get(DataSeries.QS_QualityScore), coreOutputStream, externalOutputMap);

        tagValueCodecs = header.tMap.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        mapEntry -> new DataSeriesWriter<>(DataSeriesType.BYTE_ARRAY, mapEntry.getValue(), coreOutputStream, externalOutputMap)));
    }

    /**
     * Look up a Data Series in the Cram Compression Header's Encoding Map.  If found, create a Data Writer
     *
     * @param dataSeries Which Data Series to write
     * @param <T> The Java data type associated with the Data Series
     * @return a Data Writer for the given Data Series, or null if it's not in the encoding map
     */
    private <T> DataSeriesWriter<T> createDataWriter(final DataSeries dataSeries) {
        if (encodingMap.containsKey(dataSeries)) {
            return new DataSeriesWriter<>(dataSeries.getType(), encodingMap.get(dataSeries), coreBlockOutputStream, externalBlockOutputMap);
        }
        else {
            return null;
        }
    }

    /**
     * Writes a series of Cram Compression Records, using this class's Encodings
     *
     * @param records the Cram Compression Records to write
     * @param prevAlignmentStart the alignmentStart of the previous record, for delta calculation
     */
    public void writeCramCompressionRecords(final List<CramCompressionRecord> records, int prevAlignmentStart) {
        for (final CramCompressionRecord record : records) {
            record.alignmentDelta = record.alignmentStart - prevAlignmentStart;
            prevAlignmentStart = record.alignmentStart;
            writeRecord(record);
        }
    }

    /**
     * Write a Cram Compression Record, using this class's Encodings
     *
     * @param r the Cram Compression Record to write
     */
    private void writeRecord(final CramCompressionRecord r) {
        bitFlagsC.writeData(r.flags);
        compBitFlagsC.writeData(r.getCompressionFlags());
        if (refId == Slice.MULTI_REFERENCE) {
            refIdCodec.writeData(r.sequenceId);
        }

        readLengthC.writeData(r.readLength);

        if (AP_delta) {
            alStartC.writeData(r.alignmentDelta);
        } else {
            alStartC.writeData(r.alignmentStart);
        }

        readGroupC.writeData(r.readGroupID);

        if (captureReadNames) {
            readNameC.writeData(r.readName.getBytes(charset));
        }

        // mate record:
        if (r.isDetached()) {
            mateBitFlagsCodec.writeData(r.getMateFlags());
            if (!captureReadNames) {
                readNameC.writeData(r.readName.getBytes(charset));
            }

            nextFragmentReferenceSequenceIDCodec.writeData(r.mateSequenceID);
            nextFragmentAlignmentStart.writeData(r.mateAlignmentStart);
            templateSize.writeData(r.templateSize);
        } else if (r.isHasMateDownStream()) {
            distanceC.writeData(r.recordsToNextFragment);
        }

        // tag records:
        tagIdListCodec.writeData(r.tagIdsIndex.value);
        if (r.tags != null) {
            for (int i = 0; i < r.tags.length; i++) {
                final DataSeriesWriter<byte[]> writer = tagValueCodecs.get(r.tags[i].keyType3BytesAsInt);
                writer.writeData(r.tags[i].getValueAsByteArray());
            }
        }

        if (!r.isSegmentUnmapped()) {
            // writing read features:
            numberOfReadFeaturesCodec.writeData(r.readFeatures.size());
            int prevPos = 0;
            for (final ReadFeature f : r.readFeatures) {
                featuresCodeCodec.writeData(f.getOperator());

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
            if (!r.isUnknownBases()) {
                for (final byte b : r.readBases) {
                    baseCodec.writeData(b);
                }
            }

            if (r.isForcePreserveQualityScores()) {
                qualityScoreArrayCodec.writeData(r.qualityScores);
            }
        }
    }
}
