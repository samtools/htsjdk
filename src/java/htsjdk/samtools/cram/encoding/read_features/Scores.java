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

public class Scores implements Serializable, ReadFeature {

    private int position;
    private byte[] scores;

    public byte[] getScores() {
        return scores;
    }

    public void setScores(byte[] scores) {
        this.scores = scores;
    }

    public Scores() {
    }

    public Scores(int position, byte[] sequence) {
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
    public void setPosition(int position) {
        this.position = position;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Scores))
            return false;

        Scores v = (Scores) obj;

        if (position != v.position)
            return false;
        if (Arrays.equals(scores, v.scores))
            return false;

        return true;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + "position=" + position + "; scores=" + new String(scores) + "] ";
    }
}
