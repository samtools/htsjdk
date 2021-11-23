package htsjdk.samtools;

import htsjdk.utils.ValidationUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 *
 * A series of utility functions that enable comparison of two sequence dictionaries -- from the reference,
 * from BAMs, or from feature sources -- for consistency.  The system supports two basic modes: get an enum state that
 * describes at a high level the consistency between two dictionaries, or a validateDictionaries that will
 * blow up with a UserException if the dicts are too incompatible.
 *
 * Dictionaries are tested for contig name overlaps, consistency in ordering in these overlap set, and length,
 * if available.
 */
public final class SAMSequenceDictionaryUtils {

    private SAMSequenceDictionaryUtils(){}

    /**
     * Compares sequence records by their order
     */
    private static final Comparator<SAMSequenceRecord> SEQUENCE_INDEX_ORDER = Comparator.comparing(SAMSequenceRecord::getSequenceIndex);

    // The following sets of contig records are used to perform the non-canonical human ordering check.
    // This check ensures that the order is 1,2,3... instead of 1, 10, 11, 12...2, 20, 21...

    // hg18
    protected static final SAMSequenceRecord CHR1_HG18 = new SAMSequenceRecord("chr1", 247249719);
    protected static final SAMSequenceRecord CHR2_HG18 = new SAMSequenceRecord("chr2", 242951149);
    protected static final SAMSequenceRecord CHR10_HG18 = new SAMSequenceRecord("chr10", 135374737);

    // hg19
    protected static final SAMSequenceRecord CHR1_HG19 = new SAMSequenceRecord("chr1", 249250621);
    protected static final SAMSequenceRecord CHR2_HG19 = new SAMSequenceRecord("chr2", 243199373);
    protected static final SAMSequenceRecord CHR10_HG19 = new SAMSequenceRecord("chr10", 135534747);

    // b36
    protected static final SAMSequenceRecord CHR1_B36 = new SAMSequenceRecord("1", 247249719);
    protected static final SAMSequenceRecord CHR2_B36 = new SAMSequenceRecord("2", 242951149);
    protected static final SAMSequenceRecord CHR10_B36 = new SAMSequenceRecord("10", 135374737);

    // b37
    protected static final SAMSequenceRecord CHR1_B37 = new SAMSequenceRecord("1", 249250621);
    protected static final SAMSequenceRecord CHR2_B37 = new SAMSequenceRecord("2", 243199373);
    protected static final SAMSequenceRecord CHR10_B37 = new SAMSequenceRecord("10", 135534747);


    public enum SequenceDictionaryCompatibility {
        IDENTICAL,                      // the dictionaries are identical
        COMMON_SUBSET,                  // there exists a common subset of equivalent contigs
        SUPERSET,                       // the first dict's set of contigs supersets the second dict's set
        NO_COMMON_CONTIGS,              // no overlap between dictionaries
        UNEQUAL_COMMON_CONTIGS,         // common subset has contigs that have the same name but different lengths
        NON_CANONICAL_HUMAN_ORDER,      // human reference detected but the order of the contigs is non-standard (lexicographic, for example)
        OUT_OF_ORDER,                   // the two dictionaries overlap but the overlapping contigs occur in different
        // orders with respect to each other
        DIFFERENT_INDICES               // the two dictionaries overlap and the overlapping contigs occur in the same
        // order with respect to each other, but one or more of them have different
        // indices in the two dictionaries. Eg., { chrM, chr1, chr2 } vs. { chr1, chr2 }
    }

    /**
     * Workhorse routine that takes two dictionaries and returns their compatibility.
     *
     * @param dict1 first sequence dictionary
     * @param dict2 second sequence dictionary
     * @param checkContigOrdering if true, perform checks related to contig ordering: forbid lexicographically-sorted
     *                            dictionaries, and require common contigs to be in the same relative order and at the
     *                            same absolute indices
     * @return A SequenceDictionaryCompatibility enum value describing the compatibility of the two dictionaries
     */
    public static SequenceDictionaryCompatibility compareDictionaries( final SAMSequenceDictionary dict1, final SAMSequenceDictionary dict2, final boolean checkContigOrdering ) {
        if ( checkContigOrdering && (nonCanonicalHumanContigOrder(dict1) || nonCanonicalHumanContigOrder(dict2)) ) {
            return SequenceDictionaryCompatibility.NON_CANONICAL_HUMAN_ORDER;
        }

        final Set<String> commonContigs = getCommonContigsByName(dict1, dict2);

        if (commonContigs.isEmpty()) {
            return SequenceDictionaryCompatibility.NO_COMMON_CONTIGS;
        }
        else if ( ! commonContigsHaveSameLengths(commonContigs, dict1, dict2) ) {
            return SequenceDictionaryCompatibility.UNEQUAL_COMMON_CONTIGS;
        }

        final boolean commonContigsAreInSameRelativeOrder = commonContigsAreInSameRelativeOrder(commonContigs, dict1, dict2);

        if ( checkContigOrdering && ! commonContigsAreInSameRelativeOrder ) {
            return SequenceDictionaryCompatibility.OUT_OF_ORDER;
        }
        else if ( commonContigsAreInSameRelativeOrder && commonContigs.size() == dict1.size() && commonContigs.size() == dict2.size() ) {
            return SequenceDictionaryCompatibility.IDENTICAL;
        }
        else if ( checkContigOrdering && ! commonContigsAreAtSameIndices(commonContigs, dict1, dict2) ) {
            return SequenceDictionaryCompatibility.DIFFERENT_INDICES;
        }
        else if ( supersets(dict1, dict2) ) {
            return SequenceDictionaryCompatibility.SUPERSET;
        }
        else {
            return SequenceDictionaryCompatibility.COMMON_SUBSET;
        }
    }


