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

public class HardClip implements Serializable, ReadFeature {
    public static final byte operator = 'H';

    private int position;
    private int length;

    public HardClip() {
    }

    public HardClip(int position, int len) {
        this.position = position;
        this.length = len;
    }

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

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof HardClip))
            return false;

        HardClip v = (HardClip) obj;

        if (position != v.position)
            return false;
        if (length != v.length)
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer(getClass().getSimpleName() + "[");
        sb.append("position=").append(position);
        sb.append("; len=").append(length);
        sb.append("] ");
        return sb.toString();
    }
}
