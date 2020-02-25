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

import htsjdk.samtools.SAMFlag;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.structure.*;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A reader used to consume and populate encoded {@link CRAMCompressionRecord}s from a set of streams representing data
 * series/blocks in a Slice. This is essentially a bridge between the various data series streams associated in
 * a {@link Slice} and the corresponding {@link CRAMCompressionRecord} fields.
 */
public final class CramRecordReader {
    private final DataSeriesReader<Integer> bitFlagsCodec;
    private final DataSeriesReader<Integer> compressionBitFlagsCodec;
    private final DataSeriesReader<Integer> readLengthCodec;
    private final DataSeriesReader<Integer> alignmentStartCodec;
    private final DataSeriesReader<Integer> readGroupCodec;
    private final DataSeriesReader<byte[]> readNameCodec;
    private final DataSeriesReader<Integer> distanceToNextFragmentCodec;
    private final Map<Integer, DataSeriesReader<byte[]>> tagValueCodecs;
    private final DataSeriesReader<Integer> numberOfReadFeaturesCodec;
    private final DataSeriesReader<Integer> readFeaturePositionCodec;
    private final DataSeriesReader<Byte> readFeatureCodeCodec;
    private final DataSeriesReader<Byte> baseCodec;
    private final DataSeriesReader<byte[]> qualityScoreArrayCodec;
    private final DataSeriesReader<Byte> baseSubstitutionCodec;
    private final DataSeriesReader<byte[]> insertionCodec;
    private final DataSeriesReader<byte[]> softClipCodec;
    private final DataSeriesReader<Integer> hardClipCodec;
    private final DataSeriesReader<Integer> paddingCodec;
    private final DataSeriesReader<Integer> deletionLengthCodec;
    private final DataSeriesReader<Integer> mappingScoreCodec;
    private final DataSeriesReader<Integer> mateBitFlagCodec;
    private final DataSeriesReader<Integer> mateReferenceIdCodec;
    private final DataSeriesReader<Integer> mateAlignmentStartCodec;
    private final DataSeriesReader<Integer> insertSizeCodec;
    private final DataSeriesReader<Integer> tagIdListCodec;
    private final DataSeriesReader<Integer> refIdCodec;
    private final DataSeriesReader<Integer> refSkipCodec;
    private final DataSeriesReader<byte[]> basesCodec;
    private final DataSeriesReader<Byte> qualityScoreCodec;
    private final DataSeriesReader<byte[]> qualityScoresCodec;

    private final Charset charset = Charset.forName("UTF8");

    private final Slice slice;
    private final CompressionHeader compressionHeader;
    private final SliceBlocksReadStreams sliceBlocksReadStreams;
    protected final ValidationStringency validationStringency;

