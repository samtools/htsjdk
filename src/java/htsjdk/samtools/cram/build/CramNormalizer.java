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
import htsjdk.samtools.cram.encoding.read_features.RefSkip;
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
    private final SAMFileHeader header;
    private int readCounter = 0;

    private static Log log = Log.getInstance(CramNormalizer.class);
    private ReferenceSource referenceSource;

    private CramNormalizer(final SAMFileHeader header) {
        this.header = header;
    }

    public CramNormalizer(final SAMFileHeader header, final ReferenceSource referenceSource) {
        this.header = header;
        this.referenceSource = referenceSource;
    }

    public void normalize(final ArrayList<CramCompressionRecord> records,
                          final byte[] ref, final int refOffset_zeroBased,
                          final SubstitutionMatrix substitutionMatrix) {

        final int startCounter = readCounter;

        for (final CramCompressionRecord r : records) {
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
            for (final CramCompressionRecord r : records) {
                if (!r.isMultiFragment() || r.isDetached()) {
                    r.recordsToNextFragment = -1;

                    r.next = null;
                    r.previous = null;
                    continue;
                }
                if (r.isHasMateDownStream()) {
                    final CramCompressionRecord downMate = records.get(r.index
                            + r.recordsToNextFragment - startCounter);
                    r.next = downMate;
                    downMate.previous = r;
                }
            }
            for (final CramCompressionRecord r : records) {
                if (r.previous != null) continue;
                if (r.next == null) continue;
                restoreMateInfo(r);
            }
        }

        // assign some read names if needed:
        for (final CramCompressionRecord r : records) {
            if (r.readName == null) {
                final String readNamePrefix = "";
                final String name = readNamePrefix + r.index;
                r.readName = name;
                if (r.next != null)
                    r.next.readName = name;
                if (r.previous != null)
                    r.previous.readName = name;
            }
        }

        // resolve bases:
        for (final CramCompressionRecord r : records) {
            if (r.isSegmentUnmapped())
                continue;

            byte[] refBases = ref;
            {
                // ref could be supplied (aka forced) already or needs looking up:
                // ref.length=0 is a special case of seqId=-2 (multiref)
                if ((ref == null || ref.length == 0) && referenceSource != null)
                    refBases = referenceSource.getReferenceBases(
                            header.getSequence(r.sequenceId), true);
            }

            if (r.isUnknownBases()) {
                r.readBases = SAMRecord.NULL_SEQUENCE;
            } else
                r.readBases = restoreReadBases(r, refBases, refOffset_zeroBased,
                        substitutionMatrix);
        }

        // restore quality scores:
        final byte defaultQualityScore = '?' - '!';
        restoreQualityScores(defaultQualityScore, records);
    }

    private static void restoreMateInfo (final CramCompressionRecord r) {
        if (r.next == null) {

            return;
        }
        CramCompressionRecord cur ;
        cur = r ;
        while (cur.next != null){
            setNextMate(cur, cur.next);
            cur = cur.next ;
        }

        // cur points to the last segment now:
        final CramCompressionRecord last = cur;
        setNextMate(last, r);
//        r.setFirstSegment(true);
//        last.setLastSegment(true);

        final int templateLength = computeInsertSize(r, last);
        r.templateSize = templateLength;
        last.templateSize = -templateLength;
    }

    private static void setNextMate (final CramCompressionRecord r, final CramCompressionRecord next) {
        r.mateAlignmentStart = next.alignmentStart;
        r.setMateUnmapped(next.isSegmentUnmapped());
        r.setMateNegativeStrand(next.isNegativeStrand());
        r.mateSequenceID = next.sequenceId;
        if (r.mateSequenceID == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
            r.mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;
    }

    public static void restoreQualityScores(final byte defaultQualityScore,
                                            final List<CramCompressionRecord> records) {
        for (final CramCompressionRecord record : records)
            restoreQualityScores(defaultQualityScore, record);
    }

    private static byte[] restoreQualityScores(final byte defaultQualityScore,
                                               final CramCompressionRecord record) {
        if (!record.isForcePreserveQualityScores()) {
            boolean star = true;
            final byte[] scores = new byte[record.readLength];
            Arrays.fill(scores, defaultQualityScore);
            if (record.readFeatures != null)
                for (final ReadFeature f : record.readFeatures) {
                    switch (f.getOperator()) {
                        case BaseQualityScore.operator:
                            int pos = f.getPosition();
                            scores[pos - 1] = ((BaseQualityScore) f).getQualityScore();
                            star = false;
                            break;
                        case ReadBase.operator:
                            pos = f.getPosition();
                            scores[pos - 1] = ((ReadBase) f).getQualityScore();
                            star = false;
                            break;

                        default:
                            break;
                    }
                }

            if (star)
                record.qualityScores = SAMRecord.NULL_QUALS;
            else
                record.qualityScores = scores;
        } else {
            final byte[] scores = record.qualityScores;
            int missingScores = 0;
            for (int i = 0; i < scores.length; i++)
                if (scores[i] == -1) {
                    scores[i] = defaultQualityScore;
                    missingScores++;
                }
            if (missingScores == scores.length)
                record.qualityScores = SAMRecord.NULL_QUALS;
        }

        return record.qualityScores;
    }

    private static byte[] restoreReadBases(final CramCompressionRecord record, final byte[] ref,
                                           final int refOffset_zeroBased, final SubstitutionMatrix substitutionMatrix) {
        if (record.isUnknownBases() || record.readLength == 0) return SAMRecord.NULL_SEQUENCE;
        final int readLength = record.readLength;
        final byte[] bases = new byte[readLength];

        int posInRead = 1;
        final int alignmentStart = record.alignmentStart - 1;

        int posInSeq = 0;
        if (record.readFeatures == null || record.readFeatures.isEmpty()) {
            if (ref.length + refOffset_zeroBased < alignmentStart
                    + bases.length) {
                Arrays.fill(bases, (byte) 'N');
                System.arraycopy(
                        ref,
                        alignmentStart - refOffset_zeroBased,
                        bases,
                        0,
                        Math.min(bases.length, ref.length + refOffset_zeroBased
                                - alignmentStart));
            } else
                System.arraycopy(ref, alignmentStart - refOffset_zeroBased,
                        bases, 0, bases.length);
            return bases;
        }
        final List<ReadFeature> variations = record.readFeatures;
        for (final ReadFeature v : variations) {
            for (; posInRead < v.getPosition(); posInRead++) {
                final int rp = alignmentStart + posInSeq++ - refOffset_zeroBased;
                bases[posInRead - 1] = getByteOrDefault(ref, rp, (byte) 'N');
            }

            switch (v.getOperator()) {
                case Substitution.operator:
                    final Substitution sv = (Substitution) v;
                    byte refBase = getByteOrDefault(ref, alignmentStart + posInSeq
                            - refOffset_zeroBased, (byte) 'N');
                    refBase = Utils.normalizeBase(refBase);
                    final byte base = substitutionMatrix.base(refBase, sv.getCode());
                    sv.setBase(base);
                    sv.setReferenceBase(refBase);
                    bases[posInRead++ - 1] = base;
                    posInSeq++;
                    break;
                case Insertion.operator:
                    final Insertion iv = (Insertion) v;
                    for (int i = 0; i < iv.getSequence().length; i++)
                        bases[posInRead++ - 1] = iv.getSequence()[i];
                    break;
                case SoftClip.operator:
                    final SoftClip sc = (SoftClip) v;
                    for (int i = 0; i < sc.getSequence().length; i++)
                        bases[posInRead++ - 1] = sc.getSequence()[i];
                    break;
                case Deletion.operator:
                    final Deletion dv = (Deletion) v;
                    posInSeq += dv.getLength();
                    break;
                case InsertBase.operator:
                    final InsertBase ib = (InsertBase) v;
                    bases[posInRead++ - 1] = ib.getBase();
                    break;
                case RefSkip.operator:
                    posInSeq += ((RefSkip) v).getLength();
                    break;
            }
        }
        for (; posInRead <= readLength
                && alignmentStart + posInSeq - refOffset_zeroBased < ref.length; posInRead++, posInSeq++) {
            bases[posInRead - 1] = ref[alignmentStart + posInSeq
                    - refOffset_zeroBased];
        }

        // ReadBase overwrites bases:
        for (final ReadFeature v : variations) {
            switch (v.getOperator()) {
                case ReadBase.operator:
                    final ReadBase rb = (ReadBase) v;
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

    private static byte getByteOrDefault(final byte[] array, final int pos,
                                               final byte outOfBoundsValue) {
        if (pos >= array.length)
            return outOfBoundsValue;
        else
            return array[pos];
    }

    /**
     * The method is similar in semantics to
     * {@link htsjdk.samtools.SamPairUtil#computeInsertSize(SAMRecord, SAMRecord)
     * computeInsertSize} but operates on CRAM native records instead of
     * SAMRecord objects.
     *
     * @param firstEnd  first mate of the pair
     * @param secondEnd second mate of the pair
     * @return template length
     */
    public static int computeInsertSize(final CramCompressionRecord firstEnd,
                                        final CramCompressionRecord secondEnd) {
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
