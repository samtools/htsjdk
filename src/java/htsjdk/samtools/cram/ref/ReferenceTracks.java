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

public class ReferenceTracks {
    public static final int DEFAULT_WINDOW_SIZE = 1000000;
    private final int sequenceId;
    private final String sequenceName;
    private final byte[] reference;

    private int position;

    // a copy of ref bases for the given range:
    private final byte[] bases;
    private final short[] coverage;
    private final short[] mismatches;


    public ReferenceTracks(final int sequenceId, final String sequenceName,
                           final byte[] reference) {
        this(sequenceId, sequenceName, reference, DEFAULT_WINDOW_SIZE);
    }

    private ReferenceTracks(final int sequenceId, final String sequenceName,
                            final byte[] reference, final int windowSize) {
        this.sequenceId = sequenceId;
        this.sequenceName = sequenceName;
        this.reference = reference;

        bases = new byte[Math.min(windowSize, reference.length)];
        coverage = new short[Math.min(windowSize, reference.length)];
        mismatches = new short[Math.min(windowSize, reference.length)];
        position = 1;

        reset();
    }

    public int getSequenceId() {
        return sequenceId;
    }

    public String getSequenceName() {
        return sequenceName;
    }

    public int getWindowPosition() {
        return position;
    }

    public int getWindowLength() {
        return bases.length;
    }

    public int getReferenceLength() {
        return reference.length;
    }

    public void ensure(final int start, final int end) {
        if (end - start > bases.length)
            throw new RuntimeException("Window is too small for start " + start
                    + " end " + end);
        if (position < start)
            moveForwardTo(start);
    }

    /**
     * Shift the window forward to a new position on the reference.
     *
     * @param newPos 1-based reference coordinate, must be greater than current
     *               position and smaller than reference length.
     */
    void moveForwardTo(int newPos) {
        if (newPos - 1 >= reference.length)
            throw new RuntimeException("New position is beyond the reference: "
                    + newPos);

        if (newPos < position)
            throw new RuntimeException(
                    "Cannot shift to smaller position on the reference.");

        if (newPos > reference.length - bases.length + 1)
            newPos = reference.length - bases.length + 1;

        if (newPos == position)
            return;

        System.arraycopy(reference, newPos - 1, bases, 0,
                Math.min(bases.length, reference.length - newPos + 1));

        if (newPos > position && position + bases.length - newPos > 0) {
            for (int i = 0; i < coverage.length; i++) {
                if (i + newPos - position < coverage.length) {
                    coverage[i] = coverage[i + newPos - position];
                    mismatches[i] = mismatches[i + newPos - position];
                } else {
                    coverage[i] = 0;
                    mismatches[i] = 0;
                }
            }
        } else {
            Arrays.fill(coverage, (short) 0);
            Arrays.fill(mismatches, (short) 0);
        }

        this.position = newPos;
    }

    void reset() {
        System.arraycopy(reference, position - 1, bases, 0,
                Math.min(bases.length, reference.length - position + 1));
        Arrays.fill(coverage, (short) 0);
        Arrays.fill(mismatches, (short) 0);
    }

    public void ensureRange(final int start, final int length) {
        if (start < position)
            throw new RuntimeException("Cannot move the window backwards: "
                    + start);

        if (start > position || start + length > position + bases.length)
            moveForwardTo(start);
    }

    public final byte baseAt(final int pos) {
        if (pos - this.position < coverage.length)
            return bases[pos - this.position];
        else
            return 'N';
    }

    public final short coverageAt(final int pos) {
        if (pos - this.position >= coverage.length)
            return 0;
        else {
            return coverage[pos - this.position];
        }
    }

    public final short mismatchesAt(final int pos) {
        if (pos - this.position >= coverage.length)
            return 0;
        else
            return mismatches[pos - this.position];
    }

    public final void addCoverage(final int pos, final int amount) {
        if (pos - this.position < coverage.length)
            coverage[pos - this.position] += amount;
    }

    public final void addMismatches(final int pos, final int amount) {
        if (pos - this.position < coverage.length)
            mismatches[pos - this.position] += amount;
    }
}
