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

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.structure.*;

import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A reader used to consume encoded CramRecords, via codecs, from a set of streams representing
 * a Slice's data series blocks.
 */
public class CramRecordReader {
    //NOTE: these are all named with a "Codec" suffix, but they're really DataSeriesReaders, which are
    // generic-typed wrappers around a CRAMCodec
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
    private final DataSeriesReader<Byte> qualityScoreCodec;
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
    private final DataSeriesReader<byte[]> scoresCodec;

    private final Charset charset = Charset.forName("UTF8");

    private final Slice slice;
    private final CompressionHeader compressionHeader;
    private final SliceBlocksReadStreams sliceBlocksReadStreams;
    protected final ValidationStringency validationStringency;

    private CramCompressionRecord prevRecord;

    //TODO: unused!
    private int recordCounter = 0;

    /**
     * Initialize a Cram Record Reader
     *
     * @param slice the slice into which the records should be reade
     * @param validationStringency how strict to be when reading this CRAM record
     */
    public CramRecordReader(final Slice slice, final ValidationStringency validationStringency) {
        this.slice = slice;
        this.compressionHeader = slice.getCompressionHeader();
        this.validationStringency = validationStringency;
        this.sliceBlocksReadStreams = new SliceBlocksReadStreams(slice.getSliceBlocks());

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
        qualityScoreCodec =             createDataSeriesReader(DataSeries.QS_QualityScore);
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
        basesCodec =                    createDataSeriesReader(DataSeries.BB_bases);
        scoresCodec =                   createDataSeriesReader(DataSeries.QQ_scores);

        // special case: re-encodes QS as a byte array
        // This appears to split the QS_QualityScore series into a second codec that uses BYTE_ARRAY so that arrays of
        // scores are read from an EXTERNAL block ?
        // We can't call compressionHeader.createDataReader here because it uses the default encoding params for
        // the QS_QualityScore data series, which is BYTE, not BYTE_ARRAY
        qualityScoreArrayCodec = new DataSeriesReader<>(
                DataSeriesType.BYTE_ARRAY,
                compressionHeader.getEncodingMap().getEncodingDescriptorForDataSeries(DataSeries.QS_QualityScore),
                sliceBlocksReadStreams);

        tagValueCodecs = compressionHeader.tMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        mapEntry -> new DataSeriesReader<>(
                                // TODO: why are tags always BYTE_ARRAY ? is that in the spec, or just a logical
                                // consequence/choice for tags
                                DataSeriesType.BYTE_ARRAY,
                                mapEntry.getValue(),
                                sliceBlocksReadStreams)));
    }

    /**
     * Read a Cram Compression Record, using this class's Encodings
     *
     * @param cramRecord the Cram Compression Record to read into
     * @param prevAlignmentStart the alignmentStart of the previous record, for delta calculation
     * @return the alignmentStart of the newly-read record
     */
    public int read(final CramCompressionRecord cramRecord, final int prevAlignmentStart) {

        // NOTE: Because it is legal to interleave multiple data series encodings within a single stream,
        // the order in which these are encoded (and decoded) is significant, and prescribed by the spec.
        cramRecord.flags = bitFlagsCodec.readData();
        cramRecord.compressionFlags = compressionBitFlagsCodec.readData();
        if (slice.getReferenceContext().isMultiRef()) {
            cramRecord.sequenceId = refIdCodec.readData();
        } else {
            // either unmapped (-1) or a valid ref
            cramRecord.sequenceId = slice.getReferenceContext().getSerializableId();
        }

        cramRecord.readLength = readLengthCodec.readData();
        if (compressionHeader.isCoordinateSorted()) {
            cramRecord.alignmentStart = prevAlignmentStart + alignmentStartCodec.readData();
        } else {
            cramRecord.alignmentStart = alignmentStartCodec.readData();
        }

        cramRecord.readGroupID = readGroupCodec.readData();

        if (compressionHeader.readNamesIncluded) {
            cramRecord.readName = new String(readNameCodec.readData(), charset);
        }

        // mate record:
        if (cramRecord.isDetached()) {
            cramRecord.mateFlags = mateBitFlagCodec.readData();
            if (!compressionHeader.readNamesIncluded) {
                cramRecord.readName = new String(readNameCodec.readData(), charset);
            }

            cramRecord.mateSequenceID = mateReferenceIdCodec.readData();
            cramRecord.mateAlignmentStart = mateAlignmentStartCodec.readData();
            cramRecord.templateSize = insertSizeCodec.readData();
        } else if (cramRecord.isHasMateDownStream()) {
            cramRecord.recordsToNextFragment = distanceToNextFragmentCodec.readData();
        }

        final Integer tagIdList = tagIdListCodec.readData();
        final byte[][] ids = compressionHeader.dictionary[tagIdList];
        if (ids.length > 0) {
            final int tagCount = ids.length;
            cramRecord.tags = new ReadTag[tagCount];
            for (int i = 0; i < ids.length; i++) {
                final int id = ReadTag.name3BytesToInt(ids[i]);
                final DataSeriesReader<byte[]> dataSeriesReader = tagValueCodecs.get(id);
                final ReadTag tag = new ReadTag(id, dataSeriesReader.readData(), validationStringency);
                cramRecord.tags[i] = tag;
            }
        }

        if (!cramRecord.isSegmentUnmapped()) {
            // reading read features:
            final int size = numberOfReadFeaturesCodec.readData();
            int prevPos = 0;
            final java.util.List<ReadFeature> readFeatures = new LinkedList<>();
            cramRecord.readFeatures = readFeatures;
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
                        final Substitution substitution = new Substitution();
                        substitution.setPosition(pos);
                        final byte code = baseSubstitutionCodec.readData();
                        substitution.setCode(code);
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
                        final HardClip hardCLip = new HardClip(pos, hardClipCodec.readData());
                        readFeatures.add(hardCLip);
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
                        final Scores scores = new Scores(pos, scoresCodec.readData());
                        readFeatures.add(scores);
                        break;
                    default:
                        throw new RuntimeException("Unknown read feature operator: " + operator);
                }
            }

            // mapping quality:
            cramRecord.mappingQuality = mappingScoreCodec.readData();
            if (cramRecord.isForcePreserveQualityScores()) {
                cramRecord.qualityScores = qualityScoreArrayCodec.readDataArray(cramRecord.readLength);
            }
        } else {
            if (cramRecord.isUnknownBases()) {
                cramRecord.readBases = SAMRecord.NULL_SEQUENCE;
                cramRecord.qualityScores = SAMRecord.NULL_QUALS;
            } else {
                final byte[] bases = new byte[cramRecord.readLength];
                for (int i = 0; i < bases.length; i++) {
                    bases[i] = baseCodec.readData();
                }

                cramRecord.readBases = bases;

                if (cramRecord.isForcePreserveQualityScores()) {
                    cramRecord.qualityScores = qualityScoreArrayCodec.readDataArray(cramRecord.readLength);
                }
            }
        }

        recordCounter++;
        prevRecord = cramRecord;

        return cramRecord.alignmentStart;
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
            // htsjdk implementation doesn't use `BB` and `QQ`; other implementations may choose to
            // omit other data series, so its ok to return null.
            return null;
        }
    }

}
