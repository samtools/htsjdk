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
import htsjdk.samtools.cram.encoding.readfeatures.BaseQualityScore;
import htsjdk.samtools.cram.encoding.readfeatures.Bases;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.HardClip;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.Padding;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.RefSkip;
import htsjdk.samtools.cram.encoding.readfeatures.Scores;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;

import java.util.LinkedList;

public class CramRecordReader extends AbstractReader {
    private CramCompressionRecord prevRecord;
    private ValidationStringency validationStringency;

    public CramRecordReader(ValidationStringency validationStringency) {
        this.validationStringency = validationStringency;
    }

    @SuppressWarnings("ConstantConditions")
    public void read(final CramCompressionRecord cramRecord) {
        try {
            // int mark = testCodec.readData();
            // if (Writer.TEST_MARK != mark) {
            // System.err.println("Record counter=" + recordCount);
            // System.err.println(cramRecord.toString());
            // throw new RuntimeException("Test mark not found.");
            // }

            cramRecord.flags = bitFlagsCodec.readData();
            cramRecord.compressionFlags = compressionBitFlagsCodec.readData();
            if (refId == -2)
                cramRecord.sequenceId = refIdCodec.readData();
            else
                cramRecord.sequenceId = refId;

            cramRecord.readLength = readLengthCodec.readData();
            if (APDelta)
                cramRecord.alignmentDelta = alignmentStartCodec.readData();
            else
                cramRecord.alignmentStart = alignmentStartCodec.readData();
            cramRecord.readGroupID = readGroupCodec.readData();

            if (captureReadNames)
                cramRecord.readName = new String(readNameCodec.readData(), charset);

            // mate record:
            if (cramRecord.isDetached()) {
                cramRecord.mateFlags = mateBitFlagCodec.readData();
                if (!captureReadNames)
                    cramRecord.readName = new String(readNameCodec.readData(), charset);

                cramRecord.mateSequenceID = mateReferenceIdCodec.readData();
                cramRecord.mateAlignmentStart = mateAlignmentStartCodec.readData();
                cramRecord.templateSize = insertSizeCodec.readData();
                detachedCount++;
            } else if (cramRecord.isHasMateDownStream())
                cramRecord.recordsToNextFragment = distanceToNextFragmentCodec.readData();

            final Integer tagIdList = tagIdListCodec.readData();
            final byte[][] ids = tagIdDictionary[tagIdList];
            if (ids.length > 0) {
                final int tagCount = ids.length;
                cramRecord.tags = new ReadTag[tagCount];
                for (int i = 0; i < ids.length; i++) {
                    final int id = ReadTag.name3BytesToInt(ids[i]);
                    final DataReader<byte[]> dataReader = tagValueCodecs.get(id);
                    final ReadTag tag = new ReadTag(id, dataReader.readData(), validationStringency);
                    cramRecord.tags[i] = tag;
                }
            }

            if (!cramRecord.isSegmentUnmapped()) {
                // reading read features:
                final int size = numberOfReadFeaturesCodec.readData();
                int prevPos = 0;
                final java.util.List<ReadFeature> readFeatures = new LinkedList<ReadFeature>();
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
                    cramRecord.qualityScores = qualityScoresCodec.readDataArray(cramRecord.readLength);
                }
            } else {
                if (cramRecord.isUnknownBases()) {
                    cramRecord.readBases = SAMRecord.NULL_SEQUENCE;
                    cramRecord.qualityScores = SAMRecord.NULL_QUALS;
                } else {
                    final byte[] bases = new byte[cramRecord.readLength];
                    for (int i = 0; i < bases.length; i++)
                        bases[i] = baseCodec.readData();
                    cramRecord.readBases = bases;


                    if (cramRecord.isForcePreserveQualityScores()) {
                        cramRecord.qualityScores = qualityScoresCodec.readDataArray(cramRecord.readLength);
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
            throw new RuntimeException(e);
        }
    }
}
