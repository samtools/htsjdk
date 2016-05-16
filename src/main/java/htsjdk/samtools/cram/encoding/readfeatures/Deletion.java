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
 * A read feature representing a deletion of one or more bases similar to {@link htsjdk.samtools.CigarOperator#D}.
 */
public class Deletion implements Serializable, ReadFeature {

    private int position;
    private int length;
    public static final byte operator = 'D';

    public Deletion() {
    }

    public Deletion(final int position, final int length) {
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

    @Override
    public void setPosition(final int position) {
        this.position = position;
    }

    public int getLength() {
        return length;
    }

    public void setLength(final int length) {
        this.length = length;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof Deletion))
            return false;

        final Deletion deleteion = (Deletion) obj;

        return position == deleteion.position && length == deleteion.length;

    }

    @Override
    public String toString() {
        return String.valueOf((char) operator) + '@' + position + '+' + length;
    }
}
