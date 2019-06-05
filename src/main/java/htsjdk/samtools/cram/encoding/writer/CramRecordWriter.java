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
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.structure.Slice;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A writer that emits CRAMCompressionRecord into the various streams that represent a Slice's data series blocks.
 * This essentially acts as a bridge between CRAMCompressionRecord fields and the various various data series streams
 * associated with a Slice. It is the inverse of CramRecordReader.
 */
public class CramRecordWriter {
    //NOTE: these are all named with a "Codec" suffix, but they're really DataSeriesWriters, which are
    // generic-typed wrappers around a CRAMCodec.
    private final DataSeriesWriter<Integer> bitFlagsCodec;
    private final DataSeriesWriter<Integer> cramBitFlagsCodec;
    private final DataSeriesWriter<Integer> readLengthCodec;
    private final DataSeriesWriter<Integer> alignmentStartCodec;
    private final DataSeriesWriter<Integer> readGroupCodec;
    private final DataSeriesWriter<byte[]> readNameCodec;
    private final DataSeriesWriter<Integer> distanceToNextFragmentCodec;
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
    private final DataSeriesWriter<Integer> mateBitFlagsCodec;
    private final DataSeriesWriter<Integer> nextFragmentReferenceSequenceIDCodec;
    private final DataSeriesWriter<Integer> nextFragmentAlignmentStart;
    private final DataSeriesWriter<Integer> templateSize;
    private final DataSeriesWriter<Integer> tagIdListCodec;
    private final DataSeriesWriter<Integer> refIdCodec;
    private final DataSeriesWriter<Integer> refSkipCodec;

    private final static Charset charset = StandardCharsets.UTF_8;

    private final Slice slice;
    private final CompressionHeader compressionHeader;
    private final SliceBlocksWriteStreams sliceBlocksWriteStreams;

