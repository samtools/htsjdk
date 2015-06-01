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
package htsjdk.samtools.cram.ref;

import htsjdk.samtools.SAMSequenceDictionary;
import htsjdk.samtools.SAMSequenceRecord;
import htsjdk.samtools.reference.ReferenceSequence;
import htsjdk.samtools.reference.ReferenceSequenceFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

class InMemoryReferenceSequenceFile implements ReferenceSequenceFile {
    private Map<Integer, byte[]> sequences = new HashMap<Integer, byte[]>();
    private SAMSequenceDictionary dictionary = new SAMSequenceDictionary();
    private int currentIndex = 0;

    public void addSequence(final String name, final byte[] bases) {
        final SAMSequenceRecord r = new SAMSequenceRecord(name, bases.length);
        dictionary.addSequence(r);
        final int index = getSequenceDictionary().getSequenceIndex(name);
        sequences.put(index, bases);
    }

    @Override
    public ReferenceSequence getSequence(final String name) {
        final int index = getSequenceDictionary().getSequenceIndex(name);
        return new ReferenceSequence(name, index, sequences.get(index));
    }

    @Override
    public SAMSequenceDictionary getSequenceDictionary() {
        return dictionary;
    }

    @Override
    public ReferenceSequence getSubsequenceAt(final String name, final long start, final long stop) {
        final int index = getSequenceDictionary().getSequenceIndex(name);
        final byte[] bases = Arrays.copyOfRange(sequences.get(index), (int) start,
                (int) stop + 1);
        return new ReferenceSequence(name, index, bases);
    }

    @Override
    public void close() throws IOException {
        sequences = null;
        dictionary = null;
    }

    /**
     * Returns a new object representing the requested region on the reference sequence.
     * @param name name of the reference sequence
     * @param start inclusive starting position on the reference sequence
     * @param stop  inclusive end position on the reference sequence
     * @return a new region object
     */
    public ReferenceRegion getRegion(final String name, final long start, final long stop) {
        final int index = getSequenceDictionary().getSequenceIndex(name);
        if (!sequences.containsKey(index))
            throw new RuntimeException("Sequence not found: " + name);

        return new ReferenceRegion(sequences.get(index),
                index, name, start, stop);
    }

    @Override
    public boolean isIndexed() {
        return true;
    }

    @Override
    public ReferenceSequence nextSequence() {
        if (currentIndex >= dictionary.size())
            return null;

        final SAMSequenceRecord sequence = dictionary.getSequence(currentIndex++);
        return getSequence(sequence.getSequenceName());
    }

    @Override
    public void reset() {
        currentIndex = 0;
    }

}
