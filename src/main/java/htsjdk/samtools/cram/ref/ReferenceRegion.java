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

import java.util.Arrays;

/**
 * A class representing a region on a reference sequence.
 */
class ReferenceRegion {
    private final int index;
    private final String name;
    private long alignmentStart;
    private int arrayStart;
    private final byte[] array;

    /**
     * Construct reference sequence region with the given bases.
     * @param bases the bases for the sequence
     * @param sequenceIndex index in the {@link htsjdk.samtools.SAMSequenceDictionary}
     * @param sequenceName name of the reference sequence
     * @param alignmentStart 1-based inclusive position of the region start on the reference sequence
     * @param alignmentEnd   1-based inclusive position of the region end on the reference sequence
     */
    public ReferenceRegion(final byte[] bases, final int sequenceIndex,
                           final String sequenceName, final long alignmentStart, long alignmentEnd) {
        this.array = bases;
        this.index = sequenceIndex;
        this.name = sequenceName;

        if (alignmentEnd == -1)
            alignmentEnd = bases.length;

        if (alignmentStart < 1 || alignmentEnd < alignmentStart
                || alignmentEnd - alignmentStart > bases.length
                || alignmentEnd - 1 > bases.length)
            throw new IllegalArgumentException(String.format(
                    "Invalid reference region: %s, %d, %d.", sequenceName,
                    alignmentStart, alignmentEnd));

        this.alignmentStart = alignmentStart;

        this.arrayStart = (int) (alignmentStart - 1);
    }

    int arrayPosition(final long alignmentPosition) {
        final int arrayPosition = (int) (arrayStart + (alignmentPosition - alignmentStart));

        if (arrayPosition < 0 || arrayPosition > array.length)
            throw new IllegalArgumentException(
                    "The alignment position is out of the region: "
                            + alignmentPosition);

        return arrayPosition;
    }

    public byte base(final long alignmentPosition) {
        return array[arrayPosition(alignmentPosition)];
    }

    public byte[] copy(final long alignmentStart, final int alignmentSpan) {
        final int from = arrayPosition(alignmentStart);
        final int to = arrayPosition(alignmentStart + alignmentSpan);
        return Arrays.copyOfRange(array, from, to);
    }

    public int getIndex() {
        return index;
    }

    public String getName() {
        return name;
    }

    public long getAlignmentStart() {
        return alignmentStart;
    }
}