    /**
     * Utility function that tests whether dict1's set of contigs is a superset of dict2's
     *
     * @param dict1 first sequence dictionary
     * @param dict2 second sequence dictionary
     * @return true if dict1's set of contigs supersets dict2's
     */
    private static boolean supersets( SAMSequenceDictionary dict1, SAMSequenceDictionary dict2 ) {
        // Cannot rely on SAMSequenceRecord.equals() as it's too strict (takes extended attributes into account).
        for ( final SAMSequenceRecord dict2Record : dict2.getSequences() ) {
            final SAMSequenceRecord dict1Record = dict1.getSequence(dict2Record.getSequenceName());
            if ( dict1Record == null || ! sequenceRecordsAreEquivalent(dict2Record, dict1Record) ) {
                return false;
            }
        }

        return true;
    }



    /**
     * Utility function that tests whether the commonContigs in both dicts are equivalent.  Equivalence means
     * that the seq records have the same length, if both are non-zero.
     *
     * @param commonContigs
     * @param dict1
     * @param dict2
     * @return true if all of the common contigs are equivalent
     */
    private static boolean commonContigsHaveSameLengths(Set<String> commonContigs, SAMSequenceDictionary dict1, SAMSequenceDictionary dict2) {
        return findDisequalCommonContigs(commonContigs, dict1, dict2) == null;
    }

    /**
     * Returns a List(x,y) that contains two disequal sequence records among the common contigs in both dicts.  Returns
     * null if all common contigs are equivalent
     *
     * @param commonContigs
     * @param dict1
     * @param dict2
     * @return
     */
    public static List<SAMSequenceRecord> findDisequalCommonContigs(Set<String> commonContigs, SAMSequenceDictionary dict1, SAMSequenceDictionary dict2) {
        for ( String name : commonContigs ) {
            SAMSequenceRecord elt1 = dict1.getSequence(name);
            SAMSequenceRecord elt2 = dict2.getSequence(name);
            if ( ! sequenceRecordsAreEquivalent(elt1, elt2) )
                return Arrays.asList(elt1,elt2);
        }

        return null;
    }

    /**
     * Helper routine that returns whether two sequence records are equivalent, defined as having the same name and
     * lengths.
     *
     * NOTE: we allow the lengths to differ if one or both are UNKNOWN_SEQUENCE_LENGTH
     *
     * @param first first sequence record to compare
     * @param second second sequence record to compare
     * @return true if first and second have the same names and lengths, otherwise false
     */
    public static boolean sequenceRecordsAreEquivalent(final SAMSequenceRecord first, final SAMSequenceRecord second) {
        if ( first == second ) {
            return true;
        }
        if ( first == null || second == null ) {
            return false;
        }
        final int length1 = first.getSequenceLength();
        final int length2 = second.getSequenceLength();

        if (length1 != length2 && length1 != SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH && length2 != SAMSequenceRecord.UNKNOWN_SEQUENCE_LENGTH){
            return false;
        }
        if (! first.getSequenceName().equals(second.getSequenceName())){
            return false;
        }
        return true;
    }

    /**
     * A very simple (and naive) algorithm to determine (1) if the dict is a human reference (hg18, hg19, b36, or b37) and if it's
     * lexicographically sorted.  Works by matching lengths of the static chr1, chr10, and chr2, and then if these
     * are all matched, requiring that the order be chr1, chr2, chr10.
     *
     * @param dict
     * @return
     */
    public static boolean nonCanonicalHumanContigOrder(SAMSequenceDictionary dict) {
        SAMSequenceRecord chr1 = null, chr2 = null, chr10 = null;
        for ( SAMSequenceRecord elt : dict.getSequences() ) {
            if ( isHumanSeqRecord(elt, CHR1_HG18, CHR1_HG19, CHR1_B36, CHR1_B37) ) chr1 = elt;
            if ( isHumanSeqRecord(elt, CHR2_HG18, CHR2_HG19, CHR2_B36, CHR2_B37) ) chr2 = elt;
            if ( isHumanSeqRecord(elt, CHR10_HG18, CHR10_HG19, CHR10_B36, CHR10_B37) ) chr10 = elt;
        }
        if ( chr1 != null  && chr2 != null && chr10 != null) {
            return ! ( chr1.getSequenceIndex() < chr2.getSequenceIndex() && chr2.getSequenceIndex() < chr10.getSequenceIndex() );
        }

        return false;
    }

