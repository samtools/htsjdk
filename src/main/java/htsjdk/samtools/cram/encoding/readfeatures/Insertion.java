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

/**
 * A read feature representing a multi-base insertion.
 */
public class Insertion implements Serializable, ReadFeature {

    private int position;
    private byte[] sequence;
    public static final byte operator = 'I';

    public Insertion() {
    }

    public Insertion(final int position, final byte[] sequence) {
        this.position = position;
        this.sequence = sequence;
    }

    @Override
    public byte getOperator() {
        return operator;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    public byte[] getSequence() {
        return sequence;
    }

    public void setSequence(final byte[] sequence) {
        this.sequence = sequence;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Insertion))
            return false;

        final Insertion insertion = (Insertion) obj;

        return position == insertion.position && Arrays.equals(sequence, insertion.sequence);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; sequence=" + new String(sequence) + "] ";
    }
}
