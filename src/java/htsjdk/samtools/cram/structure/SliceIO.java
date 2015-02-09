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

import htsjdk.samtools.BinaryTagCodec;
import htsjdk.samtools.SAMBinaryTagAndValue;
import htsjdk.samtools.SAMTagUtil;
import htsjdk.samtools.ValidationStringency;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.CramArray;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.io.LTF8;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

public class SliceIO {
    private static final Log log = Log.getInstance(SliceIO.class);

    private static void readSliceHeadBlock(int major, Slice s, InputStream is) throws IOException {
        s.headerBlock = Block.readFromInputStream(major, is);
        parseSliceHeaderBlock(major, s);
    }

    private static void parseSliceHeaderBlock(int major, Slice s) throws IOException {
        InputStream is = new ByteArrayInputStream(s.headerBlock.getRawContent());

        s.sequenceId = ITF8.readUnsignedITF8(is);
        s.alignmentStart = ITF8.readUnsignedITF8(is);
        s.alignmentSpan = ITF8.readUnsignedITF8(is);
        s.nofRecords = ITF8.readUnsignedITF8(is);
        s.globalRecordCounter = LTF8.readUnsignedLTF8(is);
        s.nofBlocks = ITF8.readUnsignedITF8(is);

        s.contentIDs = CramArray.array(is);
        s.embeddedRefBlockContentID = ITF8.readUnsignedITF8(is);
        s.refMD5 = new byte[16];
        InputStreamUtils.readFully(is, s.refMD5, 0, s.refMD5.length);

        byte[] bytes = InputStreamUtils.readFully(is);

        if (major >= CramVersions.CRAM_v3.major) {
            s.sliceTags = BinaryTagCodec.readTags(bytes, 0, bytes.length, ValidationStringency.DEFAULT_STRINGENCY);

            SAMBinaryTagAndValue tags = s.sliceTags;
            while (tags != null) {
                log.debug(String.format("Found slice tag: %s", SAMTagUtil.getSingleton().makeStringTag(tags.tag)));
                tags = tags.getNext();
            }
        }
    }

    private static byte[] createSliceHeaderBlockContent(int major, Slice s) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITF8.writeUnsignedITF8(s.sequenceId, baos);
        ITF8.writeUnsignedITF8(s.alignmentStart, baos);
        ITF8.writeUnsignedITF8(s.alignmentSpan, baos);
        ITF8.writeUnsignedITF8(s.nofRecords, baos);
        LTF8.writeUnsignedLTF8(s.globalRecordCounter, baos);
        ITF8.writeUnsignedITF8(s.nofBlocks, baos);

        s.contentIDs = new int[s.external.size()];
        int i = 0;
        for (int id : s.external.keySet())
            s.contentIDs[i++] = id;
        CramArray.write(s.contentIDs, baos);
        ITF8.writeUnsignedITF8(s.embeddedRefBlockContentID, baos);
        baos.write(s.refMD5 == null ? new byte[16] : s.refMD5);

        if (major >= CramVersions.CRAM_v3.major) {
            if (s.sliceTags != null) {
                BinaryCodec bc = new BinaryCodec(baos);
                BinaryTagCodec tc = new BinaryTagCodec(bc);
                SAMBinaryTagAndValue tv = s.sliceTags;
                do {
                    log.debug("Writing slice tag: " + SAMTagUtil.getSingleton().makeStringTag(tv.tag));
                    tc.writeTag(tv.tag, tv.value, tv.isUnsignedArray());
                } while ((tv = tv.getNext()) != null);
                // BinaryCodec doesn't seem to cache things.
                // In any case, not calling bc.close() because it's behaviour is
                // irrelevant here.
            }
        }

        return baos.toByteArray();
    }

    private static void readSliceBlocks(int major, Slice s, InputStream is) throws IOException {
        s.external = new HashMap<Integer, Block>();
        for (int i = 0; i < s.nofBlocks; i++) {
            Block b1 = Block.readFromInputStream(major, is);

            switch (b1.getContentType()) {
                case CORE:
                    s.coreBlock = b1;
                    break;
                case EXTERNAL:
                    if (s.embeddedRefBlockContentID == b1.getContentId()) s.embeddedRefBlock = b1;
                    s.external.put(b1.getContentId(), b1);
                    break;

                default:
                    throw new RuntimeException("Not a slice block, content type id " + b1.getContentType().name());
            }
        }
    }

    public static void write(int major, Slice s, OutputStream os) throws IOException {

        s.nofBlocks = 1 + s.external.size() + (s.embeddedRefBlock == null ? 0 : 1);

        {
            s.contentIDs = new int[s.external.size()];
            int i = 0;
            for (int id : s.external.keySet())
                s.contentIDs[i] = id;
        }

        s.headerBlock = Block.buildNewSliceHeaderBlock(createSliceHeaderBlockContent(major, s));
        s.headerBlock.write(major, os);

        s.coreBlock.write(major, os);
        for (Block e : s.external.values())
            e.write(major, os);
    }

    public static void read(int major, Slice s, InputStream is) throws IOException {
        readSliceHeadBlock(major, s, is);
        readSliceBlocks(major, s, is);
    }
}
