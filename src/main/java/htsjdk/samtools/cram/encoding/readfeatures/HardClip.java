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
import java.util.Objects;

/**
 * A read feature representing a hard clip similar to {@link htsjdk.samtools.CigarOperator#H}.
 */
public class HardClip implements Serializable, ReadFeature {
    public static final byte operator = 'H';

    private int position;
    private int length;

    public HardClip(final int position, final int length) {
        this.position = position;
        this.length = length;
    }

    @Override
    public byte getOperator() {
        return operator;
    }

    @Override
    public int getPosition() {
        return position;
    }

    public int getLength() {
        return length;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; length=" + length + "] ";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HardClip hardClip = (HardClip) o;
        return position == hardClip.position &&
                length == hardClip.length;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, length);
    }
}