    /**
     * Trivial helper that returns true if elt has the same name and length as rec1 or rec2
     * @param elt record to test
     * @param recs the list of records to check for name and length equivalence
     * @return true if elt has the same name and length as any of the recs
     */
    private static boolean isHumanSeqRecord(SAMSequenceRecord elt, SAMSequenceRecord... recs) {
        for (SAMSequenceRecord rec : recs) {
            if (elt.getSequenceLength() == rec.getSequenceLength() && elt.getSequenceName().equals(rec.getSequenceName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the common contigs in dict1 and dict2 are in the same relative order, without regard to
     * absolute index position. This is accomplished by getting the common contigs in both dictionaries, sorting
     * these according to their indices, and then walking through the sorted list to ensure that each ordered contig
     * is equivalent
     *
     * @param commonContigs names of the contigs common to both dictionaries
     * @param dict1 first SAMSequenceDictionary
     * @param dict2 second SAMSequenceDictionary
     * @return true if the common contigs occur in the same relative order in both dict1 and dict2, otherwise false
     */
    private static boolean commonContigsAreInSameRelativeOrder(Set<String> commonContigs, SAMSequenceDictionary dict1, SAMSequenceDictionary dict2) {
        final List<SAMSequenceRecord> list1 = getSequencesOfName(commonContigs, dict1);
        final List<SAMSequenceRecord> list2 = getSequencesOfName(commonContigs, dict2);
        list1.sort(SEQUENCE_INDEX_ORDER);
        list2.sort(SEQUENCE_INDEX_ORDER);

        for ( int i = 0; i < list1.size(); i++ ) {
            SAMSequenceRecord elt1 = list1.get(i);
            SAMSequenceRecord elt2 = list2.get(i);
            if ( ! elt1.getSequenceName().equals(elt2.getSequenceName()) )
                return false;
        }

        return true;
    }

    /**
     * Gets the subset of SAMSequenceRecords in commonContigs in dict
     *
     * @param commonContigs
     * @param dict
     * @return
     */
    private static List<SAMSequenceRecord> getSequencesOfName(Set<String> commonContigs, SAMSequenceDictionary dict) {
        List<SAMSequenceRecord> l = new ArrayList<>(commonContigs.size());
        for ( String name : commonContigs ) {
            l.add(dict.getSequence(name) );
        }

        return l;
    }

    /**
     * Checks whether the common contigs in the given sequence dictionaries occur at the same indices
     * in both dictionaries
     *
     * @param commonContigs Set of names of the contigs that occur in both dictionaries
     * @param dict1 first sequence dictionary
     * @param dict2 second sequence dictionary
     * @return true if the contigs common to dict1 and dict2 occur at the same indices in both dictionaries,
     *         otherwise false
     */
    private static boolean commonContigsAreAtSameIndices( final Set<String> commonContigs, final SAMSequenceDictionary dict1, final SAMSequenceDictionary dict2 ) {
        for ( String commonContig : commonContigs ) {
            SAMSequenceRecord dict1Record = dict1.getSequence(commonContig);
            SAMSequenceRecord dict2Record = dict2.getSequence(commonContig);

            // Each common contig must have the same index in both dictionaries
            if ( dict1Record.getSequenceIndex() != dict2Record.getSequenceIndex() ) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the set of contig names found in both dicts.
     * @param dict1
     * @param dict2
     * @return
     */
    public static Set<String> getCommonContigsByName(SAMSequenceDictionary dict1, SAMSequenceDictionary dict2) {
        Set<String> intersectingSequenceNames = getContigNames(dict1);
        intersectingSequenceNames.retainAll(getContigNames(dict2));
        return intersectingSequenceNames;
    }

    public static Set<String> getContigNames(SAMSequenceDictionary dict) {
        Set<String> contigNames = new LinkedHashSet<String>(dict.size());
        for (SAMSequenceRecord dictionaryEntry : dict.getSequences())
            contigNames.add(dictionaryEntry.getSequenceName());
        return contigNames;
    }

    public static List<String> getContigNamesList(final SAMSequenceDictionary refSeqDict) {
        ValidationUtils.nonNull(refSeqDict, "provided reference sequence ditionary is null");
        return refSeqDict.getSequences().stream().map(SAMSequenceRecord::getSequenceName).collect(Collectors.toList());
    }

    /**
     * Returns a compact String representation of the sequence dictionary it's passed
     *
     * The format of the returned String is:
     * [ contig1Name(length: contig1Length) contig2Name(length: contig2Length) ... ]
     *
     * @param dict a non-null SAMSequenceDictionary
     * @return A String containing all of the contig names and lengths from the sequence dictionary it's passed
     */
    public static String getDictionaryAsString( final SAMSequenceDictionary dict ) {
        ValidationUtils.nonNull(dict, "Sequence dictionary must be non-null");

        StringBuilder s = new StringBuilder("[ ");

        for ( SAMSequenceRecord dictionaryEntry : dict.getSequences() ) {
            s.append(dictionaryEntry.getSequenceName());
            s.append("(length:");
            s.append(dictionaryEntry.getSequenceLength());
            s.append(") ");
        }

        s.append("]");

        return s.toString();
    }

}
