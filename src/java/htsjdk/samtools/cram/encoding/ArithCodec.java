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
package htsjdk.samtools.cram.encoding;

import htsjdk.samtools.cram.common.NullOutputStream;
import htsjdk.samtools.cram.io.BitInputStream;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class ArithCodec extends AbstractBitCodec<byte[]> {
    private byte curBit = 0;
    private int curByte = 0;
    private double min = 0;
    private double max = 1;
    private double localMin = 0;
    private double localMax = 1;

    private final int TERMINATOR = 256;
    private double[] probs;
    private int[] map, rev_map;
    private long bitCount;

    private ByteArrayOutputStream baos;
    private ArrayList<Integer> fileData;

    public ArithCodec(int[] freqs, int[] map) {
        // build expanded map ------------------------------
        this.map = new int[257]; // ASCII + end character
        Arrays.fill(this.map, -1);
        for (int i = 0; i < map.length; i++)
            this.map[map[i]] = i;
        this.map[this.TERMINATOR] = map.length;

        // copy collapsed map, plus end character ----------
        this.rev_map = new int[map.length + 1];
        System.arraycopy(map, 0, this.rev_map, 0, map.length);
        this.rev_map[map.length] = this.TERMINATOR;

        // build probability table from frequency count ----
        this.probs = new double[freqs.length + 1];
        int total = 0, endCharCount = 0;
        for (int i = 0; i < freqs.length; i++)
            total += freqs[i];
        endCharCount = (total / 100) > 0 ? (total / 100) : (total / 10);
        total += endCharCount;
        int t = 0;
        for (int i = 0; i < freqs.length; i++) {
            t += freqs[i];
            this.probs[i] = (double) t / (double) total;
        }
        this.probs[this.probs.length - 1] = 1.0;

        // initialize byte stream --------------------------
        this.baos = new ByteArrayOutputStream(2 * 215000000);
        this.fileData = new ArrayList();
    }

    /*
     * Reading and expanding a bit stream based on given frequency count
     */
    @Override
    public byte[] read(BitInputStream bis) throws IOException {
        this.baos.reset();
        this.fileData.clear();
        curBit = 0;
        curByte = 0;
        min = 0;
        max = 1;
        localMin = 0;
        localMax = 1;

        int read = decodeCharacter(bis);
        while (read != this.map[this.TERMINATOR]) {
            this.baos.write(this.rev_map[read]);
            read = decodeCharacter(bis);
        }

        return this.baos.toByteArray();
    }

    public int decodeCharacter(BitInputStream bis) throws IOException {
        double tempMin = min;
        double tempMax = max;
        byte tempBit = curBit;
        int tempByte = curByte;
        int val = 0;
        if (this.fileData.isEmpty())
            fileData.add(bis.readBits(8));
        while (true) {
            double cur = (min + max) / 2.0;
            val = -1;
            for (int i = 0; i < probs.length; i++) {
                if (probs[i] > min) {
                    if (probs[i] > max)
                        val = i;
                    break;
                }
            }
            if (val == -1) {
                boolean bit = false;
                if ((fileData.get(curByte) & (128 >> curBit)) != 0)
                    bit = true;
                if (bit)
                    min = cur;
                else
                    max = cur;
                curBit++;
                if (curBit == 8) {
                    curBit = 0;
                    curByte++;
                    if (curByte > fileData.size() - 1) {
                        try {
                            fileData.add(bis.readBits(8));
                        } catch (Throwable t) {
                            fileData.add(0);
                        }
                    }
                }
            } else
                break;
        }
        min = tempMin;
        max = tempMax;
        curBit = tempBit;
        curByte = tempByte;
        while (true) {
            double cur = (min + max) / 2.0;
            int temp = 0;
            for (; temp < probs.length; temp++)
                if (probs[temp] > cur)
                    break;
            if (cur < 0 || cur > 1)
                temp = -1;
            if (temp != val) {
                boolean bit = false;
                if ((fileData.get(curByte) & (128 >> curBit)) != 0)
                    bit = true;
                if (bit)
                    min = cur;
                else
                    max = cur;
                curBit++;
                if (curBit == 8) {
                    curBit = 0;
                    curByte++;
                    if (curByte > fileData.size() - 1)
                        try {
                            fileData.add(bis.readBits(8));
                        } catch (Throwable t) {
                            fileData.add(0);
                        }
                }
            } else {
                tempMin = 0;
                if (val > 0)
                    tempMin = probs[val - 1];
                double factor = 1.0 / (probs[val] - tempMin);
                min = factor * (min - tempMin);
                max = factor * (max - tempMin);
                break;
            }
        }
        return val;
    }

    /*
     * Write compressed output to a bit stream
     */
    @Override
    public long write(BitOutputStream bos, byte[] object) throws IOException {
        this.baos.reset();
        curBit = 0;
        curByte = 0;
        min = 0;
        max = 1;
        localMin = 0;
        localMax = 1;
        this.bitCount = 0;

        try {
            for (int i = 0; i < object.length; i++)
                encodeCharacter(bos, this.map[object[i] & 0xFF]);
            encodeCharacter(bos, this.map[this.TERMINATOR]);
            encodeCharacter(bos, this.map[this.TERMINATOR]);
            flush(bos);
        } catch (Exception ex) {
            Log.getInstance(getClass()).error(ex);
        }

        return this.bitCount;
    }

    private void encodeCharacter(BitOutputStream bos, int character)
            throws Exception {
        if (probs.length < 2 || probs[probs.length - 1] != 1 || character < 0
                || character >= probs.length)
            throw new Exception("Invalid input");
        if (character > 0)
            localMin = probs[character - 1];
        else
            localMin = 0;
        localMax = probs[character];
        while (true) {
            double cur = (min + max) / 2.0;
            if (cur < localMin) {
                curByte |= (128 >> curBit); // set bit = 1, left-to-right
                curBit++;
                if (curBit == 8) {
                    bos.write(curByte, 8);
                    curByte = 0; // byte containing bits to be written
                    curBit = 0; // bit-position, left-to-right
                    this.bitCount += 8;
                }
                min = cur; // wrote 1 (go higher) adjust min
            } else if (cur >= localMax) {
                curBit++;
                if (curBit == 8) {
                    bos.write(curByte, 8);
                    curByte = 0;
                    curBit = 0;
                    this.bitCount += 8;
                }
                max = cur; // wrote 0 (go lower) adjust max
            } else {
                double factor = 1.0 / (localMax - localMin);
                min = factor * (min - localMin);
                max = factor * (max - localMin);
                break;
            }
        }
    }

    private void flush(BitOutputStream bos) throws IOException {
        if (curBit != 0) {
            while (true) {
                while (true) {
                    double cur = (min + max) / 2.0;
                    double mid = (localMin + localMax) / 2.0;
                    if (cur < mid) {
                        curByte |= (128 >> curBit);
                        min = cur;
                    } else
                        max = cur;
                    curBit++;
                    if (curBit == 8) {
                        bos.write(curByte, 8);
                        curByte = 0;
                        curBit = 0;
                        this.bitCount += 8;
                        break;
                    }
                }
                double cur = (min + max) / 2.0;
                if (cur >= localMin && cur < localMax)
                    break;
            }
        }
        bos.close();
    }

    /*
     * Compress and count bits in the end
     */
    @Override
    public long numberOfBits(byte[] object) {
        NullOutputStream baos = new NullOutputStream();
        DefaultBitOutputStream nBos = new DefaultBitOutputStream(baos);

        this.baos.reset();
        curBit = 0;
        curByte = 0;
        min = 0;
        max = 1;
        localMin = 0;
        localMax = 1;
        this.bitCount = 0;

        try {
            for (int i = 0; i < object.length; i++)
                encodeCharacter(nBos, this.map[object[i] & 0xFF]);
            encodeCharacter(nBos, this.map[this.TERMINATOR]);
            encodeCharacter(nBos, this.map[this.TERMINATOR]);
            flush(nBos);
        } catch (Exception ex) {
            Log.getInstance(ArithCodec.class).error(ex);
        }

        return this.bitCount;
    }

    @Override
    public byte[] read(BitInputStream bis, int len) throws IOException {
        throw new RuntimeException("Not implemented.");
    }
}
