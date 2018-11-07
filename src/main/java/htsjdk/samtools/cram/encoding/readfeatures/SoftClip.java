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
package htsjdk.samtools.cram.encoding.readfeatures;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

/**
 * A read feature representing a soft clip similar to {@link htsjdk.samtools.CigarOperator#S}.
 */
public class SoftClip implements Serializable, ReadFeature {

    private int position;
    private byte[] sequence;

    public byte[] getSequence() {
        return sequence;
    }

    public void setSequence(final byte[] sequence) {
        this.sequence = sequence;
    }

    public SoftClip() {
    }

    public SoftClip(final int position, final byte[] sequence) {
        this.position = position;
        this.sequence = sequence;
    }

    public static final byte operator = 'S';

    @Override
    public byte getOperator() {
        return operator;
    }

    @Override
    public int getPosition() {
        return position;
    }

    @Override
    public void setPosition(final int position) {
        this.position = position;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; bases=" + new String(sequence) + "] ";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoftClip softClip = (SoftClip) o;
        return position == softClip.position &&
                Arrays.equals(sequence, softClip.sequence);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(position);
        result = 31 * result + Arrays.hashCode(sequence);
        return result;
    }
}
