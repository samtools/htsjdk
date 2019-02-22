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

import htsjdk.samtools.SAMFormatException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.encoding.readfeatures.*;
import htsjdk.samtools.cram.structure.*;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.Map;
import java.util.stream.Collectors;

public class CramRecordReader {
    private final DataSeriesReader<Integer> bitFlagsCodec;
    private final DataSeriesReader<Byte> compressionBitFlagsCodec;
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
    private final DataSeriesReader<Byte> mateBitFlagCodec;
    private final DataSeriesReader<Integer> mateReferenceIdCodec;
    private final DataSeriesReader<Integer> mateAlignmentStartCodec;
    private final DataSeriesReader<Integer> insertSizeCodec;
    private final DataSeriesReader<Integer> tagIdListCodec;
    private final DataSeriesReader<Integer> refIdCodec;
    private final DataSeriesReader<Integer> refSkipCodec;
    private final DataSeriesReader<byte[]> basesCodec;
    private final DataSeriesReader<byte[]> scoresCodec;

    private final Charset charset = Charset.forName("UTF8");

    private final boolean captureReadNames;
    private final byte[][][] tagIdDictionary;
    private final int refId;
    protected final ValidationStringency validationStringency;

    protected final boolean APDelta;

    private final Map<DataSeries, EncodingParams> encodingMap;
    private final BitInputStream coreBlockInputStream;
    private final Map<Integer, ByteArrayInputStream> externalBlockInputMap;

    private CramCompressionRecord prevRecord;
    private int recordCounter = 0;


