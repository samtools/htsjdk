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
package htsjdk.samtools;

import htsjdk.samtools.util.Log;

import java.io.Serializable;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import static htsjdk.samtools.SAMSequenceRecord.*;
import static java.util.stream.Collectors.toList;

/**
 * Collection of SAMSequenceRecords.
 */
@XmlRootElement(name="References")
public class SAMSequenceDictionary implements Serializable {
    public static final long serialVersionUID = 1L;

    /* xml Serialization , for `m_sequence` we use the field instead of the
    getter because the later wraps the list into an unmodifiable List 
    see http://tech.joshuacummings.com/2010/10/problems-with-defensive-collection.html */
    @XmlElement(name="Reference")
    private List<SAMSequenceRecord> mSequences = new ArrayList<>();
    private final Map<String, SAMSequenceRecord> mSequenceMap = new HashMap<>();

    public SAMSequenceDictionary() {
    }

    public SAMSequenceDictionary(final List<SAMSequenceRecord> list) {
        this();
        setSequences(list);
    }

    @XmlTransient //we use the field instead of getter/setter
    public List<SAMSequenceRecord> getSequences() {
        return Collections.unmodifiableList(mSequences);
    }

    private static Log log = Log.getInstance(SAMSequenceDictionary.class);

    public SAMSequenceRecord getSequence(final String name) {
        return mSequenceMap.get(name);
    }

    /**
     * Replaces the existing list of SAMSequenceRecords with the given list.
     * Reset the aliases
     *
     * @param list This value is used directly, rather than being copied.
     */
    public void setSequences(final List<SAMSequenceRecord> list) {
        mSequences = list;
        mSequenceMap.clear();
        int index = 0;
        for (final SAMSequenceRecord record : list) {
            record.setSequenceIndex(index++);
            if (mSequenceMap.put(record.getSequenceName(), record) != null) {
                throw new IllegalArgumentException("Cannot add sequence that already exists in SAMSequenceDictionary: " +
                        record.getSequenceName());
            }
        }
    }

    public void addSequence(final SAMSequenceRecord sequenceRecord) {
        if (mSequenceMap.containsKey(sequenceRecord.getSequenceName())) {
            throw new IllegalArgumentException("Cannot add sequence that already exists in SAMSequenceDictionary: " +
                    sequenceRecord.getSequenceName());
        }
        sequenceRecord.setSequenceIndex(mSequences.size());
        mSequences.add(sequenceRecord);
        mSequenceMap.put(sequenceRecord.getSequenceName(), sequenceRecord);
    }

    /**
     * @return The SAMSequenceRecord with the given index, or null if index is out of range.
     */
    public SAMSequenceRecord getSequence(final int sequenceIndex) {
        if (sequenceIndex < 0 || sequenceIndex >= mSequences.size()) {
            return null;
        }
        return mSequences.get(sequenceIndex);
    }

    /**
     * @return The index for the given sequence name, or -1 if the name is not found.
     */
    public int getSequenceIndex(final String sequenceName) {
        final SAMSequenceRecord record = mSequenceMap.get(sequenceName);
        if (record == null) {
            return -1;
        }
        return record.getSequenceIndex();
    }

    /**
     * @return number of SAMSequenceRecord(s) in this dictionary
     */
    public int size() {
        return mSequences.size();
    }

    /**
     * @return The sum of the lengths of the sequences in this dictionary
     */
    public long getReferenceLength() {
        long len = 0L;
        for (final SAMSequenceRecord seq : getSequences()) {
            len += seq.getSequenceLength();
        }
        return len;
    }

    /**
     * @return true is the dictionary is empty
     */
    public boolean isEmpty() {
        return mSequences.isEmpty();
    }

    private static String DICT_MISMATCH_TEMPLATE = "SAM dictionaries are not the same: %s.";
    /**
     * Non-comprehensive {@link #equals(Object)}-assertion: instead of calling {@link SAMSequenceRecord#equals(Object)} on constituent
     * {@link SAMSequenceRecord}s in this dictionary against its pair in the target dictionary, in order, call
     * {@link SAMSequenceRecord#isSameSequence(SAMSequenceRecord)}.
     * Aliases are ignored.
     *
     * @throws AssertionError When the dictionaries are not the same, with some human-readable information as to why
     */
    public void assertSameDictionary(final SAMSequenceDictionary that) {
        if (this == that) return;

        final Iterator<SAMSequenceRecord> thatSequences = that.mSequences.iterator();
        for (final SAMSequenceRecord thisSequence : mSequences) {
            if (!thatSequences.hasNext()) {
                throw new AssertionError(String.format(DICT_MISMATCH_TEMPLATE, thisSequence + " is present in only one dictionary"));
            } else {
                final SAMSequenceRecord thatSequence = thatSequences.next();
                if(!thatSequence.isSameSequence(thisSequence)) {
                    throw new AssertionError(
                            String.format(DICT_MISMATCH_TEMPLATE, thatSequence + " was found when " + thisSequence + " was expected")
                    );
                }
            }
        }
        if (thatSequences.hasNext())
            throw new AssertionError(String.format(DICT_MISMATCH_TEMPLATE, thatSequences.next() + " is present in only one dictionary"));
    }

