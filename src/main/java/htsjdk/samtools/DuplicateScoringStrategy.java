/*
 * The MIT License
 *
 * Copyright (c) 2014 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package htsjdk.samtools;

import htsjdk.samtools.util.Murmur3;

/**
 * This class helps us compute and compare duplicate scores, which are used for selecting the non-duplicate
 * during duplicate marking (see MarkDuplicates).
 * @author nhomer
 */
public class DuplicateScoringStrategy {

    public enum ScoringStrategy {
        SUM_OF_BASE_QUALITIES,
        TOTAL_MAPPED_REFERENCE_LENGTH,
        RANDOM,
    }

    /** Hash used for the RANDOM scoring strategy. */
    private static final Murmur3 hasher = new Murmur3(1);

    /** An enum to use for storing temporary attributes on SAMRecords. */
    private static enum Attr { DuplicateScore }

    /** Calculates a score for the read which is the sum of scores over Q15. */
    private static int getSumOfBaseQualities(final SAMRecord rec) {
        int score = 0;
        for (final byte b : rec.getBaseQualities()) {
            if (b >= 15) score += b;
        }

        return score;
    }

    /**
     * Returns the duplicate score computed from the given fragment.
     */
    public static short computeDuplicateScore(final SAMRecord record, final ScoringStrategy scoringStrategy) {
        return computeDuplicateScore(record, scoringStrategy, false);
    }

    /**
     * Returns the duplicate score computed from the given fragment.
     * value should be capped by Short.MAX_VALUE/2 since the score from two reads will be
     * added and an overflow will be
     *
     * If true is given to assumeMateCigar, then any score that can use the mate cigar to compute the mate's score will return the score
     * computed on both ends.
     */
    public static short computeDuplicateScore(final SAMRecord record, final ScoringStrategy scoringStrategy, final boolean assumeMateCigar) {
        Short storedScore = (Short) record.getTransientAttribute(Attr.DuplicateScore);

        if (storedScore == null) {
            short score=0;
            switch (scoringStrategy) {
                case SUM_OF_BASE_QUALITIES:
                    // two (very) long reads worth of high-quality bases can go over Short.MAX_VALUE/2
                    // and risk overflow.
                    score += (short) Math.min(getSumOfBaseQualities(record), Short.MAX_VALUE / 2);
                    break;
                case TOTAL_MAPPED_REFERENCE_LENGTH:
                    if (!record.getReadUnmappedFlag()) {
                        // no need to remember the score since this scoring mechanism is symmetric
                        score = (short) Math.min(record.getCigar().getReferenceLength(), Short.MAX_VALUE / 2);
                    }
                    if (assumeMateCigar && record.getReadPairedFlag() && !record.getMateUnmappedFlag()) {
                        score += (short) Math.min(SAMUtils.getMateCigar(record).getReferenceLength(), Short.MAX_VALUE / 2);
                    }
                    break;
                // The RANDOM score gives the same score to both reads so that they get filtered together.
                // it's not critical do use the readName since the scores from both ends get added, but it seem
                // to be clearer this way.
                case RANDOM:
                    // start with a random number between Short.MIN_VALUE/4 and Short.MAX_VALUE/4
                    score += (short) (hasher.hashUnencodedChars(record.getReadName()) & 0b11_1111_1111_1111);
                    // subtract Short.MIN_VALUE/4 from it to end up with a number between
                    // 0 and Short.MAX_VALUE/2. This number can be then discounted in case the read is
                    // not passing filters. We need to stay far from overflow so that when we add the two
                    // scores from the two read mates we do not overflow since that could cause us to chose a
                    // failing read-pair instead of a passing one.
                    score -= Short.MIN_VALUE / 4;
            }

            // make sure that filter-failing records are heavily discounted. (the discount can happen twice, once
            // for each mate, so need to make sure we do not subtract more than Short.MIN_VALUE overall.)
            score += record.getReadFailsVendorQualityCheckFlag() ? (short) (Short.MIN_VALUE / 2) : 0;

            storedScore = score;
            record.setTransientAttribute(Attr.DuplicateScore, storedScore);
        }

        return storedScore;
    }

    /**
     * Compare two records based on their duplicate scores.  If the scores are equal, we break ties based on mapping quality
     * (added to the mate's mapping quality if paired and mapped), then library/read name.
     *
     * If true is given to assumeMateCigar, then any score that can use the mate cigar to to compute the mate's score will return the score
     * computed on both ends.
     *
     * We allow different scoring strategies. We return <0 if rec1 has a better strategy than rec2.
     */
    public static int compare(final SAMRecord rec1, final SAMRecord rec2, final ScoringStrategy scoringStrategy, final boolean assumeMateCigar) {
        int cmp;

        // always prefer paired over non-paired
        if (rec1.getReadPairedFlag() != rec2.getReadPairedFlag()) return rec1.getReadPairedFlag() ? 1 : -1;

        cmp = computeDuplicateScore(rec2, scoringStrategy, assumeMateCigar) - computeDuplicateScore(rec1, scoringStrategy, assumeMateCigar);

        /**
         * Finally, use library ID and read name
         * This is important because we cannot control the order in which reads appear for reads that are comparable up to now (i.e. cmp == 0).  We want to deterministically
         * choose them, and so we need this.
         */
        if (0 == cmp) cmp = SAMUtils.getCanonicalRecordName(rec1).compareTo(SAMUtils.getCanonicalRecordName(rec2));

        return cmp;
    }

    /**
     * Compare two records based on their duplicate scores.  The duplicate scores for each record is assumed to be
     * pre-computed by computeDuplicateScore and stored in the "DS" tag.  If the scores are equal, we break
     * ties based on mapping quality (added to the mate's mapping quality if paired and mapped), then library/read name.
     *
     * We allow different scoring strategies. We return <0 if rec1 has a better strategy than rec2.
     */
    public static int compare(final SAMRecord rec1, final SAMRecord rec2, final ScoringStrategy scoringStrategy) {
        return compare(rec1, rec2, scoringStrategy, false);
    }

}
