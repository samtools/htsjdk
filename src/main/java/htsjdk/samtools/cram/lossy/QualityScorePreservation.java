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
package htsjdk.samtools.cram.lossy;

import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.cram.encoding.readfeatures.BaseQualityScore;
import htsjdk.samtools.cram.encoding.readfeatures.ReadFeature;
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
        final QualityScoreTreatment qualityScoreTreatment;
        switch (param) {
            case 0:
                qualityScoreTreatment = QualityScoreTreatment.drop();
                break;
            case 40:
                qualityScoreTreatment = QualityScoreTreatment.preserve();
                break;

            default:
                qualityScoreTreatment = QualityScoreTreatment.bin(param);
                break;

        }
        return qualityScoreTreatment;
    }

    private static List<PreservationPolicy> parsePolicies(final String spec) {
        final List<PreservationPolicy> policyList = new ArrayList<PreservationPolicy>();
        for (final String string : spec.split("-")) {
            if (string.isEmpty())
                continue;
            final PreservationPolicy policy = parseSinglePolicy(string);
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
        final PreservationPolicy preservationPolicy = new PreservationPolicy();
        final LinkedList<Character> list = new LinkedList<Character>();
        for (final char character : spec.toCharArray())
            list.add(character);

        while (!list.isEmpty()) {
            final char code = list.removeFirst();
            switch (code) {
                case 'R':
                    preservationPolicy.baseCategories.add(BaseCategory.match());
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case 'N':
                    preservationPolicy.baseCategories.add(BaseCategory.mismatch());
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case 'X':
                    final int coverage = readParam(list);
                    preservationPolicy.baseCategories
                            .add(BaseCategory.lowerThanCoverage(coverage));
                    break;
                case 'D':
                    preservationPolicy.baseCategories.add(BaseCategory.flankingDeletion());
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case 'M':
                    int score = readParam(list);
                    preservationPolicy.readCategory = ReadCategory.higher_than_mapping_score(score);
                    break;
                case 'm':
                    score = readParam(list);
                    preservationPolicy.readCategory = ReadCategory.lower_than_mapping_score(score);
                    break;
                case 'U':
                    preservationPolicy.readCategory = ReadCategory.unplaced();
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case 'P':
                    final int mismatches = readParam(list);
                    preservationPolicy.baseCategories.add(BaseCategory.pileup(mismatches));
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case 'I':
                    preservationPolicy.baseCategories.add(BaseCategory.insertion());
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case '_':
                    preservationPolicy.treatment = readTreatment(list);
                    break;
                case '*':
                    preservationPolicy.readCategory = ReadCategory.all();
                    preservationPolicy.treatment = readTreatment(list);
                    break;

                default:
                    throw new RuntimeException("Unknown read or base category: "
                            + code);
            }

            if (preservationPolicy.treatment == null)
                preservationPolicy.treatment = QualityScoreTreatment.preserve();
        }

        return preservationPolicy;
    }

    private static void applyBinning(final byte[] scores) {
        for (int i = 0; i < scores.length; i++)
            scores[i] = Binning.ILLUMINA_BINNING_MATRIX[scores[i]];
    }

    private static byte applyTreatment(final byte score, final QualityScoreTreatment qualityScoreTreatment) {
        switch (qualityScoreTreatment.type) {
            case BIN:
                return Binning.ILLUMINA_BINNING_MATRIX[score];
            case DROP:
                return -1;
            case PRESERVE:
                return score;

        }
        throw new RuntimeException("Unknown quality score treatment type: "
                + qualityScoreTreatment.type.name());
    }

    public void addQualityScores(final SAMRecord samRecord, final CramCompressionRecord cramRecord,
                                 final ReferenceTracks referenceTracks) {
        if (samRecord.getBaseQualities() == SAMRecord.NULL_QUALS) {
            cramRecord.qualityScores = SAMRecord.NULL_QUALS;
            cramRecord.setForcePreserveQualityScores(false);
            return;
        }

        final byte[] scores = new byte[samRecord.getReadLength()];
        Arrays.fill(scores, (byte) -1);
        for (final PreservationPolicy preservationPolicy : policyList)
            addQS(samRecord, cramRecord, scores, referenceTracks, preservationPolicy);

        if (!cramRecord.isForcePreserveQualityScores()) {
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > -1) {
                    if (cramRecord.readFeatures == null)
                        cramRecord.readFeatures = new LinkedList<ReadFeature>();
                    cramRecord.readFeatures.add(new BaseQualityScore(i + 1, scores[i]));
                }
            }
            if (cramRecord.readFeatures != null)
                Collections.sort(cramRecord.readFeatures, readFeaturePositionComparator);
        }
        cramRecord.qualityScores = scores;
    }

    private static final Comparator<ReadFeature> readFeaturePositionComparator = new Comparator<ReadFeature>() {

        @Override
        public int compare(final ReadFeature o1, final ReadFeature o2) {
            return o1.getPosition() - o2.getPosition();
        }
    };

    public boolean areReferenceTracksRequired() {
        if (policyList == null || policyList.isEmpty()) return false;
        for (final PreservationPolicy preservationPolicy : policyList) {
            if (preservationPolicy.baseCategories == null || preservationPolicy.baseCategories.isEmpty())
                continue;
            for (final BaseCategory c : preservationPolicy.baseCategories) {
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

    private static void addQS(final SAMRecord samRecord, final CramCompressionRecord cramRecord,
                              final byte[] scores, final ReferenceTracks referenceTracks, final PreservationPolicy preservationPolicy) {
        final int alSpan = samRecord.getAlignmentEnd() - samRecord.getAlignmentStart();
        final byte[] qualityScores = samRecord.getBaseQualities();

        // check if read is falling into the read category:
        if (preservationPolicy.readCategory != null) {
            @SuppressWarnings("UnusedAssignment") boolean properRead = false;
            switch (preservationPolicy.readCategory.type) {
                case ALL:
                    properRead = true;
                    break;
                case UNPLACED:
                    properRead = samRecord.getReadUnmappedFlag();
                    break;
                case LOWER_MAPPING_SCORE:
                    properRead = samRecord.getMappingQuality() < preservationPolicy.readCategory.param;
                    break;
                case HIGHER_MAPPING_SCORE:
                    properRead = samRecord.getMappingQuality() > preservationPolicy.readCategory.param;
                    break;

                default:
                    throw new RuntimeException("Unknown read category: "
                            + preservationPolicy.readCategory.type.name());
            }

            if (!properRead) // nothing to do here:
                return;
        }

        // apply treatment if there is no per-base policy:
        if (preservationPolicy.baseCategories == null || preservationPolicy.baseCategories.isEmpty()) {
            switch (preservationPolicy.treatment.type) {
                case BIN:
                    if (cramRecord.qualityScores == null)
                        cramRecord.qualityScores = samRecord.getBaseQualities();
                    System.arraycopy(samRecord.getBaseQualities(), 0, scores, 0,
                            scores.length);
                    applyBinning(scores);
                    cramRecord.setForcePreserveQualityScores(true);
                    break;
                case PRESERVE:
                    System.arraycopy(samRecord.getBaseQualities(), 0, scores, 0,
                            scores.length);
                    cramRecord.setForcePreserveQualityScores(true);
                    break;
                case DROP:
                    cramRecord.qualityScores = null;
                    cramRecord.setForcePreserveQualityScores(false);
                    break;

                default:
                    throw new RuntimeException(
                            "Unknown quality score treatment type: "
                                    + preservationPolicy.treatment.type.name());
            }

            // nothing else to do here:
            return;
        }

        // here we go, scan all bases to check if the policy applies:
        final boolean[] mask = new boolean[qualityScores.length];

        final int alStart = samRecord.getAlignmentStart();
        // must be a mapped read at this point:
        if (alStart == SAMRecord.NO_ALIGNMENT_START)
            return;
        referenceTracks.ensureRange(alStart, alSpan);

        for (final BaseCategory baseCategory : preservationPolicy.baseCategories) {
            int pos;
            int refPos;
            switch (baseCategory.type) {
                case FLANKING_DELETION:
                    pos = 0;
                    for (final CigarElement cigarElement : samRecord.getCigar().getCigarElements()) {
                        if (cigarElement.getOperator() == CigarOperator.D) {
                            // if (pos > 0)
                            mask[pos] = true;
                            if (pos + 1 < mask.length)
                                mask[pos + 1] = true;
                        }

                        pos += cigarElement.getOperator().consumesReadBases() ? cigarElement
                                .getLength() : 0;
                    }
                    break;
                case MATCH:
                case MISMATCH:
                    pos = 0;
                    refPos = samRecord.getAlignmentStart();
                    for (final CigarElement cigarElement : samRecord.getCigar().getCigarElements()) {
                        if (cigarElement.getOperator().consumesReadBases()) {
                            for (int i = 0; i < cigarElement.getLength(); i++) {
                                final boolean match = samRecord.getReadBases()[pos + i] == referenceTracks
                                        .baseAt(refPos + i);
                                mask[pos + i] = (baseCategory.type == BaseCategoryType.MATCH && match)
                                        || (baseCategory.type == BaseCategoryType.MISMATCH && !match);
                            }
                            pos += cigarElement.getLength();
                        }
                        refPos += cigarElement.getOperator().consumesReferenceBases() ? cigarElement
                                .getLength() : 0;
                    }
                    break;
                case INSERTION:
                    pos = 0;
                    for (final CigarElement cigarElement : samRecord.getCigar().getCigarElements()) {
                        switch (cigarElement.getOperator()) {
                            case I:
                                for (int i = 0; i < cigarElement.getLength(); i++)
                                    mask[pos + i] = true;
                                break;
                            default:
                                break;
                        }

                        pos += cigarElement.getOperator().consumesReadBases() ? cigarElement
                                .getLength() : 0;
                    }
                    break;
                case LOWER_COVERAGE:
                    pos = 1;
                    refPos = samRecord.getAlignmentStart();
                    for (final CigarElement cigarElement : samRecord.getCigar().getCigarElements()) {
                        switch (cigarElement.getOperator()) {
                            case M:
                            case EQ:
                            case X:
                                for (int i = 0; i < cigarElement.getLength(); i++)
                                    mask[pos + i - 1] = referenceTracks.coverageAt(refPos + i) < baseCategory.param;
                                break;
                            default:
                                break;
                        }

                        pos += cigarElement.getOperator().consumesReadBases() ? cigarElement
                                .getLength() : 0;
                        refPos += cigarElement.getOperator().consumesReferenceBases() ? cigarElement
                                .getLength() : 0;
                    }
                    break;
                case PILEUP:
                    for (int i = 0; i < qualityScores.length; i++)
                        if (referenceTracks.mismatchesAt(alStart + i) > baseCategory.param)
                            mask[i] = true;
                    break;

                default:
                    break;
            }

        }

        int maskedCount = 0;
        for (int i = 0; i < mask.length; i++)
            if (mask[i]) {
                scores[i] = applyTreatment(qualityScores[i], preservationPolicy.treatment);
                maskedCount++;
            }

        // safety latch, store all qs if there are too many individual score
        // to store:
        if (maskedCount > samRecord.getReadLength() / 2)
            cramRecord.setForcePreserveQualityScores(true);
    }
}
