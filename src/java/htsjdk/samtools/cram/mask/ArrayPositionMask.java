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
package htsjdk.samtools.cram.mask;

import java.util.Arrays;

public class ArrayPositionMask implements PositionMask {
    private int[] array;

    public ArrayPositionMask(int[] array) {
        if (array == null)
            throw new NullPointerException("Expecting a non-null array.");
        this.array = array;
        Arrays.sort(array);
    }

    @Override
    public boolean isMasked(int position) {
        return Arrays.binarySearch(array, position) > -1;
    }

    @Override
    public int[] getMaskedPositions() {
        return array;
    }

    @Override
    public boolean isEmpty() {
        return array.length == 0;
    }

    @Override
    public int getMaskedCount() {
        return array.length;
    }

    @Override
    public int getMinMaskedPosition() {
        return array.length > 0 ? array[0] : -1;
    }

    @Override
    public int getMaxMaskedPosition() {
        return array.length > 0 ? array[array.length - 1] : -1;
    }

    public static final PositionMask EMPTY_INSTANCE = new ArrayPositionMask(new int[]{});

    @Override
    public byte[] toByteArrayUsing(byte mask, byte nonMask) {
        byte[] ba = new byte[array[array.length - 1]];
        Arrays.fill(ba, nonMask);
        for (int pos : array)
            ba[pos - 1] = mask;

        return ba;
    }
}
