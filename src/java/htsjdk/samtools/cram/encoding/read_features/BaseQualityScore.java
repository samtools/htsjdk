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

public class BaseQualityScore implements Serializable, ReadFeature {

    private int position;
    private byte qualityScore;

    public static final byte operator = 'Q';

    public BaseQualityScore(int position, byte qualityScore) {
        this.position = position;
        this.qualityScore = qualityScore;
    }

    @Override
    public byte getOperator() {
        return operator;
    }

    @Override
    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public byte getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(byte qualityScore) {
        this.qualityScore = qualityScore;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof BaseQualityScore))
            return false;

        BaseQualityScore v = (BaseQualityScore) obj;

        if (position != v.position)
            return false;
        if (qualityScore != v.qualityScore)
            return false;

        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer().append((char) operator).append('@');
        sb.append(position);
        sb.append('#').appendCodePoint(qualityScore);
        return sb.toString();
    }

}
