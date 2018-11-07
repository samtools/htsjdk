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

public class Bases implements Serializable, ReadFeature {

    private int position;
    private byte[] bases;

    public byte[] getBases() {
        return bases;
    }

    public void setBases(final byte[] bases) {
        this.bases = bases;
    }

    public Bases() {
    }

    public Bases(final int position, final byte[] sequence) {
        this.position = position;
        this.bases = sequence;
    }

    public static final byte operator = 'b';

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
        return getClass().getSimpleName() + "[" + "position=" + position + "; bases=" + new String(bases) + "] ";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bases bases1 = (Bases) o;
        return position == bases1.position &&
                Arrays.equals(bases, bases1.bases);
    }

    @Override
    public int hashCode() {

        int result = Objects.hash(position);
        result = 31 * result + Arrays.hashCode(bases);
        return result;
    }
}