    /**
     * Non-comprehensive {@link #equals(Object)}-validation: instead of calling {@link SAMSequenceRecord#equals(Object)} on constituent
     * {@link SAMSequenceRecord}s in this dictionary against its pair in the target dictionary, in order, call
     * {@link SAMSequenceRecord#isSameSequence(SAMSequenceRecord)}.
     *
     * @param that {@link SAMSequenceDictionary} to compare against
     * @return true if the dictionaries are the same, false otherwise
     *
     */
    public boolean isSameDictionary(final SAMSequenceDictionary that) {
        if (that == null || that.mSequences == null) return false;
        if (this == that) return true;

        final Iterator<SAMSequenceRecord> thatSequences = that.mSequences.iterator();
        for (final SAMSequenceRecord thisSequence : mSequences) {
            if (!thatSequences.hasNext()) {
                return false;
            } else {
                final SAMSequenceRecord thatSequence = thatSequences.next();
                if (!thatSequence.isSameSequence(thisSequence)) {
                    return false;
                }
            }
        }

        return !thatSequences.hasNext();
    }

    /** returns true if the two dictionaries are the same, aliases are NOT considered */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SAMSequenceDictionary that = (SAMSequenceDictionary) o;

       return mSequences.equals(that.mSequences);
    }

    /**
     * Add an alias to a SAMSequenceRecord. This can be use to provide some
     * alternate names fo a given contig. e.g:
     * <code>1,chr1,chr01,01,CM000663,NC_000001.10</code> e.g:
     * <code>MT,chrM</code>
     *
     * @param originalName
     *            existing contig name
     * @param altName
     *            new contig name
     * @return the contig associated to the 'originalName/altName'
     */
    public SAMSequenceRecord addSequenceAlias(final String originalName,
            final String altName) {
        if (originalName == null) throw new IllegalArgumentException("original name cannot be null");
        if (altName == null) throw new IllegalArgumentException("alt name cannot be null");
        final SAMSequenceRecord originalSeqRecord = getSequence(originalName);
        if (originalSeqRecord == null) throw new IllegalArgumentException("Sequence " + originalName + " doesn't exist in dictionary.");
        // same name, nothing to do
        if (originalName.equals(altName)) return originalSeqRecord;
        final SAMSequenceRecord altSeqRecord = getSequence(altName);
        if (altSeqRecord != null) {
            // alias was already set to the same record
            if (altSeqRecord.equals(originalSeqRecord)) return originalSeqRecord;
            // alias was already set to another record
            throw new IllegalArgumentException("Alias " + altName +
                    " was already set to " + altSeqRecord.getSequenceName());
        }
        mSequenceMap.put(altName, originalSeqRecord);
        return originalSeqRecord;
    }

    /**
     * return a MD5 sum for ths dictionary, the checksum is re-computed each
     * time this method is called.
     *
     * <pre>
     * md5( (seq1.md5_if_available) + ' '+(seq2.name+seq2.length) + ' '+...)
     * </pre>
     *
     * @return a MD5 checksum for this dictionary or the empty string if it is
     *         empty
     */
    public String md5() {
        if (isEmpty())
            return "";
        try {
            final MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.reset();
            for (final SAMSequenceRecord samSequenceRecord : mSequences) {
                if (samSequenceRecord.getSequenceIndex() > 0)
                    md5.update((byte) ' ');
                final String md5_tag = samSequenceRecord.getAttribute(SAMSequenceRecord.MD5_TAG);
                if (md5_tag != null) {
                    md5.update(md5_tag.getBytes());
                } else {
                    md5.update(samSequenceRecord.getSequenceName().getBytes());
                    md5.update(String.valueOf(samSequenceRecord.getSequenceLength()).getBytes());
                }
            }
            String hash = new BigInteger(1, md5.digest()).toString(16);
            if (hash.length() != 32) {
                final String zeros = "00000000000000000000000000000000";
                hash = zeros.substring(0, 32 - hash.length()) + hash;
            }
            return hash;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return mSequences.hashCode();
    }

    @Override
    public String toString() {
        return "SAMSequenceDictionary:( sequences:"+ size()+
                " length:"+ getReferenceLength()+" "+
                " md5:"+md5()+")";
    }

    public static final List<String> DEFAULT_DICTIONARY_EQUAL_TAG = Arrays.asList(
            SAMSequenceRecord.MD5_TAG,
            SAMSequenceRecord.SEQUENCE_LENGTH_TAG);

    /**
     * Will merge dictionaryTags from two dictionaries into one focusing on merging the tags rather than the sequences.
     *
     * Requires that dictionaries have the same SAMSequence records in the same order.
     * For each sequenceIndex, the union of the tags from both sequences will be added to the new sequence, mismatching
     * values (for tags that are in both) will generate a warning, and the value from dict1 will be used.
     * For tags that are in tagsToEquate an unequal value will generate an error (an IllegalArgumentException will
     * be thrown.) tagsToEquate must include LN and MD.
     *
     * @param dict1 first dictionary
     * @param dict2 first dictionary
     * @param tagsToMatch list of tags that must be equal if present in both sequence. Must contain MD, and LN
     * @return dictionary consisting of the same sequences as the two inputs with the merged values of tags.
     */
    static public SAMSequenceDictionary mergeDictionaries(final SAMSequenceDictionary dict1,
                                                          final SAMSequenceDictionary dict2,
                                                          final List<String> tagsToMatch) {

        // We require MD and LN to match.
        if (!tagsToMatch.contains(MD5_TAG) || !tagsToMatch.contains(SEQUENCE_LENGTH_TAG)) {
            throw new IllegalArgumentException("Both " + MD5_TAG + " and " + SEQUENCE_LENGTH_TAG + " must be matched " +
                    "when merging dictionaries. Found: " + String.join(",", tagsToMatch));
        }

        if (!dict1.getSequences().stream().map(SAMSequenceRecord::getSequenceName).collect(Collectors.toList()).equals(
                dict2.getSequences().stream().map(SAMSequenceRecord::getSequenceName).collect(Collectors.toList()))) {

            throw new IllegalArgumentException(String.format("Do not use this function to merge dictionaries with " +
                            "different sequences in them. Sequences must be in the same order as well. Found [%s] and [%s].",
                    String.join(", ", dict1.getSequences().stream().map(SAMSequenceRecord::getSequenceName).collect(toList())),
                    String.join(", ", dict2.getSequences().stream().map(SAMSequenceRecord::getSequenceName).collect(toList()))));
        }

        final SAMSequenceDictionary finalDict = new SAMSequenceDictionary();
        for (int sequenceIndex = 0; sequenceIndex < dict1.getSequences().size(); sequenceIndex++) {
            final SAMSequenceRecord s1 = dict1.getSequence(sequenceIndex);
            final SAMSequenceRecord s2 = dict2.getSequence(sequenceIndex);

            final String sName = s1.getSequenceName();
            final SAMSequenceRecord sMerged = new SAMSequenceRecord(sName, UNKNOWN_SEQUENCE_LENGTH);
            finalDict.addSequence(sMerged);

            final Set<String> allTags = new HashSet<>();
            s1.getAttributes().forEach(a -> allTags.add(a.getKey()));
            s2.getAttributes().forEach(a -> allTags.add(a.getKey()));

            for (final String tag : allTags) {
                final String value1 = s1.getAttribute(tag);
                final String value2 = s2.getAttribute(tag);

                if (value1 != null && value2 != null && !value1.equals(value2)) {
                    String baseMessage = String.format("Found sequence entry for which " +
                                    "tags differ: %s and tag %s has the two values: %s and %s.",
                            sName, tag, value1, value2);

                    if (tagsToMatch.contains(tag)) {
                        log.error("Cannot merge dictionaries. ", baseMessage);
                        throw new IllegalArgumentException("Cannot merge dictionaries. " + baseMessage);
                    } else {
                        log.warn(baseMessage, " Using ", value1);
                    }
                }
                sMerged.setAttribute(tag, value1 == null ? value2 : value1);
            }

            final int length1 = s1.getSequenceLength();
            final int length2 = s2.getSequenceLength();

            if (length1 != UNKNOWN_SEQUENCE_LENGTH && length2 != UNKNOWN_SEQUENCE_LENGTH && length1 != length2) {
                throw new IllegalArgumentException(String.format("Cannot merge the two dictionaries. " +
                        "Found sequence entry for which " + "lengths differ: %s has lengths %s and %s", sName, length1, length2));
            }
            sMerged.setSequenceLength(length1 == UNKNOWN_SEQUENCE_LENGTH ? length2 : length1);
        }
        return finalDict;
    }
}

