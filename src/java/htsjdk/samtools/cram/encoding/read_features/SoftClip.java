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

public class SoftClip implements Serializable, ReadFeature {

    private int position;
    private byte[] sequence;

    public byte[] getSequence() {
        return sequence;
    }

    public void setSequence(byte[] sequence) {
        this.sequence = sequence;
    }

    public SoftClip() {
    }

    public SoftClip(int position, byte[] sequence) {
        this.position = position;
        this.sequence = sequence;
    }

    public static final byte operator = 'S';

    @Override
    public byte getOperator() {
        return operator;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SoftClip))
            return false;

        SoftClip v = (SoftClip) obj;

        if (position != v.position)
            return false;
        if (Arrays.equals(sequence, v.sequence))
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(getClass().getSimpleName() + "[");
        sb.append("position=").append(position);
        sb.append("; bases=").append(new String(sequence));
        sb.append("] ");
        return sb.toString();
    }
}
