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

/**
 * A read feature representing a single quality score in a read.
 */
public class BaseQualityScore implements Serializable, ReadFeature {

    private int position;
    private byte qualityScore;

    public static final byte operator = 'Q';

    public BaseQualityScore(final int position, final byte qualityScore) {
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

    public void setPosition(final int position) {
        this.position = position;
    }

    public byte getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(final byte qualityScore) {
        this.qualityScore = qualityScore;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof BaseQualityScore))
            return false;

        final BaseQualityScore v = (BaseQualityScore) obj;

        return position == v.position && qualityScore == v.qualityScore;

    }

    @Override
    public String toString() {
        return new StringBuilder().append((char) operator).append('@')
                .append(position).append('#').appendCodePoint(qualityScore).toString();
    }

}
