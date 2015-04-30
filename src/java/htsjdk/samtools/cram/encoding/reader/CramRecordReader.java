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

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.read_features.BaseQualityScore;
import htsjdk.samtools.cram.encoding.read_features.Bases;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.HardClip;
import htsjdk.samtools.cram.encoding.read_features.InsertBase;
import htsjdk.samtools.cram.encoding.read_features.Insertion;
import htsjdk.samtools.cram.encoding.read_features.Padding;
import htsjdk.samtools.cram.encoding.read_features.ReadBase;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.RefSkip;
import htsjdk.samtools.cram.encoding.read_features.Scores;
import htsjdk.samtools.cram.encoding.read_features.SoftClip;
import htsjdk.samtools.cram.encoding.read_features.Substitution;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;

import java.util.LinkedList;

public class CramRecordReader extends AbstractReader {
    private CramCompressionRecord prevRecord;

    @SuppressWarnings("ConstantConditions")
    public void read(final CramCompressionRecord r) {
        try {
            // int mark = testCodec.readData();
            // if (Writer.TEST_MARK != mark) {
            // System.err.println("Record counter=" + recordCount);
            // System.err.println(r.toString());
            // throw new RuntimeException("Test mark not found.");
            // }

            r.flags = bitFlagsCodec.readData();
            r.compressionFlags = compressionBitFlagsCodec.readData();
            if (refId == -2)
                r.sequenceId = refIdCodec.readData();
            else
                r.sequenceId = refId;

            r.readLength = readLengthCodec.readData();
            if (AP_delta)
                r.alignmentDelta = alignmentStartCodec.readData();
            else
                r.alignmentStart = alignmentStartCodec.readData();
            r.readGroupID = readGroupCodec.readData();

            if (captureReadNames)
                r.readName = new String(readNameCodec.readData(), charset);

            // mate record:
            if (r.isDetached()) {
                r.mateFlags = mateBitFlagCodec.readData();
                if (!captureReadNames)
                    r.readName = new String(readNameCodec.readData(), charset);

                r.mateSequenceID = mateReferenceIdCodec.readData();
                r.mateAlignmentStart = mateAlignmentStartCodec.readData();
                r.templateSize = insertSizeCodec.readData();
                detachedCount++;
            } else if (r.isHasMateDownStream())
                r.recordsToNextFragment = distanceToNextFragmentCodec.readData();

            final Integer tagIdList = tagIdListCodec.readData();
            final byte[][] ids = tagIdDictionary[tagIdList];
            if (ids.length > 0) {
                final int tagCount = ids.length;
                r.tags = new ReadTag[tagCount];
                for (int i = 0; i < ids.length; i++) {
                    final int id = ReadTag.name3BytesToInt(ids[i]);
                    final DataReader<byte[]> dataReader = tagValueCodecs.get(id);
                    final ReadTag tag = new ReadTag(id, dataReader.readData());
                    r.tags[i] = tag;
                }
            }

            if (!r.isSegmentUnmapped()) {
                // reading read features:
                final int size = numberOfReadFeaturesCodec.readData();
                int prevPos = 0;
                final java.util.List<ReadFeature> rf = new LinkedList<ReadFeature>();
                r.readFeatures = rf;
                for (int i = 0; i < size; i++) {
                    final Byte operator = readFeatureCodeCodec.readData();

                    final int pos = prevPos + readFeaturePositionCodec.readData();
                    prevPos = pos;

                    switch (operator) {
                        case ReadBase.operator:
                            final ReadBase rb = new ReadBase(pos, baseCodec.readData(), qualityScoreCodec.readData());
                            rf.add(rb);
                            break;
                        case Substitution.operator:
                            final Substitution sv = new Substitution();
                            sv.setPosition(pos);
                            final byte code = baseSubstitutionCodec.readData();
                            sv.setCode(code);
                            rf.add(sv);
                            break;
                        case Insertion.operator:
                            final Insertion iv = new Insertion(pos, insertionCodec.readData());
                            rf.add(iv);
                            break;
                        case SoftClip.operator:
                            final SoftClip fv = new SoftClip(pos, softClipCodec.readData());
                            rf.add(fv);
                            break;
                        case HardClip.operator:
                            final HardClip hv = new HardClip(pos, hardClipCodec.readData());
                            rf.add(hv);
                            break;
                        case Padding.operator:
                            final Padding pv = new Padding(pos, paddingCodec.readData());
                            rf.add(pv);
                            break;
                        case Deletion.operator:
                            final Deletion dv = new Deletion(pos, deletionLengthCodec.readData());
                            rf.add(dv);
                            break;
                        case RefSkip.operator:
                            final RefSkip rsv = new RefSkip(pos, refSkipCodec.readData());
                            rf.add(rsv);
                            break;
                        case InsertBase.operator:
                            final InsertBase ib = new InsertBase(pos, baseCodec.readData());
                            rf.add(ib);
                            break;
                        case BaseQualityScore.operator:
                            final BaseQualityScore bqs = new BaseQualityScore(pos, qualityScoreCodec.readData());
                            rf.add(bqs);
                            break;
                        case Bases.operator:
                            final Bases bases = new Bases(pos, basesCodec.readData());
                            rf.add(bases);
                            break;
                        case Scores.operator:
                            final Scores scores = new Scores(pos, scoresCodec.readData());
                            rf.add(scores);
                            break;
                        default:
                            throw new RuntimeException("Unknown read feature operator: " + operator);
                    }
                }

                // mapping quality:
                r.mappingQuality = mappingScoreCodec.readData();
                if (r.isForcePreserveQualityScores()) {
                    r.qualityScores = qualityScoresCodec.readDataArray(r.readLength);
                }
            } else {
                if (r.isUnknownBases()) {
                    r.readBases = SAMRecord.NULL_SEQUENCE;
                    r.qualityScores = SAMRecord.NULL_QUALS;
                } else {
                    final byte[] bases = new byte[r.readLength];
                    for (int i = 0; i < bases.length; i++)
                        bases[i] = baseCodec.readData();
                    r.readBases = bases;


                    if (r.isForcePreserveQualityScores()) {
                        r.qualityScores = qualityScoresCodec.readDataArray(r.readLength);
                    }
                }
            }

            recordCounter++;

            prevRecord = r;
        } catch (final Exception e) {
            if (prevRecord != null)
                System.err.printf("Failed at record %d. Here is the previously read record: %s\n", recordCounter,
                        prevRecord.toString());
            throw new RuntimeException(e);
        }
    }
}