    /**
     * Initialize a Cram Record Reader
     *
     * @param slice the slice into which the records should be read
     * @param validationStringency how strict to be when reading this CRAM record
     */
    public CramRecordReader(
            final Slice slice,
            final CompressorCache compressorCache,
            final ValidationStringency validationStringency) {
        this.slice = slice;
        this.compressionHeader = slice.getCompressionHeader();
        this.validationStringency = validationStringency;
        this.sliceBlocksReadStreams = new SliceBlocksReadStreams(slice.getSliceBlocks(), compressorCache);

        bitFlagsCodec =                 createDataSeriesReader(DataSeries.BF_BitFlags);
        compressionBitFlagsCodec =      createDataSeriesReader(DataSeries.CF_CompressionBitFlags);
        readLengthCodec =               createDataSeriesReader(DataSeries.RL_ReadLength);
        alignmentStartCodec =           createDataSeriesReader(DataSeries.AP_AlignmentPositionOffset);
        readGroupCodec =                createDataSeriesReader(DataSeries.RG_ReadGroup);
        readNameCodec =                 createDataSeriesReader(DataSeries.RN_ReadName);
        distanceToNextFragmentCodec =   createDataSeriesReader(DataSeries.NF_RecordsToNextFragment);
        numberOfReadFeaturesCodec =     createDataSeriesReader(DataSeries.FN_NumberOfReadFeatures);
        readFeaturePositionCodec =      createDataSeriesReader(DataSeries.FP_FeaturePosition);
        readFeatureCodeCodec =          createDataSeriesReader(DataSeries.FC_FeatureCode);
        baseCodec =                     createDataSeriesReader(DataSeries.BA_Base);
        baseSubstitutionCodec =         createDataSeriesReader(DataSeries.BS_BaseSubstitutionCode);
        insertionCodec =                createDataSeriesReader(DataSeries.IN_Insertion);
        softClipCodec =                 createDataSeriesReader(DataSeries.SC_SoftClip);
        hardClipCodec =                 createDataSeriesReader(DataSeries.HC_HardClip);
        paddingCodec =                  createDataSeriesReader(DataSeries.PD_padding);
        deletionLengthCodec =           createDataSeriesReader(DataSeries.DL_DeletionLength);
        mappingScoreCodec =             createDataSeriesReader(DataSeries.MQ_MappingQualityScore);
        mateBitFlagCodec =              createDataSeriesReader(DataSeries.MF_MateBitFlags);
        mateReferenceIdCodec =          createDataSeriesReader(DataSeries.NS_NextFragmentReferenceSequenceID);
        mateAlignmentStartCodec =       createDataSeriesReader(DataSeries.NP_NextFragmentAlignmentStart);
        insertSizeCodec =               createDataSeriesReader(DataSeries.TS_InsertSize);
        tagIdListCodec =                createDataSeriesReader(DataSeries.TL_TagIdList);
        refIdCodec =                    createDataSeriesReader(DataSeries.RI_RefId);
        refSkipCodec =                  createDataSeriesReader(DataSeries.RS_RefSkip);
        basesCodec =                    createDataSeriesReader(DataSeries.BB_Bases);
        qualityScoreCodec =             createDataSeriesReader(DataSeries.QS_QualityScore);
        qualityScoresCodec =            createDataSeriesReader(DataSeries.QQ_scores);

        // special case: re-encodes QS as a byte array
        // This appears to split the QS_QualityScore series into a second codec that uses BYTE_ARRAY so that arrays of
        // scores are read from an EXTERNAL block ?
        // We can't call compressionHeader.createDataReader here because it uses the default encoding params for
        // the QS_QualityScore data series, which is BYTE, not BYTE_ARRAY
        qualityScoreArrayCodec = new DataSeriesReader<>(
                DataSeriesType.BYTE_ARRAY,
                compressionHeader.getEncodingMap().getEncodingDescriptorForDataSeries(DataSeries.QS_QualityScore),
                sliceBlocksReadStreams);

        tagValueCodecs = compressionHeader.getTagEncodingMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        mapEntry -> new DataSeriesReader<>(
                                // consequence/choice for tags
                                DataSeriesType.BYTE_ARRAY,
                                mapEntry.getValue(),
                                sliceBlocksReadStreams)));
    }

    /**
     * Read a CRAMCompressionRecord, using this reader's data series readers.
     *
     * @param prevAlignmentStart the alignmentStart of the previous record, for position delta calculation
     * @return the newly-read CRAMCompressionRecord
     */
    public CRAMCompressionRecord readCRAMRecord(
            final long sequentialIndex,
            final int prevAlignmentStart) {
        // NOTE: Because it is legal to interleave multiple data series encodings within a single stream,
        // the order in which these are encoded (and decoded) is significant, and prescribed by the spec.
        int bamFlags = bitFlagsCodec.readData();
        final int cramFlags = compressionBitFlagsCodec.readData();

        // decode positions
        int referenceIndex;
        if (slice.getAlignmentContext().getReferenceContext().isMultiRef()) {
            referenceIndex = refIdCodec.readData();
        } else {
            // either unmapped (-1) or a valid ref
            referenceIndex = slice.getAlignmentContext().getReferenceContext().getReferenceContextID();
        }

        final int readLength = readLengthCodec.readData();

        int alignmentStart;
        if (compressionHeader.isAPDelta()) {
            // note that its legal to have negative alignmentStart deltas
            alignmentStart = prevAlignmentStart + alignmentStartCodec.readData();
        } else {
            alignmentStart = alignmentStartCodec.readData();
        }

        int readGroupID = readGroupCodec.readData();

        String readName = null;
        if (compressionHeader.isPreserveReadNames()) {
            readName = new String(readNameCodec.readData(), charset);
        } //read names are generated for the entire slice later

        // mate record:
        int mateFlags = 0;
        int mateSequenceID = SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX;
        int mateAlignmentStart = 0;
        int templateSize = 0;
        int recordsToNextFragment = -1;

        if (CRAMCompressionRecord.isDetached(cramFlags)) {
            mateFlags = mateBitFlagCodec.readData();
            // CRAM write implementations are not required to preserve these BAM flags directly in the
            // BAM Flags series, so we have to propagate them from mate flags just in case.
            if ((mateFlags & CRAMCompressionRecord.MF_MATE_NEG_STRAND) != 0) {
                bamFlags |= SAMFlag.MATE_REVERSE_STRAND.intValue();
            }
            if ((mateFlags & CRAMCompressionRecord.MF_MATE_UNMAPPED) != 0) {
                bamFlags |= SAMFlag.MATE_UNMAPPED.intValue();
            }
            //NOTE: The spec prescribes that readName must be decoded AFTER mateFlags in the case
            //where read names are not preserved
            if (!compressionHeader.isPreserveReadNames()) {
                readName = new String(readNameCodec.readData(), charset);
            }

            mateSequenceID = mateReferenceIdCodec.readData();
            mateAlignmentStart = mateAlignmentStartCodec.readData();
            templateSize = insertSizeCodec.readData();
        } else if (CRAMCompressionRecord.isHasMateDownStream(cramFlags)) {
            recordsToNextFragment = distanceToNextFragmentCodec.readData();
            // this record's bam flags will be updated once the next fragment
            // is resolved
        }

        List<ReadTag> readTags = null;
        final Integer tagIdList = tagIdListCodec.readData();
        final byte[][] ids = compressionHeader.getTagIDDictionary()[tagIdList];
        if (ids.length > 0) {
            readTags = new ArrayList<>(ids.length);
            for (int i = 0; i < ids.length; i++) {
                final int id = ReadTag.name3BytesToInt(ids[i]);
                final DataSeriesReader<byte[]> dataSeriesReader = tagValueCodecs.get(id);
                final ReadTag tag = new ReadTag(id, dataSeriesReader.readData(), validationStringency);
                readTags.add(tag);
            }
        }

        int mappingQuality = 0;
        List<ReadFeature> readFeatures = null;
        byte[] readBases = SAMRecord.NULL_SEQUENCE;
        byte[] qualityScores = SAMRecord.NULL_QUALS;

        if (!CRAMCompressionRecord.isSegmentUnmapped(bamFlags)) {
            // reading read features:
            final int size = numberOfReadFeaturesCodec.readData();
            int prevPos = 0;
            if ( size > 0) {
                readFeatures = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    final Byte operator = readFeatureCodeCodec.readData();

                    final int pos = prevPos + readFeaturePositionCodec.readData();
                    prevPos = pos;

                    switch (operator) {
                        case ReadBase.operator:
                            final ReadBase readBase = new ReadBase(pos, baseCodec.readData(), qualityScoreCodec.readData());
                            readFeatures.add(readBase);
                            break;
                        case Substitution.operator:
                            final byte code = baseSubstitutionCodec.readData();
                            final Substitution substitution = new Substitution(pos, code);
                            readFeatures.add(substitution);
                            break;
                        case Insertion.operator:
                            final Insertion insertion = new Insertion(pos, insertionCodec.readData());
                            readFeatures.add(insertion);
                            break;
                        case SoftClip.operator:
                            final SoftClip softClip = new SoftClip(pos, softClipCodec.readData());
                            readFeatures.add(softClip);
                            break;
                        case HardClip.operator:
                            final HardClip hardClip = new HardClip(pos, hardClipCodec.readData());
                            readFeatures.add(hardClip);
                            break;
                        case Padding.operator:
                            final Padding padding = new Padding(pos, paddingCodec.readData());
                            readFeatures.add(padding);
                            break;
                        case Deletion.operator:
                            final Deletion deletion = new Deletion(pos, deletionLengthCodec.readData());
                            readFeatures.add(deletion);
                            break;
                        case RefSkip.operator:
                            final RefSkip refSkip = new RefSkip(pos, refSkipCodec.readData());
                            readFeatures.add(refSkip);
                            break;
                        case InsertBase.operator:
                            final InsertBase insertBase = new InsertBase(pos, baseCodec.readData());
                            readFeatures.add(insertBase);
                            break;
                        case BaseQualityScore.operator:
                            final BaseQualityScore baseQualityScore = new BaseQualityScore(pos, qualityScoreCodec.readData());
                            readFeatures.add(baseQualityScore);
                            break;
                        case Bases.operator:
                            final Bases bases = new Bases(pos, basesCodec.readData());
                            readFeatures.add(bases);
                            break;
                        case Scores.operator:
                            final Scores scores = new Scores(pos, qualityScoresCodec.readData());
                            readFeatures.add(scores);
                            break;
                        default:
                            throw new RuntimeException("Unknown read feature operator: " + operator);
                    }
                }
            }

            mappingQuality = mappingScoreCodec.readData();;
            if (CRAMCompressionRecord.isForcePreserveQualityScores(cramFlags)) {
                qualityScores = qualityScoreArrayCodec.readDataArray(readLength);
            }
        } else {
            if (!CRAMCompressionRecord.isUnknownBases(cramFlags)) {
                readBases = new byte[readLength];
                for (int i = 0; i < readBases.length; i++) {
                    readBases[i] = baseCodec.readData();
                }
                if (CRAMCompressionRecord.isForcePreserveQualityScores(cramFlags)) {
                    qualityScores = qualityScoreArrayCodec.readDataArray(readLength);
                }
            }
        }

        return new CRAMCompressionRecord(
                sequentialIndex,
                bamFlags,
                cramFlags,
                readName,
                readLength,
                referenceIndex,
                alignmentStart,
                templateSize,
                mappingQuality,
                qualityScores,
                readBases,
                readTags,
                readFeatures,
                readGroupID,
                mateFlags,
                mateSequenceID,
                mateAlignmentStart,
                recordsToNextFragment);
    }

    private <T> DataSeriesReader<T> createDataSeriesReader(final DataSeries dataSeries) {
        final EncodingDescriptor encodingDescriptor = compressionHeader.getEncodingMap().getEncodingDescriptorForDataSeries(dataSeries);
        if (encodingDescriptor != null) {
            return new DataSeriesReader<>(
                    dataSeries.getType(),
                    encodingDescriptor,
                    sliceBlocksReadStreams);
        } else {
            // NOTE: Not all CRAM implementations choose to use all data series. For example, the
            // htsjdk write implementation doesn't use `BB` and `QQ`; other implementations may choose to
            // omit other data series, so its ok to return null.
            return null;
        }
    }

}
