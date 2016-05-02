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
 * A read feature representing a single insert base.
 */
public class InsertBase implements Serializable, ReadFeature {

    private int position;
    private byte base;
    public static final byte operator = 'i';

    public InsertBase() {
    }

    public InsertBase(final int position, final byte base) {
        this.position = position;
        this.base = base;
    }


    @Override
    public byte getOperator() {
        return operator;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(final int position) {
        this.position = position;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof InsertBase))
            return false;

        final InsertBase insertBase = (InsertBase) obj;

        return position == insertBase.position && base == insertBase.base;

    }

    @Override
    public String toString() {
        return new StringBuilder().append((char) operator).append('@')
                .append(position).append('\\').appendCodePoint(base).toString();
    }

    public byte getBase() {
        return base;
    }

    public void setBase(final byte base) {
        this.base = base;
    }
}
