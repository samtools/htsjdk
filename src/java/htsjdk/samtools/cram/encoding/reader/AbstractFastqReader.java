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
package htsjdk.samtools.cram.encoding.reader;

import htsjdk.samtools.cram.structure.CramCompressionRecord;
import htsjdk.samtools.cram.structure.ReadTag;
import htsjdk.samtools.util.SequenceUtil;

import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public abstract class AbstractFastqReader extends AbstractReader {
    private ReadFeatureBuffer rfBuf = new ReadFeatureBuffer();
    public boolean reverseNegativeReads = true;
    public boolean appendSegmentIndexToReadNames = true;

    public byte[] referenceSequence;
    public int flags;
    public int compressionFlags;
    public int mateFlags;
    public int readLength;
    public int prevAlStart;
    public byte[] readName;

    public static final int maxReadBufferLength = 1024 * 1024;
    public byte[] bases = new byte[maxReadBufferLength];
    public byte[] scores = new byte[maxReadBufferLength];
    private Map<Integer, Integer> nameCache = new HashMap<Integer, Integer>();
    public long counterOffset = 0;

    public int defaultQS = '?';

    /**
     * For now this is to identify the right buffer to use.
     *
     * @param flags read bit flags
     * @return 0 for non-paired or other rubbish which could not be reliably
     * paired, 1 for first in pair and 2 for second in pair
     */
    protected int getSegmentIndexInTemplate(int flags) {
        if ((flags & CramCompressionRecord.MULTIFRAGMENT_FLAG) == 0)
            return 0;

        if ((flags & CramCompressionRecord.FIRST_SEGMENT_FLAG) != 0)
            return 1;
        else
            return 2;
    }

    protected abstract byte[] refSeqChanged(int seqID);

    public void read() throws IOException {
        int seqId = refId;
        readName = null;
        try {
            flags = bitFlagsC.readData();

            compressionFlags = compBitFlagsC.readData();
            if (refId == -2) {
                seqId = refIdCodec.readData();
            }

            readLength = readLengthC.readData();
            if (AP_delta)
                prevAlStart += alStartC.readData();
            else
                prevAlStart = alStartC.readData();

            readGroupC.skip();

            if (captureReadNames)
                readName = readNameC.readData();

            // mate record:
            if ((compressionFlags & CramCompressionRecord.DETACHED_FLAG) != 0) {
                mateFlags = mbfc.readData();
                if (!captureReadNames)
                    readName = readNameC.readData();

                mrc.skip();
                malsc.skip();
                tsc.skip();
                detachedCount++;
            } else if ((compressionFlags & CramCompressionRecord.HAS_MATE_DOWNSTREAM_FLAG) != 0) {
                int distance = distanceC.readData();
                nameCache.put(recordCounter + distance + 1, recordCounter);
            }

            if (readName == null) {
                // check cache:
                if (nameCache.containsKey(recordCounter)) {
                    int order = nameCache.remove(recordCounter);
                    readName = Long.toString(order + counterOffset).getBytes();
                } else
                    readName = Long.toString(recordCounter + counterOffset).getBytes();
            }

            Integer tagIdList = tagIdListCodec.readData();
            byte[][] ids = tagIdDictionary[tagIdList];
            if (ids.length > 0) {
                for (int i = 0; i < ids.length; i++) {
                    int id = ReadTag.name3BytesToInt(ids[i]);
                    DataReader<byte[]> dataReader = tagValueCodecs.get(id);
                    try {
                        dataReader.skip();
                    } catch (EOFException e) {
                        throw e;
                    }
                }
            }

            if ((flags & CramCompressionRecord.SEGMENT_UNMAPPED_FLAG) == 0) {
                byte[] refBases = referenceSequence;
                if (seqId != refId)
                    refBases = refSeqChanged(seqId);
                rfBuf.readReadFeatures(this);
                rfBuf.restoreReadBases(readLength, prevAlStart, refBases, substitutionMatrix, bases);

                mqc.skip();
            } else {
                bc.readByteArrayInto(bases, 0, readLength);

            }

            Arrays.fill(scores, 0, readLength, (byte) (defaultQS - 33));
            if ((compressionFlags & CramCompressionRecord.FORCE_PRESERVE_QS_FLAG) != 0) {
                qcArray.readByteArrayInto(scores, 0, readLength);
            } else {
                if ((flags & CramCompressionRecord.SEGMENT_UNMAPPED_FLAG) == 0) {
                    rfBuf.restoreQualityScores(readLength, prevAlStart, scores);
                }
            }
            for (int i = 0; i < readLength; i++)
                if (scores[i] == -1)
                    scores[i] = (byte) defaultQS;
                else
                    scores[i] += 33;

            if (reverseNegativeReads && (flags & CramCompressionRecord.NEGATIVE_STRAND_FLAG) != 0) {
                SequenceUtil.reverseComplement(bases, 0, readLength);
                SequenceUtil.reverse(scores, 0, readLength);
            }

            writeRead(readName, flags, bases, scores);

            recordCounter++;
        } catch (Exception e) {
            System.err.printf("Failed at record %d. \n", recordCounter);
            if (readName != null)
                System.err.println("read name: " + new String(readName));
            throw new RuntimeException(e);
        }
    }

    protected abstract void writeRead(byte[] name, int flags, byte[] bases, byte[] scores);

    public abstract void finish();
}
