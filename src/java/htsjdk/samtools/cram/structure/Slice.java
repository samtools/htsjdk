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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.SequenceUtil;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Map;

public class Slice {
    public static final int UNMAPPED_OR_NOREF = -1;
    public static final int MUTLIREF = -2;
    private static final Log log = Log.getInstance(Slice.class);

    // as defined in the specs:
    public int sequenceId = -1;
    public int alignmentStart = -1;
    public int alignmentSpan = -1;
    public int nofRecords = -1;
    public long globalRecordCounter = -1;
    public int nofBlocks = -1;
    public int[] contentIDs;
    public int embeddedRefBlockContentID = -1;
    public byte[] refMD5;

    // content associated with ids:
    public Block headerBlock;
    public BlockContentType contentType;
    public Block coreBlock;
    public Block embeddedRefBlock;
    public Map<Integer, Block> external;

    // for indexing purposes:
    public int offset = -1;
    public long containerOffset = -1;
    public int size = -1;
    public int index = -1;

    // to pass this to the container:
    public long bases;

    private static final int shoulder = 10;

    /**
     * @param ref
     * @return true if the slice is completely within the reference and false if
     * the slice's end is beyond the reference.
     */
    private boolean alignmentBordersSanityCheck(byte[] ref) {
        if (alignmentStart > 0 && sequenceId >= 0 && ref == null)
            throw new NullPointerException("Mapped slice reference is null.");

        if (alignmentStart > ref.length) {
            log.error(String.format("Slice mapped outside of reference: seqid=%d, alstart=%d, counter=%d.", sequenceId,
                    alignmentStart, globalRecordCounter));
            throw new RuntimeException("Slice mapped outside of the reference.");
        }

        if (alignmentStart - 1 + alignmentSpan > ref.length) {
            log.warn(String.format(
                    "Slice partially mapped outside of reference: seqid=%d, alstart=%d, alspan=%d, counter=%d.",
                    sequenceId, alignmentStart, alignmentSpan, globalRecordCounter));
            return false;
        }

        return true;
    }

    public boolean validateRefMD5(byte[] ref) throws NoSuchAlgorithmException {
        alignmentBordersSanityCheck(ref);

        if (!validateRefMD5(ref, alignmentStart, alignmentSpan, refMD5)) {
            String excerpt = getBrief(alignmentStart, alignmentSpan, ref, shoulder, null);

            if (validateRefMD5(ref, alignmentStart, alignmentSpan - 1, refMD5)) {
                log.warn(String.format("Reference MD5 matches partially for slice %d:%d-%d, %s", sequenceId,
                        alignmentStart, alignmentStart + alignmentSpan - 1, excerpt));
                return true;
            }

            log.error(String.format("Reference MD5 mismatch for slice %d:%d-%d, %s", sequenceId, alignmentStart,
                    alignmentStart + alignmentSpan - 1, excerpt));
            return false;
        }

        return true;
    }

    private static boolean validateRefMD5(byte[] ref, int alignmentStart, int alignmentSpan, byte[] expectedMD5) {
        int span = Math.min(alignmentSpan, ref.length - alignmentStart + 1);
        String md5 = SequenceUtil.calculateMD5String(ref, alignmentStart - 1, span);
        return md5.equals(String.format("%032x", new BigInteger(1, expectedMD5)));
    }

    private static String getBrief(int start_1based, int span, byte[] bases, int shoulderLength, StringBuffer sb) {
        if (sb == null)
            sb = new StringBuffer();
        int from_inc = start_1based - 1;

        int to_exc = start_1based + span - 1;
        to_exc = Math.min(to_exc, bases.length);

        if (to_exc - from_inc <= 2 * shoulderLength) {
            sb.append(new String(Arrays.copyOfRange(bases, from_inc, to_exc)));
        } else {
            sb.append(new String(Arrays.copyOfRange(bases, from_inc, from_inc + shoulderLength)));
            sb.append("...");
            sb.append(new String(Arrays.copyOfRange(bases, to_exc - shoulderLength, to_exc)));
        }

        return sb.toString();
    }

    public static void main(String[] args) {
        String s = "0123456789";
        byte[] bases = s.getBytes();
        StringBuffer sb;
        int start, span, shoulder;
        String format = "start %d, span %d, shoulder %d:\t";

        start = 1;
        span = 1;
        shoulder = 1;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 1;
        span = 11;
        shoulder = 1;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 1;
        span = 10;
        shoulder = 10;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 1;
        shoulder = 1;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 11;
        shoulder = 1;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 10;
        shoulder = 10;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 2;
        shoulder = 2;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 4;
        shoulder = 2;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 5;
        shoulder = 2;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 10;
        shoulder = 4;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 10;
        shoulder = 5;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());

        start = 2;
        span = 10;
        shoulder = 6;
        sb = new StringBuffer(String.format(format, start, span, shoulder));
        getBrief(start, span, bases, shoulder, sb);
        System.out.println(sb.toString());
    }

    public void setRefMD5(byte[] ref) {
        alignmentBordersSanityCheck(ref);

        if (sequenceId < 0 && alignmentStart < 1) {
            refMD5 = new byte[16];
            Arrays.fill(refMD5, (byte) 0);

            log.debug("Empty slice ref md5 is set.");
        } else {

            int span = Math.min(alignmentSpan, ref.length - alignmentStart + 1);

            if (alignmentStart + span > ref.length + 1)
                throw new RuntimeException("Invalid alignment boundaries.");

            refMD5 = SequenceUtil.calculateMD5(ref, alignmentStart - 1, span);

            StringBuffer sb = new StringBuffer();
            int shoulder = 10;
            sb.append(new String(Arrays.copyOfRange(ref, alignmentStart - 1, alignmentStart + shoulder)));
            sb.append("...");
            sb.append(new String(Arrays.copyOfRange(ref, alignmentStart - 1 + span - shoulder, alignmentStart + span)));

            log.debug(String.format("Slice md5: %s for %d:%d-%d, %s",
                    String.format("%032x", new BigInteger(1, refMD5)), sequenceId, alignmentStart, alignmentStart
                            + span - 1, sb.toString()));
        }
    }
}
