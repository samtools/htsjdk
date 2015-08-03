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
package htsjdk.samtools.cram.build;

import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.BaseQualityScore;
import htsjdk.samtools.cram.encoding.readfeatures.Deletion;
import htsjdk.samtools.cram.encoding.readfeatures.InsertBase;
import htsjdk.samtools.cram.encoding.readfeatures.Insertion;
import htsjdk.samtools.cram.encoding.readfeatures.ReadBase;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
import htsjdk.samtools.cram.encoding.readfeatures.RefSkip;
import htsjdk.samtools.cram.encoding.readfeatures.SoftClip;
import htsjdk.samtools.cram.encoding.readfeatures.Substitution;
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

        for (final CramCompressionRecord record : records) {
            record.index = ++readCounter;

            if (record.sequenceId == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX) {
                record.sequenceName = SAMRecord.NO_ALIGNMENT_REFERENCE_NAME;
                record.alignmentStart = SAMRecord.NO_ALIGNMENT_START;
            } else {
                record.sequenceName = header.getSequence(record.sequenceId)
                        .getSequenceName();
            }
        }

        {// restore pairing first:
            for (final CramCompressionRecord record : records) {
                if (!record.isMultiFragment() || record.isDetached()) {
                    record.recordsToNextFragment = -1;

                    record.next = null;
                    record.previous = null;
                    continue;
                }
                if (record.isHasMateDownStream()) {
                    final CramCompressionRecord downMate = records.get(record.index
                            + record.recordsToNextFragment - startCounter);
                    record.next = downMate;
                    downMate.previous = record;
                }
            }
            for (final CramCompressionRecord record : records) {
                if (record.previous != null) continue;
                if (record.next == null) continue;
                restoreMateInfo(record);
            }
        }

        // assign some read names if needed:
        for (final CramCompressionRecord record : records) {
            if (record.readName == null) {
                final String readNamePrefix = "";
                final String name = readNamePrefix + record.index;
                record.readName = name;
                if (record.next != null)
                    record.next.readName = name;
                if (record.previous != null)
                    record.previous.readName = name;
            }
        }

        // resolve bases:
        for (final CramCompressionRecord record : records) {
            if (record.isSegmentUnmapped())
                continue;

            byte[] refBases = ref;
            {
                // ref could be supplied (aka forced) already or needs looking up:
                // ref.length=0 is a special case of seqId=-2 (multiref)
                if ((ref == null || ref.length == 0) && referenceSource != null)
                    refBases = referenceSource.getReferenceBases(
                            header.getSequence(record.sequenceId), true);
            }

            if (record.isUnknownBases()) {
                record.readBases = SAMRecord.NULL_SEQUENCE;
            } else
                record.readBases = restoreReadBases(record, refBases, refOffset_zeroBased,
                        substitutionMatrix);
        }

        // restore quality scores:
        final byte defaultQualityScore = '?' - '!';
        restoreQualityScores(defaultQualityScore, records);
    }

    private static void restoreMateInfo(final CramCompressionRecord record) {
        if (record.next == null) {

            return;
        }
        CramCompressionRecord cur;
        cur = record;
        while (cur.next != null) {
            setNextMate(cur, cur.next);
            cur = cur.next;
        }

        // cur points to the last segment now:
        final CramCompressionRecord last = cur;
        setNextMate(last, record);
//        record.setFirstSegment(true);
//        last.setLastSegment(true);

        final int templateLength = computeInsertSize(record, last);
        record.templateSize = templateLength;
        last.templateSize = -templateLength;
    }

    private static void setNextMate(final CramCompressionRecord record, final CramCompressionRecord next) {
        record.mateAlignmentStart = next.alignmentStart;
        record.setMateUnmapped(next.isSegmentUnmapped());
        record.setMateNegativeStrand(next.isNegativeStrand());
        record.mateSequenceID = next.sequenceId;
        if (record.mateSequenceID == SAMRecord.NO_ALIGNMENT_REFERENCE_INDEX)
            record.mateAlignmentStart = SAMRecord.NO_ALIGNMENT_START;
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
                for (final ReadFeature feature : record.readFeatures) {
                    switch (feature.getOperator()) {
                        case BaseQualityScore.operator:
                            int pos = feature.getPosition();
                            scores[pos - 1] = ((BaseQualityScore) feature).getQualityScore();
                            star = false;
                            break;
                        case ReadBase.operator:
                            pos = feature.getPosition();
                            scores[pos - 1] = ((ReadBase) feature).getQualityScore();
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
                                           final int refOffsetZeroBased, final SubstitutionMatrix substitutionMatrix) {
        if (record.isUnknownBases() || record.readLength == 0) return SAMRecord.NULL_SEQUENCE;
        final int readLength = record.readLength;
        final byte[] bases = new byte[readLength];

        int posInRead = 1;
        final int alignmentStart = record.alignmentStart - 1;

        int posInSeq = 0;
        if (record.readFeatures == null || record.readFeatures.isEmpty()) {
            if (ref.length + refOffsetZeroBased < alignmentStart
                    + bases.length) {
                Arrays.fill(bases, (byte) 'N');
                System.arraycopy(
                        ref,
                        alignmentStart - refOffsetZeroBased,
                        bases,
                        0,
                        Math.min(bases.length, ref.length + refOffsetZeroBased
                                - alignmentStart));
            } else
                System.arraycopy(ref, alignmentStart - refOffsetZeroBased,
                        bases, 0, bases.length);
            return bases;
        }
        final List<ReadFeature> variations = record.readFeatures;
        for (final ReadFeature variation : variations) {
            for (; posInRead < variation.getPosition(); posInRead++) {
                final int rp = alignmentStart + posInSeq++ - refOffsetZeroBased;
                bases[posInRead - 1] = getByteOrDefault(ref, rp, (byte) 'N');
            }

            switch (variation.getOperator()) {
                case Substitution.operator:
                    final Substitution substitution = (Substitution) variation;
                    byte refBase = getByteOrDefault(ref, alignmentStart + posInSeq
                            - refOffsetZeroBased, (byte) 'N');
                    refBase = Utils.normalizeBase(refBase);
                    final byte base = substitutionMatrix.base(refBase, substitution.getCode());
                    substitution.setBase(base);
                    substitution.setReferenceBase(refBase);
                    bases[posInRead++ - 1] = base;
                    posInSeq++;
                    break;
                case Insertion.operator:
                    final Insertion insertion = (Insertion) variation;
                    for (int i = 0; i < insertion.getSequence().length; i++)
                        bases[posInRead++ - 1] = insertion.getSequence()[i];
                    break;
                case SoftClip.operator:
                    final SoftClip softClip = (SoftClip) variation;
                    for (int i = 0; i < softClip.getSequence().length; i++)
                        bases[posInRead++ - 1] = softClip.getSequence()[i];
                    break;
                case Deletion.operator:
                    final Deletion deletion = (Deletion) variation;
                    posInSeq += deletion.getLength();
                    break;
                case InsertBase.operator:
                    final InsertBase insert = (InsertBase) variation;
                    bases[posInRead++ - 1] = insert.getBase();
                    break;
                case RefSkip.operator:
                    posInSeq += ((RefSkip) variation).getLength();
                    break;
            }
        }
        for (; posInRead <= readLength
                && alignmentStart + posInSeq - refOffsetZeroBased < ref.length; posInRead++, posInSeq++) {
            bases[posInRead - 1] = ref[alignmentStart + posInSeq
                    - refOffsetZeroBased];
        }

        // ReadBase overwrites bases:
        for (final ReadFeature variation : variations) {
            switch (variation.getOperator()) {
                case ReadBase.operator:
                    final ReadBase readBase = (ReadBase) variation;
                    bases[variation.getPosition() - 1] = readBase.getBase();
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
