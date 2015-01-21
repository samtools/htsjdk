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


public class BaseChange implements Serializable {
    private int change;

    public BaseChange(int change) {
        this.change = change;
    }

    public BaseChange(byte from, byte to) {
        change = toInt(from, to);
    }

    public byte getBaseForReference(byte refBase) {
        return BaseTransitions.getBaseForTransition(refBase, change);
    }

    public static int toInt(byte from, byte to) {
        return BaseTransitions.getBaseTransition(from, to);
    }

    public int getChange() {
        return change;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof BaseChange && ((BaseChange) obj).change == change)
            return true;
        return false;
    }
}
