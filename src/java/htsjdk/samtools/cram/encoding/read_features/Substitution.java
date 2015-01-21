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

public class Substitution implements Serializable, ReadFeature {

    private int position;
    private byte base = -1;
    private byte refernceBase = -1;
    private BaseChange baseChange;
    private byte code = -1;

    public byte getCode() {
        return code;
    }

    public void setCode(byte code) {
        this.code = code;
    }

    public static final byte operator = 'X';

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

    public byte getBase() {
        return base;
    }

    public void setBase(byte base) {
        this.base = base;
    }

    public byte getRefernceBase() {
        return refernceBase;
    }

    public void setRefernceBase(byte refernceBase) {
        this.refernceBase = refernceBase;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Substitution))
            return false;

        Substitution v = (Substitution) obj;

        if (position != v.position)
            return false;

        if (baseChange != null || v.baseChange != null) {
            if (baseChange != null) {
                if (!baseChange.equals(v.baseChange))
                    return false;
            } else if (!v.baseChange.equals(baseChange))
                return false;
        }

        if ((code != v.code) & (code == -1 || v.code == -1)) {
            return false;
        }

        if (code > -1 && v.code > -1) {
            if (refernceBase != v.refernceBase) return false;
            if (base != v.base) return false;
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer().append((char) operator).append('@');
        sb.append(position);
        if (baseChange != null)
            sb.append('/').append(baseChange.getChange());
        else {
            sb.append('\\').append((char) base);
            sb.append((char) refernceBase);
        }
        return sb.toString();
    }

    public BaseChange getBaseChange() {
        return baseChange;
    }

    public void setBaseChange(BaseChange baseChange) {
        this.baseChange = baseChange;
    }
}
