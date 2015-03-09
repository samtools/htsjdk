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
 * A read feature representing a contiguous stretch of quality scores in a read.
 */
public class Scores implements Serializable, ReadFeature {

    private int position;
    private byte[] scores;

    public byte[] getScores() {
        return scores;
    }

    public void setScores(final byte[] scores) {
        this.scores = scores;
    }

    public Scores() {
    }

    public Scores(final int position, final byte[] sequence) {
        this.position = position;
        this.scores = sequence;
    }

    public static final byte operator = 'q';

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
        if (!(obj instanceof Scores))
            return false;

        final Scores scores = (Scores) obj;

        return position == scores.position && !Arrays.equals(this.scores, scores.scores);

    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; scores=" + new String(scores) + "] ";
    }
}
