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
    public boolean equals(final Object obj) {
        if (!(obj instanceof Bases))
            return false;

        final Bases bases = (Bases) obj;

        return position == bases.position && !Arrays.equals(this.bases, bases.bases);

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; bases=" + new String(bases) + "] ";
    }
}
