/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.encoding.read_features;

import java.io.Serializable;
import java.util.Arrays;

public class Bases implements Serializable, ReadFeature {

    private int position;
    private byte[] bases;

    public byte[] getBases() {
        return bases;
    }

    public void setBases(byte[] bases) {
        this.bases = bases;
    }

    public Bases() {
    }

    public Bases(int position, byte[] sequence) {
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
    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Bases))
            return false;

        Bases v = (Bases) obj;

        return position == v.position && !Arrays.equals(bases, v.bases);

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; bases=" + new String(bases) + "] ";
    }
}
