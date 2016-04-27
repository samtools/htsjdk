/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
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
package htsjdk.samtools.util;

import htsjdk.samtools.AlignmentBlock;
import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMException;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.SAMTag;

import java.io.File;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SequenceUtil {
    /** Byte typed variables for all normal bases. */
    public static final byte a = 'a', c = 'c', g = 'g', t = 't', n = 'n', A = 'A', C = 'C', G = 'G', T = 'T', N = 'N';

    public static final byte[] VALID_BASES_UPPER = new byte[]{A, C, G, T};
    public static final byte[] VALID_BASES_LOWER = new byte[]{a, c, g, t};

    private static final byte A_MASK = 1;
    private static final byte C_MASK = 2;
    private static final byte G_MASK = 4;
    private static final byte T_MASK = 8;

    private static final byte[] bases = new byte[127];

    /*
     * Definition of IUPAC codes:
     * http://www.bioinformatics.org/sms2/iupac.html
     */
    static {
        Arrays.fill(bases, (byte) 0);
        bases[A] = A_MASK;
        bases[C] = C_MASK;
        bases[G] = G_MASK;
        bases[T] = T_MASK;
        bases['M'] = A_MASK | C_MASK;
        bases['R'] = A_MASK | G_MASK;
        bases['W'] = A_MASK | T_MASK;
        bases['S'] = C_MASK | G_MASK;
        bases['Y'] = C_MASK | T_MASK;
        bases['K'] = G_MASK | T_MASK;
        bases['V'] = A_MASK | C_MASK | G_MASK;
        bases['H'] = A_MASK | C_MASK | T_MASK;
        bases['D'] = A_MASK | G_MASK | T_MASK;
        bases['B'] = C_MASK | G_MASK | T_MASK;
        bases['N'] = A_MASK | C_MASK | G_MASK | T_MASK;
        // Also store the bases in lower case
        for (int i = 'A'; i <= 'Z'; i++) {
            bases[(byte) i + 32] = bases[(byte) i];
        }
        bases['.'] = A_MASK | C_MASK | G_MASK | T_MASK;
    };


    /**
     * Calculate the reverse complement of the specified sequence
     * (Stolen from Reseq)
     *
     * @param sequenceData
     * @return reverse complement
     */
    public static String reverseComplement(final String sequenceData) {
        final byte[] bases = htsjdk.samtools.util.StringUtil.stringToBytes(sequenceData);
        reverseComplement(bases);
        return htsjdk.samtools.util.StringUtil.bytesToString(bases);
    }


    /**
     * Efficiently compare two IUPAC base codes, simply returning true if they are equal (ignoring case),
     * without considering the set relationships between ambiguous codes.
     */
    public static boolean basesEqual(final byte lhs, final byte rhs) {
        return (bases[lhs] == bases[rhs]);
    }

    /**
     * Efficiently compare two IUPAC base codes, one coming from a read sequence and the other coming from
     * a reference sequence, using the reference code as a 'pattern' that the read base must match.
     *
     * We take ambiguous codes into account, returning true if the set of possible bases
     * represented by the read value is a (non-strict) subset of the possible bases represented
     * by the reference value.
     *
     * Since the comparison is directional, make sure to pass read / ref codes in correct order.
     */
    public static boolean readBaseMatchesRefBaseWithAmbiguity(final byte readBase, final byte refBase) {
        return (bases[readBase] & bases[refBase]) == bases[readBase];
    }

    /**
     * returns true if the value of base represents a no call
     */
    public static boolean isNoCall(final byte base) {
        return base == 'N' || base == 'n' || base == '.';
    }

    /** Returns true if the byte is in [acgtACGT]. */
    public static boolean isValidBase(final byte b) {
        return isValidBase(b, VALID_BASES_UPPER) || isValidBase(b, VALID_BASES_LOWER);
    }

    private static boolean isValidBase(final byte b, final byte[] validBases) {
        for (final byte validBase : validBases) {
            if (b == validBase) return true;
        }
        return false;
    }

    /** Calculates the fraction of bases that are G/C in the sequence. */
    public static double calculateGc(final byte[] bases) {
        int gcs = 0;
        for (int i = 0; i < bases.length; ++i) {
            final byte b = bases[i];
            if (b == 'C' || b == 'G' || b == 'c' || b == 'g') ++gcs;
        }

        return gcs / (double) bases.length;
    }

    /**
     * default signature that forces the lists to be the same size
     *
     * @param s1 a list of sequence headers
     * @param s2 a second list of sequence headers
     */
    public static void assertSequenceListsEqual(final List<SAMSequenceRecord> s1, final List<SAMSequenceRecord> s2) {
        assertSequenceListsEqual(s1, s2, false);
    }
    /**
     * Throws an exception only if both (first) parameters are not null
     * optionally check that one list is a (nonempty) prefix of the other.
     *
     * @param s1 a list of sequence headers
     * @param s2 a second list of sequence headers
     * @param checkPrefixOnly a flag specifying whether to only look at the first records in the lists. This will then check that the
     * records of the smaller dictionary are equal to the records of the beginning of the larger dictionary, which can be useful since
     * sometimes different pipelines choose to use only the first contigs of a standard reference.
     */
    public static void assertSequenceListsEqual(final List<SAMSequenceRecord> s1, final List<SAMSequenceRecord> s2, final boolean checkPrefixOnly) {
        if (s1 != null && s2 != null) {

            final int sizeToTest;

            if (checkPrefixOnly) {
                sizeToTest = Math.min(s1.size(), s2.size());
                if (sizeToTest == 0) {
                    throw new SequenceListsDifferException("Neither of the dictionaries can be empty.");
                }
            } else {
                sizeToTest = s1.size();
                if (s1.size() != s2.size()) {
                    throw new SequenceListsDifferException(
                            "Sequence dictionaries are not the same size (" + s1.size() + ", " + s2.size() +
                                    ")");
                }
            }
            for (int i = 0; i < sizeToTest; ++i) {
                if (!s1.get(i).isSameSequence(s2.get(i))) {
                    String s1Attrs = "";
                    for (final java.util.Map.Entry<String, String> entry : s1.get(i)
                            .getAttributes()) {
                        s1Attrs += "/" + entry.getKey() + "=" + entry.getValue();
                    }
                    String s2Attrs = "";
                    for (final java.util.Map.Entry<String, String> entry : s2.get(i)
                            .getAttributes()) {
                        s2Attrs += "/" + entry.getKey() + "=" + entry.getValue();
                    }
                    throw new SequenceListsDifferException(
                            "Sequences at index " + i + " don't match: " +
                                    s1.get(i).getSequenceIndex() + "/" + s1.get(i).getSequenceLength() +
                                    "/" + s1.get(i).getSequenceName() + s1Attrs + " " +
                                    s2.get(i).getSequenceIndex() + "/" + s2.get(i).getSequenceLength() +
                                    "/" + s2.get(i).getSequenceName() + s2Attrs);
                }
            }
        }
    }

    public static class SequenceListsDifferException extends SAMException {
        public SequenceListsDifferException() {
        }

        public SequenceListsDifferException(final String s) {
            super(s);
        }

        public SequenceListsDifferException(final String s, final Throwable throwable) {
            super(s, throwable);
        }

        public SequenceListsDifferException(final Throwable throwable) {
            super(throwable);
        }
    }

    /**
     * Returns true if both parameters are null or equal, otherwise returns false
     *
     * @param s1 a list of sequence headers
     * @param s2 a second list of sequence headers
     */
    public static boolean areSequenceDictionariesEqual(final SAMSequenceDictionary s1, final SAMSequenceDictionary s2) {
        if (s1 == null && s2 == null) return true;
        if (s1 == null || s2 == null) return false;

        try {
            assertSequenceListsEqual(s1.getSequences(), s2.getSequences());
            return true;
        } catch (final SequenceListsDifferException e) {
            return false;
        }
    }

    /**
     * Throws an exception if both parameters are non-null and unequal.
     *
     * @param s1 a list of sequence headers
     * @param s2 a second list of sequence headers
     */
    public static void assertSequenceDictionariesEqual(final SAMSequenceDictionary s1, final SAMSequenceDictionary s2) {
        assertSequenceDictionariesEqual(s1, s2, false);
    }

    /**
     * Throws an exception if both (first) parameters are non-null and unequal (if checkPrefixOnly, checks prefix of lists only).
     *
     * @param s1 a list of sequence headers
     * @param s2 a second list of sequence headers
     * @param checkPrefixOnly a flag specifying whether to only look at the first records in the lists. This will then check that the
     * records of the smaller dictionary are equal to the records of the beginning of the larger dictionary, which can be useful since
     * sometimes different pipelines choose to use only the first contigs of a standard reference.
     */
    public static void assertSequenceDictionariesEqual(final SAMSequenceDictionary s1, final SAMSequenceDictionary s2, final boolean checkPrefixOnly) {
        if (s1 == null || s2 == null) return;
        assertSequenceListsEqual(s1.getSequences(), s2.getSequences(), checkPrefixOnly);
    }

    /**
     * Throws an exception if both parameters are non-null and unequal, including the filenames.
     */
    public static void assertSequenceDictionariesEqual(final SAMSequenceDictionary s1, final SAMSequenceDictionary s2,
                                                       final File f1, final File f2) {
        try {
            assertSequenceDictionariesEqual(s1, s2);
        } catch (final SequenceListsDifferException e) {
            throw new SequenceListsDifferException("In files " + f1.getAbsolutePath() + " and " + f2.getAbsolutePath(), e);
        }
    }

    /**
     * Create a simple ungapped cigar string, which might have soft clipping at either end
     *
     * @param alignmentStart raw aligment start, which may result in read hanging off beginning or end of read
     * @return cigar string that may have S operator at beginning or end, and has M operator for the rest of the read
     */
    public static String makeCigarStringWithPossibleClipping(final int alignmentStart, final int readLength, final int referenceSequenceLength) {
        int start = alignmentStart;
        int leftSoftClip = 0;
        if (start < 1) {
            leftSoftClip = 1 - start;
            start = 1;
        }
        int rightSoftClip = 0;
        if (alignmentStart + readLength > referenceSequenceLength + 1) {
            rightSoftClip = alignmentStart + readLength - referenceSequenceLength - 1;
        }
        // CIGAR is trivial because there are no indels or clipping in Gerald
        final int matchLength = readLength - leftSoftClip - rightSoftClip;
        if (matchLength < 1) {
            throw new SAMException("Unexpected cigar string with no M op for read.");
        }
        return makeSoftClipCigar(leftSoftClip) + Integer.toString(matchLength) + "M" + makeSoftClipCigar(rightSoftClip);
    }

    /**
     * Create a cigar string for a gapped alignment, which may have soft clipping at either end
     *
     * @param alignmentStart          raw alignment start, which may result in read hanging off beginning or end of read
     * @param readLength
     * @param referenceSequenceLength
     * @param indelPosition           number of matching bases before indel.  Must be > 0
     * @param indelLength             length of indel.  Positive for insertion, negative for deletion.
     * @return cigar string that may have S operator at beginning or end, has one or two M operators, and an I or a D.
     */
    public static String makeCigarStringWithIndelPossibleClipping(final int alignmentStart,
                                                                  final int readLength,
                                                                  final int referenceSequenceLength,
                                                                  final int indelPosition,
                                                                  final int indelLength) {
        int start = alignmentStart;
        int leftSoftClip = 0;
        if (start < 1) {
            leftSoftClip = 1 - start;
            start = 1;
        }
        int rightSoftClip = 0;
        final int alignmentEnd = alignmentStart + readLength - indelLength;
        if (alignmentEnd > referenceSequenceLength + 1) {
            rightSoftClip = alignmentEnd - referenceSequenceLength - 1;
        }
        if (leftSoftClip >= indelPosition) {
            throw new IllegalStateException("Soft clipping entire pre-indel match. leftSoftClip: " + leftSoftClip +
                    "; indelPosition: " + indelPosition);
        }
        // CIGAR is trivial because there are no indels or clipping in Gerald
        final int firstMatchLength = indelPosition - leftSoftClip;
        final int secondMatchLength = readLength - indelPosition - (indelLength > 0 ? indelLength : 0) - rightSoftClip;
        if (secondMatchLength < 1) {
            throw new SAMException("Unexpected cigar string with no M op for read.");
        }
        return makeSoftClipCigar(leftSoftClip) + Integer.toString(firstMatchLength) + "M" +
                Math.abs(indelLength) + (indelLength > 0 ? "I" : "D") +
                Integer.toString(secondMatchLength) + "M" +
                makeSoftClipCigar(rightSoftClip);
    }

    public static String makeSoftClipCigar(final int clipLength) {
        if (clipLength == 0) {
            return "";
        }
        return Integer.toString(clipLength) + "S";
    }

    /**
     * Helper method to handle the various use cases of base comparison.
     *
     * @param readBase the read base to match
     * @param refBase the reference base to match
     * @param negativeStrand set to true if the base to test is on the negative strand and should be reverse complemented (only applies if bisulfiteSequence is true)
     * @param bisulfiteSequence set to true if the base to match is a bisulfite sequence and needs to be converted
     * @param matchAmbiguousRef causes the match to return true when the read base is a subset of the possible IUPAC reference bases, but not the other way around
     * @return true if the bases match, false otherwise
     */
    private static boolean basesMatch(final byte readBase, final byte refBase, final boolean negativeStrand,
                                      final boolean bisulfiteSequence, final boolean matchAmbiguousRef) {
        if (bisulfiteSequence) {
            if (matchAmbiguousRef) return bisulfiteBasesMatchWithAmbiguity(negativeStrand, readBase, refBase);
            else return bisulfiteBasesEqual(negativeStrand, readBase, refBase);
        } else {
            if (matchAmbiguousRef) return readBaseMatchesRefBaseWithAmbiguity(readBase, refBase);
            else return basesEqual(readBase, refBase);
        }
    }

    /** Calculates the number of mismatches between the read and the reference sequence provided. */
    public static int countMismatches(final SAMRecord read, final byte[] referenceBases) {
        return countMismatches(read, referenceBases, 0, false);
    }

    /** Calculates the number of mismatches between the read and the reference sequence provided. */
    public static int countMismatches(final SAMRecord read, final byte[] referenceBases, final int referenceOffset) {
        return countMismatches(read, referenceBases, referenceOffset, false);
    }

    /**
     * Calculates the number of mismatches between the read and the reference sequence provided.
     *
     * @param referenceBases    Array of ASCII bytes that covers at least the the portion of the reference sequence
     *                          to which read is aligned from getReferenceStart to getReferenceEnd.
     * @param referenceOffset   0-based offset of the first element of referenceBases relative to the start
     *                          of that reference sequence.
     * @param bisulfiteSequence If this is true, it is assumed that the reads were bisulfite treated
     *                          and C->T on the positive strand and G->A on the negative strand will not be counted
     *                          as mismatches.
     */
    public static int countMismatches(final SAMRecord read, final byte[] referenceBases, final int referenceOffset, final boolean bisulfiteSequence) {
        return countMismatches(read, referenceBases, referenceOffset, bisulfiteSequence, false);
    }

    public static int countMismatches(final SAMRecord read, final byte[] referenceBases, final int referenceOffset,
                                      final boolean bisulfiteSequence, final boolean matchAmbiguousRef) {
        try {
            int mismatches = 0;

            final byte[] readBases = read.getReadBases();

            for (final AlignmentBlock block : read.getAlignmentBlocks()) {
                final int readBlockStart = block.getReadStart() - 1;
                final int referenceBlockStart = block.getReferenceStart() - 1 - referenceOffset;
                final int length = block.getLength();

                for (int i = 0; i < length; ++i) {
                    if (!basesMatch(readBases[readBlockStart + i], referenceBases[referenceBlockStart + i],
                            read.getReadNegativeStrandFlag(), bisulfiteSequence, matchAmbiguousRef)) {
                        ++mismatches;
                    }
                }
            }
            return mismatches;
        } catch (final Exception e) {
            throw new SAMException("Exception counting mismatches for read " + read, e);
        }
    }

    /**
     * Calculates the number of mismatches between the read and the reference sequence provided.
     *
     * @param referenceBases    Array of ASCII bytes that covers at least the the portion of the reference sequence
     *                          to which read is aligned from getReferenceStart to getReferenceEnd.
     * @param bisulfiteSequence If this is true, it is assumed that the reads were bisulfite treated
     *                          and C->T on the positive strand and G->A on the negative strand will not be counted
     *                          as mismatches.
     */
    public static int countMismatches(final SAMRecord read, final byte[] referenceBases, final boolean bisulfiteSequence) {
        return countMismatches(read, referenceBases, 0, bisulfiteSequence);
    }

    /**
     * Sadly, this is a duplicate of the method above, except that it takes char[] for referenceBases rather
     * than byte[].  This is because GATK needs it this way.
     * <p/>
     * TODO: Remove this method when GATK map method is changed to take refseq as byte[].
     * TODO: UPDATE: Seems to be removed from GATK. Deprecated now to be removed in a future version.
     */
    @Deprecated
    private static int countMismatches(final SAMRecord read, final char[] referenceBases, final int referenceOffset) {
        int mismatches = 0;

        final byte[] readBases = read.getReadBases();

        for (final AlignmentBlock block : read.getAlignmentBlocks()) {
            final int readBlockStart = block.getReadStart() - 1;
            final int referenceBlockStart = block.getReferenceStart() - 1 - referenceOffset;
            final int length = block.getLength();

            for (int i = 0; i < length; ++i) {
                if (!basesEqual(readBases[readBlockStart + i], StringUtil.charToByte(referenceBases[referenceBlockStart + i]))) {
                    ++mismatches;
                }
            }
        }
        return mismatches;
    }

    /**
     * Calculates the sum of qualities for mismatched bases in the read.
     *
     * @param referenceBases Array of ASCII bytes in which the 0th position in the array corresponds
     *                       to the first element of the reference sequence to which read is aligned.
     */
    public static int sumQualitiesOfMismatches(final SAMRecord read, final byte[] referenceBases) {
        return sumQualitiesOfMismatches(read, referenceBases, 0, false);
    }

    /**
     * Calculates the sum of qualities for mismatched bases in the read.
     *
     * @param referenceBases  Array of ASCII bytes that covers at least the the portion of the reference sequence
     *                        to which read is aligned from getReferenceStart to getReferenceEnd.
     * @param referenceOffset 0-based offset of the first element of referenceBases relative to the start
     *                        of that reference sequence.
     */
    public static int sumQualitiesOfMismatches(final SAMRecord read, final byte[] referenceBases,
                                               final int referenceOffset) {
        return sumQualitiesOfMismatches(read, referenceBases, referenceOffset, false);
    }

    /**
     * Calculates the sum of qualities for mismatched bases in the read.
     *
     * @param referenceBases    Array of ASCII bytes that covers at least the the portion of the reference sequence
     *                          to which read is aligned from getReferenceStart to getReferenceEnd.
     * @param referenceOffset   0-based offset of the first element of referenceBases relative to the start
     *                          of that reference sequence.
     * @param bisulfiteSequence If this is true, it is assumed that the reads were bisulfite treated
     *                          and C->T on the positive strand and G->A on the negative strand will not be counted
     *                          as mismatches.
     */
    public static int sumQualitiesOfMismatches(final SAMRecord read, final byte[] referenceBases,
                                               final int referenceOffset, final boolean bisulfiteSequence) {
        int qualities = 0;

        final byte[] readBases = read.getReadBases();
        final byte[] readQualities = read.getBaseQualities();

        if (read.getAlignmentStart() <= referenceOffset) {
            throw new IllegalArgumentException("read.getAlignmentStart(" + read.getAlignmentStart() +
                    ") <= referenceOffset(" + referenceOffset + ")");
        }

        for (final AlignmentBlock block : read.getAlignmentBlocks()) {
            final int readBlockStart = block.getReadStart() - 1;
            final int referenceBlockStart = block.getReferenceStart() - 1 - referenceOffset;
            final int length = block.getLength();

            for (int i = 0; i < length; ++i) {
                if (!bisulfiteSequence) {
                    if (!basesEqual(readBases[readBlockStart + i], referenceBases[referenceBlockStart + i])) {
                        qualities += readQualities[readBlockStart + i];
                    }

                } else {
                    if (!bisulfiteBasesEqual(read.getReadNegativeStrandFlag(), readBases[readBlockStart + i],
                            referenceBases[referenceBlockStart + i])) {
                        qualities += readQualities[readBlockStart + i];
                    }
                }
            }
        }

        return qualities;
    }

    /**
     * Sadly, this is a duplicate of the method above, except that it takes char[] for referenceBases rather
     * than byte[].  This is because GATK needs it this way.
     * <p/>
     * TODO: Remove this method when GATK map method is changed to take refseq as byte[].
     * TODO: UPDATE: Seems to be removed from GATK. Deprecated now to be removed in a future version.
     */
    @Deprecated
    public static int sumQualitiesOfMismatches(final SAMRecord read, final char[] referenceBases,
                                               final int referenceOffset) {
        int qualities = 0;

        final byte[] readBases = read.getReadBases();
        final byte[] readQualities = read.getBaseQualities();

        if (read.getAlignmentStart() <= referenceOffset) {
            throw new IllegalArgumentException("read.getAlignmentStart(" + read.getAlignmentStart() +
                    ") <= referenceOffset(" + referenceOffset + ")");
        }

        for (final AlignmentBlock block : read.getAlignmentBlocks()) {
            final int readBlockStart = block.getReadStart() - 1;
            final int referenceBlockStart = block.getReferenceStart() - 1 - referenceOffset;
            final int length = block.getLength();

            for (int i = 0; i < length; ++i) {
                if (!basesEqual(readBases[readBlockStart + i], StringUtil.charToByte(referenceBases[referenceBlockStart + i]))) {
                    qualities += readQualities[readBlockStart + i];
                }
            }
        }

        return qualities;
    }

    public static int countInsertedBases(final Cigar cigar) {
        int ret = 0;
        for (final CigarElement element : cigar.getCigarElements()) {
            if (element.getOperator() == CigarOperator.INSERTION) ret += element.getLength();
        }
        return ret;
    }

    public static int countDeletedBases(final Cigar cigar) {
        int ret = 0;
        for (final CigarElement element : cigar.getCigarElements()) {
            if (element.getOperator() == CigarOperator.DELETION) ret += element.getLength();
        }
        return ret;
    }

    public static int countInsertedBases(final SAMRecord read) {
        return countInsertedBases(read.getCigar());
    }

    public static int countDeletedBases(final SAMRecord read) {
        return countDeletedBases(read.getCigar());
    }

    /**
     * Calculates the predefined NM tag from the SAM spec: (# of mismatches + # of indels)
     * For the purposes for calculating mismatches, we do not yet support IUPAC ambiguous codes
     * (see <code>readBaseMatchesRefBaseWithAmbiguity</code> method).
     */
    public static int calculateSamNmTag(final SAMRecord read, final byte[] referenceBases) {
        return calculateSamNmTag(read, referenceBases, 0, false);
    }

    /**
     * Calculates the predefined NM tag from the SAM spec: (# of mismatches + # of indels)
     * For the purposes for calculating mismatches, we do not yet support IUPAC ambiguous codes
     * (see <code>readBaseMatchesRefBaseWithAmbiguity</code> method).
     *
     * @param referenceOffset 0-based offset of the first element of referenceBases relative to the start
     *                        of that reference sequence.
     */
    public static int calculateSamNmTag(final SAMRecord read, final byte[] referenceBases,
                                        final int referenceOffset) {
        return calculateSamNmTag(read, referenceBases, referenceOffset, false);
    }

    /**
     * Calculates the predefined NM tag from the SAM spec: (# of mismatches + # of indels)
     * For the purposes for calculating mismatches, we do not yet support IUPAC ambiguous codes
     * (see <code>readBaseMatchesRefBaseWithAmbiguity</code> method).
     *
     * @param referenceOffset   0-based offset of the first element of referenceBases relative to the start
     *                          of that reference sequence.
     * @param bisulfiteSequence If this is true, it is assumed that the reads were bisulfite treated
     *                          and C->T on the positive strand and G->A on the negative strand will not be counted
     *                          as mismatches.
     */
    public static int calculateSamNmTag(final SAMRecord read, final byte[] referenceBases,
                                        final int referenceOffset, final boolean bisulfiteSequence) {
        int samNm = countMismatches(read, referenceBases, referenceOffset, bisulfiteSequence, false);
        for (final CigarElement el : read.getCigar().getCigarElements()) {
            if (el.getOperator() == CigarOperator.INSERTION || el.getOperator() == CigarOperator.DELETION) {
                samNm += el.getLength();
            }
        }
        return samNm;
    }

    /**
     * Attempts to calculate the predefined NM tag from the SAM spec using the cigar string alone.
     * It may calculate incorrectly if ambiguous operators (Like M) are used.
     *
     * Needed for testing infrastructure: SAMRecordSetBuilder
     */
    public static int calculateSamNmTagFromCigar(final SAMRecord record) {
        int samNm = 0;
        for (final CigarElement el : record.getCigar().getCigarElements()) {
            if ( el.getOperator() == CigarOperator.X ||
                 el.getOperator() == CigarOperator.INSERTION ||
                 el.getOperator() == CigarOperator.DELETION) {
                samNm += el.getLength();
            }
        }
        return samNm;
    }


    /**
     * Sadly, this is a duplicate of the method above, except that it takes char[] for referenceBases rather
     * than byte[].  This is because GATK needs it this way.
     * <p/>
     * TODO: Remove this method when GATK map method is changed to take refseq as byte[].
     * TODO: UPDATE: Seems to be removed from GATK. Deprecated now to be removed in a future version.
     */
    @Deprecated
    public static int calculateSamNmTag(final SAMRecord read, final char[] referenceBases,
                                        final int referenceOffset) {
        int samNm = countMismatches(read, referenceBases, referenceOffset);
        for (final CigarElement el : read.getCigar().getCigarElements()) {
            if (el.getOperator() == CigarOperator.INSERTION || el.getOperator() == CigarOperator.DELETION) {
                samNm += el.getLength();
            }
        }
        return samNm;
    }

    /** Returns the complement of a single byte. */
    public static byte complement(final byte b) {
        switch (b) {
            case a:
                return t;
            case c:
                return g;
            case g:
                return c;
            case t:
                return a;
            case A:
                return T;
            case C:
                return G;
            case G:
                return C;
            case T:
                return A;
            default:
                return b;
        }
    }

    /** Reverses and complements the bases in place. */
    public static void reverseComplement(final byte[] bases) {
        final int lastIndex = bases.length - 1;

        int i, j;
        for (i = 0, j = lastIndex; i < j; ++i, --j) {
            final byte tmp = complement(bases[i]);
            bases[i] = complement(bases[j]);
            bases[j] = tmp;
        }
        if (bases.length % 2 == 1) {
            bases[i] = complement(bases[i]);
        }
    }

    /** Reverses the quals in place. */
    public static void reverseQualities(final byte[] quals) {
        final int lastIndex = quals.length - 1;

        int i, j;
        for (i = 0, j = lastIndex; i < j; ++i, --j) {
            final byte tmp = quals[i];
            quals[i] = quals[j];
            quals[j] = tmp;
        }
    }

    /**
     * Returns true if the bases are equal OR if the mismatch can be accounted for by
     * bisulfite treatment. C->T on the positive strand and G->A on the negative strand
     * do not count as mismatches.
     */
    public static boolean bisulfiteBasesEqual(final boolean negativeStrand, final byte read, final byte reference) {
        return (basesEqual(read, reference)) || (isBisulfiteConverted(read, reference, negativeStrand));
    }

    public static boolean bisulfiteBasesEqual(final byte read, final byte reference) {
        return bisulfiteBasesEqual(false, read, reference);
    }

    /**
     * Same as above, but use <code>readBaseMatchesRefBaseWithAmbiguity</code> instead of <code>basesEqual</code>.
     * Note that <code>isBisulfiteConverted</code> is not affected because it only applies when the
     * reference base is non-ambiguous.
     */
    public static boolean bisulfiteBasesMatchWithAmbiguity(final boolean negativeStrand, final byte read, final byte reference) {
        return (readBaseMatchesRefBaseWithAmbiguity(read, reference)) || (isBisulfiteConverted(read, reference, negativeStrand));
    }

    /**
     * Checks for bisulfite conversion, C->T on the positive strand and G->A on the negative strand.
     */
    public static boolean isBisulfiteConverted(final byte read, final byte reference, final boolean negativeStrand) {
        if (negativeStrand) {
            if (basesEqual(reference, G) && basesEqual(read, A)) {
                return true;
            }
        } else {
            if (basesEqual(reference, C) && basesEqual(read, T)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBisulfiteConverted(final byte read, final byte reference) {
        return isBisulfiteConverted(read, reference, false);
    }

    /*
     * Regexp for MD string.
     *
     * \G = end of previous match.
     * (?:[0-9]+) non-capturing (why non-capturing?) group of digits.  For this number of bases read matches reference.
     *  - or -
     * Single reference base for case in which reference differs from read.
     *  - or -
     * ^one or more reference bases that are deleted in read.
     *
     */
    static final Pattern mdPat = Pattern.compile("\\G(?:([0-9]+)|([ACTGNactgn])|(\\^[ACTGNactgn]+))");

    /**
     * Produce reference bases from an aligned SAMRecord with MD string and Cigar.
     *
     * @param rec                               Must contain non-empty CIGAR and MD attribute.
     * @param includeReferenceBasesForDeletions If true, include reference bases that are deleted in the read.
     *                                          This will make the returned array not line up with the read if there are deletions.
     * @return References bases corresponding to the read.  If there is an insertion in the read, reference contains
     * '-'.  If the read is soft-clipped, reference contains '0'.  If there is a skipped region and
     * includeReferenceBasesForDeletions==true, reference will have Ns for the skipped region.
     */
    public static byte[] makeReferenceFromAlignment(final SAMRecord rec, final boolean includeReferenceBasesForDeletions) {
        final String md = rec.getStringAttribute(SAMTag.MD.name());
        if (md == null) {
            throw new SAMException("Cannot create reference from SAMRecord with no MD tag, read: " + rec.getReadName());
        }
        // Not sure how long output will be, but it will be no longer than this.
        int maxOutputLength = 0;
        final Cigar cigar = rec.getCigar();
        if (cigar == null) {
            throw new SAMException("Cannot create reference from SAMRecord with no CIGAR, read: " + rec.getReadName());
        }
        for (final CigarElement cigarElement : cigar.getCigarElements()) {
            maxOutputLength += cigarElement.getLength();
        }
        final byte[] ret = new byte[maxOutputLength];
        int outIndex = 0;

        final Matcher match = mdPat.matcher(md);
        int curSeqPos = 0;

        int savedBases = 0;
        final byte[] seq = rec.getReadBases();
        for (final CigarElement cigEl : cigar.getCigarElements()) {
            final int cigElLen = cigEl.getLength();
            final CigarOperator cigElOp = cigEl.getOperator();


            if (cigElOp == CigarOperator.SKIPPED_REGION) {
                // We've decided that MD tag will not contain bases for skipped regions, as they
                // could be megabases long, so just put N in there if caller wants reference bases,
                // otherwise ignore skipped regions.
                if (includeReferenceBasesForDeletions) {
                    for (int i = 0; i < cigElLen; ++i) {
                        ret[outIndex++] = N;
                    }
                }
            }
            // If it consumes reference bases, it's either a match or a deletion in the sequence
            // read.  Either way, we're going to need to parse through the MD.
            else if (cigElOp.consumesReferenceBases()) {
                // We have a match region, go through the MD
                int basesMatched = 0;

                // Do we have any saved matched bases?
                while ((savedBases > 0) && (basesMatched < cigElLen)) {
                    ret[outIndex++] = seq[curSeqPos++];
                    savedBases--;
                    basesMatched++;
                }

                while (basesMatched < cigElLen) {
                    boolean matched = match.find();
                    if (matched) {
                        String mg;
                        if (((mg = match.group(1)) != null) && (!mg.isEmpty())) {
                            // It's a number , meaning a series of matches
                            final int num = Integer.parseInt(mg);
                            for (int i = 0; i < num; i++) {
                                if (basesMatched < cigElLen) {
                                    ret[outIndex++] = seq[curSeqPos++];
                                } else {
                                    savedBases++;
                                }
                                basesMatched++;
                            }
                        } else if (((mg = match.group(2)) != null) && (!mg.isEmpty())) {
                            // It's a single nucleotide, meaning a mismatch
                            if (basesMatched < cigElLen) {
                                ret[outIndex++] = StringUtil.charToByte(mg.charAt(0));
                                curSeqPos++;
                            } else {
                                throw new IllegalStateException("Should never happen.");
                            }
                            basesMatched++;
                        } else if (((mg = match.group(3)) != null) && (!mg.isEmpty())) {
                            // It's a deletion, starting with a caret
                            // don't include caret
                            if (includeReferenceBasesForDeletions) {
                                final byte[] deletedBases = StringUtil.stringToBytes(mg);
                                System.arraycopy(deletedBases, 1, ret, outIndex, deletedBases.length - 1);
                                outIndex += deletedBases.length - 1;
                            }
                            basesMatched += mg.length() - 1;

                            // Check just to make sure.
                            if (basesMatched != cigElLen) {
                                throw new SAMException("Got a deletion in CIGAR (" + cigar + ", deletion " + cigElLen +
                                        " length) with an unequal ref insertion in MD (" + md + ", md " + basesMatched + " length");
                            }
                            if (cigElOp != CigarOperator.DELETION) {
                                throw new SAMException("Got an insertion in MD (" + md + ") without a corresponding deletion in cigar (" + cigar + ")");
                            }

                        } else {
                            matched = false;
                        }
                    }

                    if (!matched) {
                        throw new SAMException("Illegal MD pattern: " + md + " for read " + rec.getReadName() +
                                " with CIGAR " + rec.getCigarString());
                    }
                }

            } else if (cigElOp.consumesReadBases()) {
                // We have an insertion in read
                for (int i = 0; i < cigElLen; i++) {
                    final char c = (cigElOp == CigarOperator.SOFT_CLIP) ? '0' : '-';
                    ret[outIndex++] = StringUtil.charToByte(c);
                    curSeqPos++;
                }
            } else {
                // It's an op that consumes neither read nor reference bases.  Do we just ignore??
            }

        }
        if (outIndex < ret.length) {
            final byte[] shorter = new byte[outIndex];
            System.arraycopy(ret, 0, shorter, 0, outIndex);
            return shorter;
        }
        return ret;
    }

    public static void reverse(final byte[] array, final int offset, final int len) {
        final int lastIndex = len - 1;

        int i, j;
        for (i = offset, j = offset + lastIndex; i < j; ++i, --j) {
            final byte tmp = array[i];
            array[i] = array[j];
            array[j] = tmp;
        }
        if (len % 2 == 1) {
            array[i] = array[i];
        }
    }

    public static void reverseComplement(final byte[] bases, final int offset, final int len) {
        final int lastIndex = len - 1;

        int i, j;
        for (i = offset, j = offset + lastIndex; i < j; ++i, --j) {
            final byte tmp = complement(bases[i]);
            bases[i] = complement(bases[j]);
            bases[j] = tmp;
        }
        if (len % 2 == 1) {
            bases[i] = complement(bases[i]);
        }
    }

    public static String calculateMD5String(final byte[] data)
            throws NoSuchAlgorithmException {
        return SequenceUtil.calculateMD5String(data, 0, data.length);
    }

    public static String calculateMD5String(final byte[] data, final int offset, final int len) {
        final byte[] digest = calculateMD5(data, offset, len);
        return String.format("%032x", new BigInteger(1, digest));
    }

    public static byte[] calculateMD5(final byte[] data, final int offset, final int len) {
        final MessageDigest md5_MessageDigest;
        try {
            md5_MessageDigest = MessageDigest.getInstance("MD5");
            md5_MessageDigest.reset();

            md5_MessageDigest.update(data, offset, len);
            return md5_MessageDigest.digest();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Calculate MD and NM similarly to Samtools, except that N->N is a match.
     *
     * @param record
     * @param ref
     * @param calcMD
     * @return
     */
    public static void calculateMdAndNmTags(final SAMRecord record, final byte[] ref,
                                            final boolean calcMD, final boolean calcNM) {
        if (!calcMD && !calcNM)
            return;

        final Cigar cigar = record.getCigar();
        final List<CigarElement> cigarElements = cigar.getCigarElements();
        final byte[] seq = record.getReadBases();
        final int start = record.getAlignmentStart() - 1;
        int i, x, y, u = 0;
        int nm = 0;
        final StringBuilder str = new StringBuilder();

        final int size = cigarElements.size();
        for (i = y = 0, x = start; i < size; ++i) {
            final CigarElement ce = cigarElements.get(i);
            int j;
            final int length = ce.getLength();
            final CigarOperator op = ce.getOperator();
            if (op == CigarOperator.MATCH_OR_MISMATCH || op == CigarOperator.EQ
                    || op == CigarOperator.X) {
                for (j = 0; j < length; ++j) {
                    final int z = y + j;

                    if (ref.length <= x + j) break; // out of boundary

                    int c1 = 0;
                    int c2 = 0;
                    // try {
                    c1 = seq[z];
                    c2 = ref[x + j];

                    if ((c1 == c2) || c1 == 0) {
                        // a match
                        ++u;
                    } else {
                        str.append(u);
                        str.appendCodePoint(ref[x + j]);
                        u = 0;
                        ++nm;
                    }
                }
                if (j < length) break;
                x += length;
                y += length;
            } else if (op == CigarOperator.DELETION) {
                str.append(u);
                str.append('^');
                for (j = 0; j < length; ++j) {
                    if (ref[x + j] == 0) break;
                    str.appendCodePoint(ref[x + j]);
                }
                u = 0;
                if (j < length) break;
                x += length;
                nm += length;
            } else if (op == CigarOperator.INSERTION
                    || op == CigarOperator.SOFT_CLIP) {
                y += length;
                if (op == CigarOperator.INSERTION) nm += length;
            } else if (op == CigarOperator.SKIPPED_REGION) {
                x += length;
            }
        }
        str.append(u);

        if (calcMD) record.setAttribute(SAMTag.MD.name(), str.toString());
        if (calcNM) record.setAttribute(SAMTag.NM.name(), nm);
    }

    public static byte upperCase(final byte base) {
        return base >= a ? (byte) (base - (a - A)) : base;
    }

    public static byte[] upperCase(final byte[] bases) {
        for (int i = 0; i < bases.length; i++)
            bases[i] = upperCase(bases[i]);
        return bases;
    }

    /** Generates all possible unambiguous kmers (upper-case) of length and returns them as byte[]s. */
    public static List<byte[]> generateAllKmers(final int length) {
        final List<byte[]> sofar = new LinkedList<byte[]>();

        if (sofar.isEmpty()) {
            sofar.add(new byte[length]);
        }

        while (true) {
            final byte[] bs = sofar.remove(0);
            int indexOfNextBase = -1;
            for (int i = 0; i < bs.length; ++i) {
                if (bs[i] == 0) {
                    indexOfNextBase = i;
                    break;
                }
            }

            if (indexOfNextBase == -1) {
                sofar.add(bs);
                break;
            } else {
                for (final byte b : VALID_BASES_UPPER) {
                    final byte[] next = Arrays.copyOf(bs, bs.length);
                    next[indexOfNextBase] = b;
                    sofar.add(next);
                }
            }
        }

        return sofar;
    }

    /**
     * Returns a read name from a FASTQ header string suitable for use in a SAM/BAM file.  Any letters after the first space are ignored.
     * Ths method also strips trailing "/1" or "/2" so that paired end reads have the same name.
     *
     * @param fastqHeader the header from a {@link htsjdk.samtools.fastq.FastqRecord}.
     * @return a read name appropriate for output in a SAM/BAM file.
     */
    // Read names cannot contain blanks
    public static String getSamReadNameFromFastqHeader(final String fastqHeader) {
        final int idx = fastqHeader.indexOf(" ");
        String readName = (idx == -1) ? fastqHeader : fastqHeader.substring(0,idx);

        // NOTE: the while loop isn't necessarily the most efficient way to handle this but we don't
        // expect this to ever happen more than once, just trapping pathological cases
        while ((readName.endsWith("/1") || readName.endsWith("/2"))) {
            // If this is an unpaired run we want to make sure that "/1" isn't tacked on the end of the read name,
            // as this can cause problems down the road (ex. in Picard's MergeBamAlignment).
            readName = readName.substring(0, readName.length() - 2);
        }

        return readName;
    }
}
