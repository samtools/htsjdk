/*
 * The MIT License
 *
 * Copyright (c) 2016 The Broad Institute
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

import htsjdk.samtools.SAMSequenceRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The unit of iteration for AbstractLocusIterator.
 * Holds information about the locus (the SAMSequenceRecord and 1-based position on the reference),
 * plus list of AbstractRecordAndOffset objects,
 * If <code>RecordAndOffset</code> class is used, one object represents one aligned read that overlaps the locus.
 * If <code>TypedRecordAndOffset</code> class is used, one object represents one aligned read,
 * that starts or ends at the locus.
 *
 * @author Darina_Nikolaeva@epam.com, EPAM Systems, Inc. <www.epam.com>
 *
 */
public class AbstractLocusInfo<E extends AbstractRecordAndOffset> implements Locus {
    /**
     * Reference sequence, to which the reads are aligned.
     **/
    private final SAMSequenceRecord referenceSequence;
    /**
     * Position in the sequence, to which the reads are aligned.
     **/
    private final int position;

    /**
     * Initial size for the list of <code>AbstractRecordAndOffset</code> objects
     **/
    private final static int INITIAL_LIST_SIZE = 100;

    /**
     * List of aligned to current position reads
     **/
    private final List<E> recordAndOffsets = new ArrayList<>(INITIAL_LIST_SIZE);

    /**
     * @param referenceSequence reference sequence to which the reads are aligned
     * @param position          position in the sequence to which the reads are aligned
     */
    public AbstractLocusInfo(final SAMSequenceRecord referenceSequence, final int position) {
        this.referenceSequence = referenceSequence;
        this.position = position;
    }

    /**
     * Accumulates info for one read aligned to the locus. Method doesn't check, that <code>recordAndOffset</code>
     * is really aligned to current reference position, so it must have valid reference sequence and
     * position or further processing can go wrong.
     *
     * @param recordAndOffset object to add to current locus
     */
    public void add(E recordAndOffset) {
        recordAndOffsets.add(recordAndOffset);
    }

    /**
     * @return the index of reference sequence
     */
    public int getSequenceIndex() {
        return referenceSequence.getSequenceIndex();
    }

    /**
     * @return 1-based reference position
     */
    public int getPosition() {
        return position;
    }

    /**
     * @deprecated since name of the method can be confusing, new implementation should be used
     *          {@code getRecordAndOffsets()}
     * @return unmodifiable list of aligned to the reference position <code>recordsAndOffsets</code>
     */
    @Deprecated
    public List<E> getRecordAndPositions() {
        return Collections.unmodifiableList(recordAndOffsets);
    }

    /**
     * @return unmodifiable list of aligned to the reference position <code>recordsAndOffsets</code>
     */
    public List<E> getRecordAndOffsets() {
        return Collections.unmodifiableList(recordAndOffsets);
    }

    /**
     * @return the name of reference sequence
     */
    public String getSequenceName() {
        return referenceSequence.getSequenceName();
    }

    @Override
    public String toString() {
        return referenceSequence.getSequenceName() + ":" + position;
    }

    /**
     * @return the length of reference sequence
     */
    public int getSequenceLength() {
        return referenceSequence.getSequenceLength();
    }

    /** 
     * @return the number of records overlapping the position
     */
    public int size() { 
        return this.recordAndOffsets.size(); 
    }

    /**
     * @return <code>true</code> if RecordAndOffset list is empty;
     */
    public boolean isEmpty() {
        return getRecordAndOffsets().isEmpty();
    }
}
