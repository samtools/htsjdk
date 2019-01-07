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
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.*;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.CramIntArray;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.io.LTF8;
import htsjdk.samtools.cram.structure.block.*;
import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.Log;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;

class SliceIO {
    private static final Log log = Log.getInstance(SliceIO.class);

    private static void readSliceHeader(final int major, final Slice slice, final InputStream readInputStream) {
        slice.headerBlock = Block.read(major, readInputStream);
        if (slice.headerBlock.getContentType() != BlockContentType.MAPPED_SLICE)
            throw new RuntimeException("Slice Header Block expected, found:  " + slice.headerBlock.getContentType().name());

        final InputStream parseInputStream = new ByteArrayInputStream(slice.headerBlock.getUncompressedContent());

        slice.sequenceId = ITF8.readUnsignedITF8(parseInputStream);
        slice.alignmentStart = ITF8.readUnsignedITF8(parseInputStream);
        slice.alignmentSpan = ITF8.readUnsignedITF8(parseInputStream);
        slice.nofRecords = ITF8.readUnsignedITF8(parseInputStream);
        slice.globalRecordCounter = LTF8.readUnsignedLTF8(parseInputStream);
        slice.nofBlocks = ITF8.readUnsignedITF8(parseInputStream);

        slice.contentIDs = CramIntArray.array(parseInputStream);
        slice.embeddedRefBlockContentID = ITF8.readUnsignedITF8(parseInputStream);
        slice.refMD5 = new byte[16];
        InputStreamUtils.readFully(parseInputStream, slice.refMD5, 0, slice.refMD5.length);

        final byte[] bytes = InputStreamUtils.readFully(parseInputStream);

        if (major >= CramVersions.CRAM_v3.major) {
            slice.sliceTags = BinaryTagCodec.readTags(bytes, 0, bytes.length, ValidationStringency.DEFAULT_STRINGENCY);

            SAMBinaryTagAndValue tags = slice.sliceTags;
            while (tags != null) {
                log.debug(String.format("Found slice tag: %s", SAMTag.makeStringTag(tags.tag)));
                tags = tags.getNext();
            }
        }
    }

    private static byte[] createSliceHeaderBlockContent(final int major, final Slice slice) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ITF8.writeUnsignedITF8(slice.sequenceId, byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.alignmentStart, byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.alignmentSpan, byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.nofRecords, byteArrayOutputStream);
        LTF8.writeUnsignedLTF8(slice.globalRecordCounter, byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.nofBlocks, byteArrayOutputStream);

        slice.contentIDs = new int[slice.external.size()];
        int i = 0;
        for (final int id : slice.external.keySet())
            slice.contentIDs[i++] = id;
        CramIntArray.write(slice.contentIDs, byteArrayOutputStream);
        ITF8.writeUnsignedITF8(slice.embeddedRefBlockContentID, byteArrayOutputStream);
        try {
            byteArrayOutputStream.write(slice.refMD5 == null ? new byte[16] : slice.refMD5);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        if (major >= CramVersions.CRAM_v3.major) {
            if (slice.sliceTags != null) {
                final BinaryCodec binaryCoded = new BinaryCodec(byteArrayOutputStream);
                final BinaryTagCodec binaryTagCodec = new BinaryTagCodec(binaryCoded);
                SAMBinaryTagAndValue samBinaryTagAndValue = slice.sliceTags;
                do {
                    log.debug("Writing slice tag: " + SAMTag.makeStringTag(samBinaryTagAndValue.tag));
                    binaryTagCodec.writeTag(samBinaryTagAndValue.tag, samBinaryTagAndValue.value, samBinaryTagAndValue.isUnsignedArray());
                } while ((samBinaryTagAndValue = samBinaryTagAndValue.getNext()) != null);
                // BinaryCodec doesn't seem to cache things.
                // In any case, not calling baseCodec.close() because it's behaviour is
                // irrelevant here.
            }
        }

        return byteArrayOutputStream.toByteArray();
    }

    private static void readSliceBlocks(final int major, final Slice slice, final InputStream inputStream) {
        slice.external = new HashMap<>();
        for (int i = 0; i < slice.nofBlocks; i++) {
            final Block block = Block.read(major, inputStream);

            switch (block.getContentType()) {
                case CORE:
                    slice.coreBlock = block;
                    break;
                case EXTERNAL:
                    if (slice.embeddedRefBlockContentID == block.getContentId()) {
                        slice.embeddedRefBlock = block;
                    }
                    slice.external.put(block.getContentId(), block);
                    break;

                default:
                    throw new RuntimeException("Not a slice block, content type id " + block.getContentType().name());
            }
        }
    }

    public static void write(final int major, final Slice slice, final OutputStream outputStream) {

        slice.nofBlocks = 1 + slice.external.size() + (slice.embeddedRefBlock == null ? 0 : 1);

        {
            slice.contentIDs = new int[slice.external.size()];
            final int i = 0;
            for (final int id : slice.external.keySet())
                slice.contentIDs[i] = id;
        }

        slice.headerBlock = Block.createRawSliceHeaderBlock(createSliceHeaderBlockContent(major, slice));
        slice.headerBlock.write(major, outputStream);

        slice.coreBlock.write(major, outputStream);
        for (final Block block : slice.external.values())
            block.write(major, outputStream);
    }

    public static void read(final int major, final Slice slice, final InputStream inputStream) {
        readSliceHeader(major, slice, inputStream);
        readSliceBlocks(major, slice, inputStream);
    }
}
