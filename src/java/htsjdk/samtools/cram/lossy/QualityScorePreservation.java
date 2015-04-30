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
package htsjdk.samtools.cram.lossy;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.read_features.BaseQualityScore;
import htsjdk.samtools.cram.encoding.read_features.ReadFeature;
import htsjdk.samtools.cram.ref.ReferenceTracks;
import htsjdk.samtools.cram.structure.CramCompressionRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class QualityScorePreservation {
    private final List<PreservationPolicy> policyList;

    public QualityScorePreservation(final String specification) {
        policyList = parsePolicies(specification);
    }

    public List<PreservationPolicy> getPreservationPolicies() {
        return policyList;
    }

    private static int readParam(final LinkedList<Character> list) {
        int value = 0;

        while (!list.isEmpty() && Character.isDigit(list.getFirst()))
            value = value * 10 + (list.removeFirst() - 48);

        return value;
    }

    private static QualityScoreTreatment readTreatment(
            final LinkedList<Character> list) {
        final int param = readParam(list);
        final QualityScoreTreatment t;
        switch (param) {
            case 0:
                t = QualityScoreTreatment.drop();
                break;
            case 40:
                t = QualityScoreTreatment.preserve();
                break;

            default:
                t = QualityScoreTreatment.bin(param);
                break;

        }
        return t;
    }

    private static List<PreservationPolicy> parsePolicies(final String spec) {
        final List<PreservationPolicy> policyList = new ArrayList<PreservationPolicy>();
        for (final String s : spec.split("-")) {
            if (s.length() == 0)
                continue;
            final PreservationPolicy policy = parseSinglePolicy(s);
            policyList.add(policy);
        }

        Collections.sort(policyList, new Comparator<PreservationPolicy>() {

            @Override
            public int compare(final PreservationPolicy o1, final PreservationPolicy o2) {
                final QualityScoreTreatment t1 = o1.treatment;
                final QualityScoreTreatment t2 = o2.treatment;
                final int result = t2.type.ordinal() - t1.type.ordinal();
                if (result != 0)
                    return result;

                return 0;
            }
        });

        return policyList;
    }

    private static PreservationPolicy parseSinglePolicy(final String spec) {
        final PreservationPolicy p = new PreservationPolicy();
        final LinkedList<Character> list = new LinkedList<Character>();
        for (final char b : spec.toCharArray())
            list.add(b);

        while (!list.isEmpty()) {
            final char code = list.removeFirst();
            switch (code) {
                case 'R':
                    p.baseCategories.add(BaseCategory.match());
                    p.treatment = readTreatment(list);
                    break;
                case 'N':
                    p.baseCategories.add(BaseCategory.mismatch());
                    p.treatment = readTreatment(list);
                    break;
                case 'X':
                    final int coverage = readParam(list);
                    p.baseCategories
                            .add(BaseCategory.lower_than_coverage(coverage));
                    break;
                case 'D':
                    p.baseCategories.add(BaseCategory.flanking_deletion());
                    p.treatment = readTreatment(list);
                    break;
                case 'M':
                    int score = readParam(list);
                    p.readCategory = ReadCategory.higher_than_mapping_score(score);
                    break;
                case 'm':
                    score = readParam(list);
                    p.readCategory = ReadCategory.lower_than_mapping_score(score);
                    break;
                case 'U':
                    p.readCategory = ReadCategory.unplaced();
                    p.treatment = readTreatment(list);
                    break;
                case 'P':
                    final int mismatches = readParam(list);
                    p.baseCategories.add(BaseCategory.pileup(mismatches));
                    p.treatment = readTreatment(list);
                    break;
                case 'I':
                    p.baseCategories.add(BaseCategory.insertion());
                    p.treatment = readTreatment(list);
                    break;
                case '_':
                    p.treatment = readTreatment(list);
                    break;
                case '*':
                    p.readCategory = ReadCategory.all();
                    p.treatment = readTreatment(list);
                    break;

                default:
                    throw new RuntimeException("Unknown read or base category: "
                            + code);
            }

            if (p.treatment == null)
                p.treatment = QualityScoreTreatment.preserve();
        }

        return p;
    }

    private static void applyBinning(final byte[] scores) {
        for (int i = 0; i < scores.length; i++)
            scores[i] = Binning.Illumina_binning_matrix[scores[i]];
    }

    private static byte applyTreatment(final byte score, final QualityScoreTreatment t) {
        switch (t.type) {
            case BIN:
                return Binning.Illumina_binning_matrix[score];
            case DROP:
                return -1;
            case PRESERVE:
                return score;

        }
        throw new RuntimeException("Unknown quality score treatment type: "
                + t.type.name());
    }

    public void addQualityScores(final SAMRecord s, final CramCompressionRecord r,
                                 final ReferenceTracks t) {
        if (s.getBaseQualities() == SAMRecord.NULL_QUALS) {
            r.qualityScores = SAMRecord.NULL_QUALS;
            r.setForcePreserveQualityScores(false);
            return;
        }

        final byte[] scores = new byte[s.getReadLength()];
        Arrays.fill(scores, (byte) -1);
        for (final PreservationPolicy p : policyList)
            addQS(s, r, scores, t, p);

        if (!r.isForcePreserveQualityScores()) {
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > -1) {
                    if (r.readFeatures == null)
                        r.readFeatures = new LinkedList<ReadFeature>();
                    r.readFeatures.add(new BaseQualityScore(i + 1, scores[i]));
                }
            }
            if (r.readFeatures != null)
                Collections.sort(r.readFeatures, readFeaturePositionComparator);
        }
        r.qualityScores = scores;
    }

    private static final Comparator<ReadFeature> readFeaturePositionComparator = new Comparator<ReadFeature>() {

        @Override
        public int compare(final ReadFeature o1, final ReadFeature o2) {
            return o1.getPosition() - o2.getPosition();
        }
    };

    public boolean areReferenceTracksRequired() {
        if (policyList == null || policyList.isEmpty()) return false;
        for (final PreservationPolicy p : policyList) {
            if (p.baseCategories == null || p.baseCategories.isEmpty())
                continue;
            for (final BaseCategory c : p.baseCategories) {
                switch (c.type) {
                    case LOWER_COVERAGE:
                    case PILEUP:
                        return true;

                    default:
                        break;
                }
            }
        }
        return false;

    }

    private static void addQS(final SAMRecord s, final CramCompressionRecord r,
                                    final byte[] scores, final ReferenceTracks t, final PreservationPolicy p) {
        final int alSpan = s.getAlignmentEnd() - s.getAlignmentStart();
        final byte[] qs = s.getBaseQualities();

        // check if read is falling into the read category:
        if (p.readCategory != null) {
            @SuppressWarnings("UnusedAssignment") boolean properRead = false;
            switch (p.readCategory.type) {
                case ALL:
                    properRead = true;
                    break;
                case UNPLACED:
                    properRead = s.getReadUnmappedFlag();
                    break;
                case LOWER_MAPPING_SCORE:
                    properRead = s.getMappingQuality() < p.readCategory.param;
                    break;
                case HIGHER_MAPPING_SCORE:
                    properRead = s.getMappingQuality() > p.readCategory.param;
                    break;

                default:
                    throw new RuntimeException("Unknown read category: "
                            + p.readCategory.type.name());
            }

            if (!properRead) // nothing to do here:
                return;
        }

        // apply treatment if there is no per-base policy:
        if (p.baseCategories == null || p.baseCategories.isEmpty()) {
            switch (p.treatment.type) {
                case BIN:
                    if (r.qualityScores == null)
                        r.qualityScores = s.getBaseQualities();
                    System.arraycopy(s.getBaseQualities(), 0, scores, 0,
                            scores.length);
                    applyBinning(scores);
                    r.setForcePreserveQualityScores(true);
                    break;
                case PRESERVE:
                    System.arraycopy(s.getBaseQualities(), 0, scores, 0,
                            scores.length);
                    r.setForcePreserveQualityScores(true);
                    break;
                case DROP:
                    r.qualityScores = null;
                    r.setForcePreserveQualityScores(false);
                    break;

                default:
                    throw new RuntimeException(
                            "Unknown quality score treatment type: "
                                    + p.treatment.type.name());
            }

            // nothing else to do here:
            return;
        }

        // here we go, scan all bases to check if the policy applies:
        final boolean[] mask = new boolean[qs.length];

        final int alStart = s.getAlignmentStart();
        // must be a mapped read at this point:
        if (alStart == SAMRecord.NO_ALIGNMENT_START)
            return;
        t.ensureRange(alStart, alSpan);

        for (final BaseCategory c : p.baseCategories) {
            int pos;
            int refPos;
            switch (c.type) {
                case FLANKING_DELETION:
                    pos = 0;
                    for (final CigarElement ce : s.getCigar().getCigarElements()) {
                        if (ce.getOperator() == CigarOperator.D) {
                            // if (pos > 0)
                            mask[pos] = true;
                            if (pos + 1 < mask.length)
                                mask[pos + 1] = true;
                        }

                        pos += ce.getOperator().consumesReadBases() ? ce
                                .getLength() : 0;
                    }
                    break;
                case MATCH:
                case MISMATCH:
                    pos = 0;
                    refPos = s.getAlignmentStart();
                    for (final CigarElement ce : s.getCigar().getCigarElements()) {
                        if (ce.getOperator().consumesReadBases()) {
                            for (int i = 0; i < ce.getLength(); i++) {
                                final boolean match = s.getReadBases()[pos + i] == t
                                        .baseAt(refPos + i);
                                mask[pos + i] = (c.type == BaseCategoryType.MATCH && match)
                                        || (c.type == BaseCategoryType.MISMATCH && !match);
                            }
                            pos += ce.getLength();
                        }
                        refPos += ce.getOperator().consumesReferenceBases() ? ce
                                .getLength() : 0;
                    }
                    break;
                case INSERTION:
                    pos = 0;
                    for (final CigarElement ce : s.getCigar().getCigarElements()) {
                        switch (ce.getOperator()) {
                            case I:
                                for (int i = 0; i < ce.getLength(); i++)
                                    mask[pos + i] = true;
                                break;
                            default:
                                break;
                        }

                        pos += ce.getOperator().consumesReadBases() ? ce
                                .getLength() : 0;
                    }
                    break;
                case LOWER_COVERAGE:
                    pos = 1;
                    refPos = s.getAlignmentStart();
                    for (final CigarElement ce : s.getCigar().getCigarElements()) {
                        switch (ce.getOperator()) {
                            case M:
                            case EQ:
                            case X:
                                for (int i = 0; i < ce.getLength(); i++)
                                    mask[pos + i - 1] = t.coverageAt(refPos + i) < c.param;
                                break;
                            default:
                                break;
                        }

                        pos += ce.getOperator().consumesReadBases() ? ce
                                .getLength() : 0;
                        refPos += ce.getOperator().consumesReferenceBases() ? ce
                                .getLength() : 0;
                    }
                    break;
                case PILEUP:
                    for (int i = 0; i < qs.length; i++)
                        if (t.mismatchesAt(alStart + i) > c.param)
                            mask[i] = true;
                    break;

                default:
                    break;
            }

        }

        int maskedCount = 0;
        for (int i = 0; i < mask.length; i++)
            if (mask[i]) {
                scores[i] = applyTreatment(qs[i], p.treatment);
                maskedCount++;
            }

        // safety latch, store all qs if there are too many individual score
        // to store:
        if (maskedCount > s.getReadLength() / 2)
            r.setForcePreserveQualityScores(true);
    }
}
