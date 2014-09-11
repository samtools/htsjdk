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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.read_features.BaseQualityScore;
import htsjdk.samtools.cram.encoding.read_features.Deletion;
import htsjdk.samtools.cram.encoding.read_features.InsertBase;
import htsjdk.samtools.cram.encoding.read_features.Insertion;
import htsjdk.samtools.cram.encoding.read_features.ReadBase;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.encoding.read_features.SoftClip;
import htsjdk.samtools.cram.encoding.read_features.Substitution;
import htsjdk.samtools.cram.ref.ReferenceSource;
import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.SubstitutionMatrix;
import htsjdk.samtools.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CramNormalizer {
    private SAMFileHeader header;
    private int readCounter = 0;
    private String readNamePrefix = "";
    private byte defaultQualityScore = '?' - '!';

    private static Log log = Log.getInstance(CramNormalizer.class);
    private ReferenceSource referenceSource;

    public CramNormalizer(SAMFileHeader header) {
        this.header = header;
    }

    public CramNormalizer(SAMFileHeader header, ReferenceSource referenceSource) {
        this.header = header;
        this.referenceSource = referenceSource;
    }

    public void normalize(ArrayList<CramCompressionRecord> records, boolean resetPairing,
                          byte[] ref, int alignmentStart,
                          SubstitutionMatrix substitutionMatrix, boolean AP_delta) {

        int startCounter = readCounter;

        for (CramCompressionRecord r : records) {
            r.index = ++readCounter;

            if (r.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                r.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                r.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            } else {
                r.sequenceName = header.getSequence(r.sequenceId)
                        .getSequenceName();
            }
        }

        {// restore pairing first:
            for (CramCompressionRecord r : records) {
                if (!r.isMultiFragment() || r.isDetached()) {
                    r.recordsToNextFragment = -1;

                    r.next = null;
                    r.previous = null;
                    continue;
                }
                if (r.isHasMateDownStream()) {
                    CramCompressionRecord downMate = records.get(r.index
                            + r.recordsToNextFragment - startCounter);
                    r.next = downMate;
                    downMate.previous = r;

                    r.mateAlignmentStart = downMate.alignmentStart;
                    r.setMateUmapped(downMate.isSegmentUnmapped());
                    r.setMateNegativeStrand(downMate.isNegativeStrand());
                    r.mateSequenceID = downMate.sequenceId;
                    if (r.mateSequenceID == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                        r.mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;

                    downMate.mateAlignmentStart = r.alignmentStart;
                    downMate.setMateUmapped(r.isSegmentUnmapped());
                    downMate.setMateNegativeStrand(r.isNegativeStrand());
                    downMate.mateSequenceID = r.sequenceId;
                    if (downMate.mateSequenceID == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
                        downMate.mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;

                    if (r.isFirstSegment()) {
                        final int tlen = computeInsertSize(r, downMate);
                        r.templateSize = tlen;
                        downMate.templateSize = -tlen;
                    } else {
                        final int tlen = computeInsertSize(downMate, r);
                        downMate.templateSize = tlen;
                        r.templateSize = -tlen;
                    }
                }
            }
        }

        // assign some read names if needed:
        for (CramCompressionRecord r : records) {
            if (r.readName == null) {
                String name = readNamePrefix + r.index;
                r.readName = name;
                if (r.next != null)
                    r.next.readName = name;
                if (r.previous != null)
                    r.previous.readName = name;
            }
        }

        // resolve bases:
        for (CramCompressionRecord r : records) {
            if (r.isSegmentUnmapped())
                continue;

            byte[] refBases = ref;
            if (referenceSource != null)
                refBases = referenceSource.getReferenceBases(header.getSequence(r.sequenceId), true);

            byte[] bases = restoreReadBases(r, refBases, substitutionMatrix);
            r.readBases = bases;
        }

        // restore quality scores:
        restoreQualityScores(defaultQualityScore, records);
    }

    public static void restoreQualityScores(byte defaultQualityScore,
                                            List<CramCompressionRecord> records) {
        for (CramCompressionRecord record : records)
            restoreQualityScores(defaultQualityScore, record);
    }

    public static byte[] restoreQualityScores(byte defaultQualityScore,
                                              CramCompressionRecord record) {
        if (!record.isForcePreserveQualityScores()) {
            byte[] scores = new byte[record.readLength];
            Arrays.fill(scores, defaultQualityScore);
            if (record.readFeatures != null)
                for (ReadFeature f : record.readFeatures) {
                    switch (f.getOperator()) {
                        case BaseQualityScore.operator:
                            int pos = f.getPosition();
                            byte q = ((BaseQualityScore) f).getQualityScore();

                            try {
                                scores[pos - 1] = q;
                            } catch (ArrayIndexOutOfBoundsException e) {
                                System.err.println("PROBLEM CAUSED BY:");
                                System.err.println(record.toString());
                                throw e;
                            }
                            break;
                        case ReadBase.operator:
                            pos = f.getPosition();
                            q = ((ReadBase) f).getQualityScore();

                            try {
                                scores[pos - 1] = q;
                            } catch (ArrayIndexOutOfBoundsException e) {
                                System.err.println("PROBLEM CAUSED BY:");
                                System.err.println(record.toString());
                                throw e;
                            }
                            break;

                        default:
                            break;
                    }

                }

            record.qualityScores = scores;
        } else {
            byte[] scores = record.qualityScores;
            for (int i = 0; i < scores.length; i++)
                if (scores[i] == -1)
                    scores[i] = defaultQualityScore;
        }

        return record.qualityScores;
    }

    private static final long calcRefLength(CramCompressionRecord record) {
        if (record.readFeatures == null || record.readFeatures.isEmpty())
            return record.readLength;
        long len = record.readLength;
        for (ReadFeature rf : record.readFeatures) {
            switch (rf.getOperator()) {
                case Deletion.operator:
                    len += ((Deletion) rf).getLength();
                    break;
                case Insertion.operator:
                    len -= ((Insertion) rf).getSequence().length;
                    break;
                default:
                    break;
            }
        }

        return len;
    }

    private static final byte[] restoreReadBases(CramCompressionRecord record, byte[] ref,
                                                 SubstitutionMatrix substitutionMatrix) {
        int readLength = record.readLength;
        byte[] bases = new byte[readLength];

        int posInRead = 1;
        int alignmentStart = record.alignmentStart - 1;

        int posInSeq = 0;
        if (record.readFeatures == null || record.readFeatures.isEmpty()) {
            if (ref.length < alignmentStart + bases.length) {
                Arrays.fill(bases, (byte) 'N');
                System.arraycopy(ref, alignmentStart, bases, 0,
                        Math.min(bases.length, ref.length - alignmentStart));
            } else
                System.arraycopy(ref, alignmentStart, bases, 0, bases.length);
            return bases;
        }
        List<ReadFeature> variations = record.readFeatures;
        for (ReadFeature v : variations) {
            for (; posInRead < v.getPosition(); posInRead++)
                bases[posInRead - 1] = ref[alignmentStart + posInSeq++];

            switch (v.getOperator()) {
                case Substitution.operator:
                    Substitution sv = (Substitution) v;
                    byte refBase = Utils.normalizeBase(ref[alignmentStart
                            + posInSeq]);
                    byte base = substitutionMatrix.base(refBase, sv.getCode());
                    sv.setBase(base);
                    sv.setRefernceBase(refBase);
                    bases[posInRead++ - 1] = base;
                    posInSeq++;
                    break;
                case Insertion.operator:
                    Insertion iv = (Insertion) v;
                    for (int i = 0; i < iv.getSequence().length; i++)
                        bases[posInRead++ - 1] = iv.getSequence()[i];
                    break;
                case SoftClip.operator:
                    SoftClip sc = (SoftClip) v;
                    for (int i = 0; i < sc.getSequence().length; i++)
                        bases[posInRead++ - 1] = sc.getSequence()[i];
                    break;
                case Deletion.operator:
                    Deletion dv = (Deletion) v;
                    posInSeq += dv.getLength();
                    break;
                case InsertBase.operator:
                    InsertBase ib = (InsertBase) v;
                    bases[posInRead++ - 1] = ib.getBase();
                    break;
            }
        }
        for (; posInRead <= readLength; posInRead++)
            bases[posInRead - 1] = ref[alignmentStart + posInSeq++];

        // ReadBase overwrites bases:
        for (ReadFeature v : variations) {
            switch (v.getOperator()) {
                case ReadBase.operator:
                    ReadBase rb = (ReadBase) v;
                    bases[v.getPosition() - 1] = rb.getBase();
                    break;
                default:
                    break;
            }
        }

        for (int i = 0; i < bases.length; i++) {
            bases[i] = Utils.normalizeBase(bases[i]);
        }

        return bases;
    }

    /**
     * The method is similar in semantics to
     * {@link htsjdk.samtools.SamPairUtil#computeInsertSize(SAMRecord, SAMRecord)
     * computeInsertSize} but operates on CRAM native records instead of
     * SAMRecord objects.
     *
     * @param firstEnd
     * @param secondEnd
     * @return template length
     */
    private static int computeInsertSize(CramCompressionRecord firstEnd,
                                         CramCompressionRecord secondEnd) {
        if (firstEnd.isSegmentUnmapped() || secondEnd.isSegmentUnmapped()) {
            return 0;
        }
        if (firstEnd.sequenceId != secondEnd.sequenceId) {
            return 0;
        }

        final int firstEnd5PrimePosition = firstEnd.isNegativeStrand() ? firstEnd.getAlignmentEnd() : firstEnd.alignmentStart;
        final int secondEnd5PrimePosition = secondEnd.isNegativeStrand() ? secondEnd.getAlignmentEnd() : secondEnd.alignmentStart;

        final int adjustment = (secondEnd5PrimePosition >= firstEnd5PrimePosition) ? +1 : -1;
        return secondEnd5PrimePosition - firstEnd5PrimePosition + adjustment;
    }
}