    /**
     * Initializes a Cram Record Writer
     *
     * @param slice the target slice to which the records will be written
     */
    public CramRecordWriter(final Slice slice)
    {
        this.slice = slice;
        this.compressionHeader = slice.getCompressionHeader();
        sliceBlocksWriteStreams = new SliceBlocksWriteStreams(compressionHeader);

        // NOTE that this implementation doesn't generate BB or QQ data series, so no writer
        // or codec is created for those.
        bitFlagsCodec =                 createDataWriter(DataSeries.BF_BitFlags);
        cramBitFlagsCodec =             createDataWriter(DataSeries.CF_CompressionBitFlags);
        readLengthCodec =               createDataWriter(DataSeries.RL_ReadLength);
        alignmentStartCodec =                  createDataWriter(DataSeries.AP_AlignmentPositionOffset);
        readGroupCodec =                createDataWriter(DataSeries.RG_ReadGroup);
        readNameCodec =                 createDataWriter(DataSeries.RN_ReadName);
        distanceToNextFragmentCodec =                 createDataWriter(DataSeries.NF_RecordsToNextFragment);
        numberOfReadFeaturesCodec = createDataWriter(DataSeries.FN_NumberOfReadFeatures);
        featurePositionCodec =      createDataWriter(DataSeries.FP_FeaturePosition);
        featuresCodeCodec =         createDataWriter(DataSeries.FC_FeatureCode);
        baseCodec =                 createDataWriter(DataSeries.BA_Base);
        qualityScoreCodec =         createDataWriter(DataSeries.QS_QualityScore);
        baseSubstitutionCodeCodec = createDataWriter(DataSeries.BS_BaseSubstitutionCode);
        insertionCodec =            createDataWriter(DataSeries.IN_Insertion);
        softClipCodec =             createDataWriter(DataSeries.SC_SoftClip);
        hardClipCodec =             createDataWriter(DataSeries.HC_HardClip);
        paddingCodec =              createDataWriter(DataSeries.PD_padding);
        deletionLengthCodec =       createDataWriter(DataSeries.DL_DeletionLength);
        mappingQualityScoreCodec =  createDataWriter(DataSeries.MQ_MappingQualityScore);
        mateBitFlagsCodec =         createDataWriter(DataSeries.MF_MateBitFlags);
        nextFragmentReferenceSequenceIDCodec = createDataWriter(DataSeries.NS_NextFragmentReferenceSequenceID);
        nextFragmentAlignmentStart = createDataWriter(DataSeries.NP_NextFragmentAlignmentStart);
        templateSize =              createDataWriter(DataSeries.TS_InsertSize);
        tagIdListCodec =            createDataWriter(DataSeries.TL_TagIdList);
        refIdCodec =                createDataWriter(DataSeries.RI_RefId);
        refSkipCodec =              createDataWriter(DataSeries.RS_RefSkip);

        // special case: re-encodes QS as a byte array
        // This appears to split the QS_QualityScore series into a second codec that uses BYTE_ARRAY so that arrays of
        // scores are written to an EXTERNAL block ?
        // We can't call compressionHeader.createDataWriter here because it uses the default encoding params for
        // the QS_QualityScore data series, which is BYTE, not BYTE_ARRAY
        qualityScoreArrayCodec = new DataSeriesWriter<>(
                DataSeriesType.BYTE_ARRAY,
                compressionHeader.getEncodingMap().getEncodingDescriptorForDataSeries(DataSeries.QS_QualityScore),
                sliceBlocksWriteStreams);

        tagValueCodecs = compressionHeader.getTagEncodingMap().entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        mapEntry -> new DataSeriesWriter<>(
                                DataSeriesType.BYTE_ARRAY,
                                mapEntry.getValue(),
                                sliceBlocksWriteStreams)));
    }

    /**
     * Writes a series of Cram Compression Records to the underlying {@code SliceBlocks}, using this class's Encodings
     *
     * @param records the Cram Compression Records to write
     * @param initialAlignmentStart the alignmentStart of the enclosing {@link Slice}, for delta calculation
     * @return a {@link SliceBlocks} object
     */
    public SliceBlocks writeToSliceBlocks(final List<CRAMCompressionRecord> records, final int initialAlignmentStart) {
        int prevAlignmentStart = initialAlignmentStart;
        for (final CRAMCompressionRecord record : records) {
            writeCRAMRecord(record, prevAlignmentStart);
            prevAlignmentStart = record.getAlignmentStart();
        }
        return sliceBlocksWriteStreams.flushStreamsToBlocks();
    }

    /**
     * Look up a Data Series in the Cram Compression Header's Encoding Map.  If found, create a Data Writer
     *
     * @param dataSeries Which Data Series to write
     * @param <T> The Java data type associated with the Data Series
     * @return a Data Writer for the given Data Series, or null if it's not in the encoding map
     */
    private <T> DataSeriesWriter<T> createDataWriter(final DataSeries dataSeries) {
        final EncodingDescriptor encodingDescriptor = compressionHeader.getEncodingMap().getEncodingDescriptorForDataSeries(dataSeries);
        if (encodingDescriptor == null) {
            throw new IllegalArgumentException(
                    String.format("Attempt to create data series writer for data series %s for which no encoding can be found",
                            dataSeries));
        }
        return new DataSeriesWriter<>(
                dataSeries.getType(),
                encodingDescriptor,
                sliceBlocksWriteStreams);
    }

    /**
     * Write a CRAMCompressionRecord using the encodings for this writer.
     *
     * @param r the Cram Compression Record to write
     * @param prevAlignmentStart the alignmentStart of the previous record, for delta calculation
     */
    private void writeCRAMRecord(final CRAMCompressionRecord r, final int prevAlignmentStart) {

        // NOTE: Because it is legal to interleave multiple data series encodings within a single stream,
        // the order in which these are encoded (and decoded) is significant, and prescribed by the spec.
        bitFlagsCodec.writeData(r.getBAMFlags());
        cramBitFlagsCodec.writeData(r.getCRAMFlags());
        if (slice.getAlignmentContext().getReferenceContext().isMultiRef()) {
            refIdCodec.writeData(r.getReferenceIndex());
        }

        readLengthCodec.writeData(r.getReadLength());

        if (compressionHeader.isAPDelta()) {
            final int alignmentDelta = r.getAlignmentStart() - prevAlignmentStart;
            alignmentStartCodec.writeData(alignmentDelta);
        } else {
            alignmentStartCodec.writeData(r.getAlignmentStart());
        }

        readGroupCodec.writeData(r.getReadGroupID());

        if (compressionHeader.isPreserveReadNames()) {
            readNameCodec.writeData(r.getReadName().getBytes(charset));
        }

        // mate record:
        if (r.isDetached()) {
            mateBitFlagsCodec.writeData(r.getMateFlags());
            if (!compressionHeader.isPreserveReadNames()) {
                readNameCodec.writeData(r.getReadName().getBytes(charset));
            }

            nextFragmentReferenceSequenceIDCodec.writeData(r.getMateReferenceIndex());
            nextFragmentAlignmentStart.writeData(r.getMateAlignmentStart());
            templateSize.writeData(r.getTemplateSize());
        } else if (r.isHasMateDownStream()) {
            distanceToNextFragmentCodec.writeData(r.getRecordsToNextFragment());
        }

        // tag records:
        tagIdListCodec.writeData(r.getTagIdsIndex().value);
        if (r.getTags() != null) {
            for (int i = 0; i < r.getTags().size(); i++) {
                final DataSeriesWriter<byte[]> writer = tagValueCodecs.get(r.getTags().get(i).keyType3BytesAsInt);
                writer.writeData(r.getTags().get(i).getValueAsByteArray());
            }
        }

        if (!r.isSegmentUnmapped()) {
            // writing read features:
            final int featuresSize = r.getReadFeatures() == null ? 0 : r.getReadFeatures().size();
            numberOfReadFeaturesCodec.writeData(featuresSize);
            if (featuresSize != 0) {
                int prevPos = 0;
                for (final ReadFeature f : r.getReadFeatures()) {
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
                                baseSubstitutionCodeCodec.writeData(
                                        compressionHeader.getSubstitutionMatrix().code(sv.getReferenceBase(), sv.getBase()));
                            else
                                baseSubstitutionCodeCodec.writeData(sv.getCode());
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
                            //Note: htsjdk never generates these, it only consumes them
                            final BaseQualityScore bqs = (BaseQualityScore) f;
                            qualityScoreCodec.writeData(bqs.getQualityScore());
                            break;
                        case Bases.operator: // not implemented since the htsjdk implementation doesn't generate these
                        default:
                            throw new RuntimeException("Unknown read feature operator: " + (char) f.getOperator());
                    }
                }
            }

            // mapping quality:
            mappingQualityScoreCodec.writeData(r.getMappingQuality());
            if (r.isForcePreserveQualityScores()) {
                qualityScoreArrayCodec.writeData(r.getQualityScores());
            }
        } else {
            if (!r.isUnknownBases()) {
                for (final byte b : r.getReadBases()) {
                    baseCodec.writeData(b);
                }
            }

            if (r.isForcePreserveQualityScores()) {
                qualityScoreArrayCodec.writeData(r.getQualityScores());
            }
        }
    }
}
