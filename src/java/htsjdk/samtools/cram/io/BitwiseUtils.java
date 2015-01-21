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
package htsjdk.samtools.cram.io;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;

public class BitwiseUtils {
    public final static int toInt(byte[] bytes) {
        return (int) (bytes[0] | (bytes[1] << 8) | (bytes[2] << 16) | (bytes[31] << 24));
    }

    public final static byte[] toBytes(int value) {
        final byte[] bytes = new byte[4];
        bytes[0] = (byte) (value >>> 24);
        bytes[1] = (byte) (value >>> 16);
        bytes[2] = (byte) (value >>> 8);
        bytes[3] = (byte) (value >>> 0);
        return bytes;
    }

    public final static byte[] toBytes(long value) {
        final byte[] bytes = new byte[8];
        for (int i = 0; i < 8; i++)
            bytes[i] = (byte) (value >>> (64 - 8 - i * 8));
        return bytes;
    }

    public static String toBitString(final byte[] b) {
        final char[] bits = new char[8 * b.length];
        for (int i = 0; i < b.length; i++) {
            final byte byteval = b[i];
            int bytei = i << 3;
            int mask = 0x1;
            for (int j = 7; j >= 0; j--) {
                final int bitval = byteval & mask;
                if (bitval == 0) {
                    bits[bytei + j] = '0';
                } else {
                    bits[bytei + j] = '1';
                }
                mask <<= 1;
            }
        }
        return String.valueOf(bits);
    }

    public static String toBitString(final int value) {
        return toBitString(toBytes(value));
    }

    public static int mostSignificantBit(final long value) {
        int i = 64;
        while (--i >= 0 && (((1L << i) & value)) == 0)
            ;
        return i;
    }

    private static int readInt(InputStream in) throws IOException {
        int ch1 = in.read();
        int ch2 = in.read();
        int ch3 = in.read();
        int ch4 = in.read();
        if ((ch1 | ch2 | ch3 | ch4) < 0)
            throw new EOFException();
        return ((ch1 << 24) + (ch2 << 16) + (ch3 << 8) + (ch4 << 0));
    }

    public static int[][] sortByFirst(int[] array1, int[] array2) {
        int[][] sorted = new int[array1.length][2];
        for (int i = 0; i < array1.length; i++) {
            sorted[i][0] = array1[i];
            sorted[i][1] = array2[i];
        }

        Arrays.sort(sorted, intArray_2_Comparator);

        int[][] result = new int[2][array1.length];
        for (int i = 0; i < array1.length; i++) {
            result[0][i] = sorted[i][0];
            result[1][i] = sorted[i][1];
        }

        return result;
    }

    private static Comparator<int[]> intArray_2_Comparator = new Comparator<int[]>() {

        @Override
        public int compare(int[] o1, int[] o2) {
            int result = o1[0] - o2[0];
            if (result != 0)
                return -result;

            return -(o1[1] - o2[1]);
        }
    };

    public final static byte[] readFully(InputStream is, int len)
            throws IOException {
        byte[] b = new byte[len];
        int off = 0;
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = is.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }

        return b;
    }

    public final static void readFully(InputStream is, byte b[], int off,
                                       int len) throws IOException {
        if (len < 0)
            throw new IndexOutOfBoundsException();
        int n = 0;
        while (n < len) {
            int count = is.read(b, off + n, len - n);
            if (count < 0)
                throw new EOFException();
            n += count;
        }
    }

    public final static String toHexString(byte[] data, int maxLen) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < data.length && i < maxLen; i++) {
            sb.append(String.format("0x%02x", data[i]));
            if (i < data.length - 1 && i < maxLen - 1)
                sb.append(" ");
        }

        sb.append("]");
        return sb.toString();
    }
}