    /**
     * Initialize a Cram Record Reader
     *
     * @param coreInputStream Core data block bit stream, to be read by non-external Encodings
     * @param externalInputMap External data block byte stream map, to be read by external Encodings
     * @param header the associated Cram Compression Header
     * @param refId the reference sequence ID to assign to these records
     * @param validationStringency how strict to be when reading this CRAM record
     */
    public CramRecordReader(final BitInputStream coreInputStream,
                            final Map<Integer, ByteArrayInputStream> externalInputMap,
                            final CompressionHeader header,
                            final int refId,
                            final ValidationStringency validationStringency) {
        this.captureReadNames = header.readNamesIncluded;
        this.tagIdDictionary = header.dictionary;
        this.refId = refId;
        this.validationStringency = validationStringency;
        this.APDelta = header.APDelta;

        this.encodingMap = header.encodingMap;
        this.coreBlockInputStream = coreInputStream;
        this.externalBlockInputMap = externalInputMap;

        bitFlagsCodec =                 createDataReader(DataSeries.BF_BitFlags);
        compressionBitFlagsCodec =      createDataReader(DataSeries.CF_CompressionBitFlags);
        readLengthCodec =               createDataReader(DataSeries.RL_ReadLength);
        alignmentStartCodec =           createDataReader(DataSeries.AP_AlignmentPositionOffset);
        readGroupCodec =                createDataReader(DataSeries.RG_ReadGroup);
        readNameCodec =                 createDataReader(DataSeries.RN_ReadName);
        distanceToNextFragmentCodec =   createDataReader(DataSeries.NF_RecordsToNextFragment);
        numberOfReadFeaturesCodec =     createDataReader(DataSeries.FN_NumberOfReadFeatures);
        readFeaturePositionCodec =      createDataReader(DataSeries.FP_FeaturePosition);
        readFeatureCodeCodec =          createDataReader(DataSeries.FC_FeatureCode);
        baseCodec =                     createDataReader(DataSeries.BA_Base);
        qualityScoreCodec =             createDataReader(DataSeries.QS_QualityScore);
        baseSubstitutionCodec =         createDataReader(DataSeries.BS_BaseSubstitutionCode);
        insertionCodec =                createDataReader(DataSeries.IN_Insertion);
        softClipCodec =                 createDataReader(DataSeries.SC_SoftClip);
        hardClipCodec =                 createDataReader(DataSeries.HC_HardClip);
        paddingCodec =                  createDataReader(DataSeries.PD_padding);
        deletionLengthCodec =           createDataReader(DataSeries.DL_DeletionLength);
        mappingScoreCodec =             createDataReader(DataSeries.MQ_MappingQualityScore);
        mateBitFlagCodec =              createDataReader(DataSeries.MF_MateBitFlags);
        mateReferenceIdCodec =          createDataReader(DataSeries.NS_NextFragmentReferenceSequenceID);
        mateAlignmentStartCodec =       createDataReader(DataSeries.NP_NextFragmentAlignmentStart);
        insertSizeCodec =               createDataReader(DataSeries.TS_InsertSize);
        tagIdListCodec =                createDataReader(DataSeries.TL_TagIdList);
        refIdCodec =                    createDataReader(DataSeries.RI_RefId);
        refSkipCodec =                  createDataReader(DataSeries.RS_RefSkip);
        basesCodec =                    createDataReader(DataSeries.BB_bases);
        scoresCodec =                   createDataReader(DataSeries.QQ_scores);

        // special case: re-encodes QS as a byte array
        qualityScoreArrayCodec = new DataSeriesReader<>(DataSeriesType.BYTE_ARRAY, header.encodingMap.get(DataSeries.QS_QualityScore), coreInputStream, externalInputMap);

        tagValueCodecs = header.tMap.entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        mapEntry -> new DataSeriesReader<>(DataSeriesType.BYTE_ARRAY, mapEntry.getValue(), coreInputStream, externalInputMap)));
    }

    /**
     * Look up a Data Series in the Cram Compression Header's Encoding Map.  If found, create a Data Reader
     *
     * @param dataSeries Which Data Series to write
     * @param <T> The Java data type associated with the Data Series
     * @return a Data Reader for the given Data Series, or null if it's not in the encoding map
     */
    private <T> DataSeriesReader<T> createDataReader(DataSeries dataSeries) {
        if (encodingMap.containsKey(dataSeries)) {
            return new DataSeriesReader<>(dataSeries.getType(), encodingMap.get(dataSeries), coreBlockInputStream, externalBlockInputMap);
        } else {
            return null;
        }
    }

    /**
     * Read a Cram Compression Record, using this class's Encodings
     *
     * @param cramRecord the Cram Compression Record to read into
     */
    public void read(final CramCompressionRecord cramRecord, final int prevAlignmentStart) {
        try {
            // int mark = testCodec.readData();
            // if (Writer.TEST_MARK != mark) {
            // System.err.println("Record counter=" + recordCount);
            // System.err.println(cramRecord.toString());
            // throw new RuntimeException("Test mark not found.");
            // }

            cramRecord.flags = bitFlagsCodec.readData();
            cramRecord.compressionFlags = compressionBitFlagsCodec.readData();
            if (refId == Slice.MULTI_REFERENCE) {
                cramRecord.sequenceId = refIdCodec.readData();
            } else {
                cramRecord.sequenceId = refId;
            }

            cramRecord.readLength = readLengthCodec.readData();
            if (APDelta) {
                cramRecord.alignmentStart = prevAlignmentStart + alignmentStartCodec.readData();
            }
            else {
                cramRecord.alignmentStart = alignmentStartCodec.readData();
            }

            cramRecord.readGroupID = readGroupCodec.readData();

            if (captureReadNames) {
                cramRecord.readName = new String(readNameCodec.readData(), charset);
            }

            // mate record:
            if (cramRecord.isDetached()) {
                cramRecord.mateFlags = mateBitFlagCodec.readData();
                if (!captureReadNames) {
                    cramRecord.readName = new String(readNameCodec.readData(), charset);
                }

                cramRecord.mateSequenceID = mateReferenceIdCodec.readData();
                cramRecord.mateAlignmentStart = mateAlignmentStartCodec.readData();
                cramRecord.templateSize = insertSizeCodec.readData();
            } else if (cramRecord.isHasMateDownStream()) {
                cramRecord.recordsToNextFragment = distanceToNextFragmentCodec.readData();
            }

            final Integer tagIdList = tagIdListCodec.readData();
            final byte[][] ids = tagIdDictionary[tagIdList];
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
        }
        catch (final SAMFormatException e) {
            if (prevRecord != null) {
                System.err.printf("Failed at record %d. Here is the previously read record: %s\n", recordCounter,
                        prevRecord.toString());
            }
            throw e;
        }
        catch (final Exception e) {
            if (prevRecord != null) {
                System.err.printf("Failed at record %d. Here is the previously read record: %s\n", recordCounter,
                        prevRecord.toString());
            }
            throw new RuntimeIOException(e);
        }
    }

}
