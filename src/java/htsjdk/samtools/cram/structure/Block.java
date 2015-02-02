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

import htsjdk.samtools.cram.io.ByteBufferUtils;
import htsjdk.samtools.cram.io.CRC32_InputStream;
import htsjdk.samtools.cram.io.CRC32_OutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

public class Block {
    public BlockCompressionMethod method;
    public BlockContentType contentType;
    public int contentId;
    private int compressedContentSize;
    private int rawContentSize;
    private int checksum;

    private byte[] rawContent, compressedContent;

    public Block() {
    }

    public Block(BlockCompressionMethod method, BlockContentType contentType,
                 int contentId, byte[] rawContent, byte[] compressedContent) {
        this.method = method;
        this.contentType = contentType;
        this.contentId = contentId;
        if (rawContent != null)
            setRawContent(rawContent);
        if (compressedContent != null)
            setCompressedContent(compressedContent);
    }

    public Block(InputStream is, boolean readContent, boolean uncompress)
            throws IOException {
        this(2, is, readContent, uncompress);
    }

    public Block(int major, InputStream is, boolean readContent,
                 boolean uncompress) throws IOException {
        if (major >= 3)
            is = new CRC32_InputStream(is);
        method = BlockCompressionMethod.values()[is.read()];

        int contentTypeId = is.read();
        contentType = BlockContentType.values()[contentTypeId];

        contentId = ByteBufferUtils.readUnsignedITF8(is);
        compressedContentSize = ByteBufferUtils.readUnsignedITF8(is);
        rawContentSize = ByteBufferUtils.readUnsignedITF8(is);

        if (readContent) {
            compressedContent = new byte[compressedContentSize];
            ByteBufferUtils.readFully(compressedContent, is);
            if (major >= 3) {
                int actualChecksum = ((CRC32_InputStream) is).getCRC32();
                checksum = ByteBufferUtils.int32(is);
                if (checksum != actualChecksum)
                    throw new RuntimeException(String.format(
                            "Block CRC32 mismatch: %04x vs %04x", checksum,
                            actualChecksum));
            }

            if (uncompress)
                uncompress();
        }
    }

    @Override
    public String toString() {
        String raw = rawContent == null ? "NULL" : Arrays.toString(Arrays
                .copyOf(rawContent, Math.min(5, rawContent.length)));
        String comp = compressedContent == null ? "NULL" : Arrays
                .toString(Arrays.copyOf(compressedContent,
                        Math.min(5, compressedContent.length)));

        return String
                .format("method=%s, type=%s, id=%d, raw size=%d, compressed size=%d, raw=%s, comp=%s.",
                        method.name(), contentType.name(), contentId,
                        rawContentSize, compressedContentSize, raw, comp);
    }

    public boolean isCompressed() {
        return compressedContent != null;
    }

    public boolean isUncompressed() {
        return rawContent != null;
    }

    public void setRawContent(byte[] raw) {
        rawContent = raw;
        rawContentSize = raw == null ? 0 : raw.length;

        compressedContent = null;
        compressedContentSize = 0;
    }

    public byte[] getRawContent() {
        if (rawContent == null)
            uncompress();
        return rawContent;
    }

    public int getRawContentSize() {
        return rawContentSize;
    }

    public void setContent(byte[] raw, byte[] compressed) {
        rawContent = raw;
        compressedContent = compressed;

        if (raw == null)
            rawContentSize = 0;
        else
            rawContentSize = raw.length;

        if (compressed == null)
            compressedContentSize = 0;
        else
            compressedContentSize = compressed.length;
    }

    public void setCompressedContent(byte[] compressed) {
        this.compressedContent = compressed;
        compressedContentSize = compressed == null ? 0 : compressed.length;

        rawContent = null;
        rawContentSize = 0;
    }

    public byte[] getCompressedContent() {
        if (compressedContent == null)
            compress();
        return compressedContent;
    }

    public void compress() {
        if (compressedContent != null || rawContent == null)
            return;

        switch (method) {
            case RAW:
                compressedContent = rawContent;
                compressedContentSize = rawContentSize;
                break;
            case GZIP:
                try {
                    compressedContent = ByteBufferUtils.gzip(rawContent);
                } catch (IOException e) {
                    throw new RuntimeException("This should have never happned.", e);
                }
                compressedContentSize = compressedContent.length;
                break;
            case RANS:
                compressedContent = ByteBufferUtils.rans(rawContent, 1);
                compressedContentSize = compressedContent.length;
                break;
            default:
                break;
        }
    }

    public void uncompress() {
        if (rawContent != null || compressedContent == null)
            return;

        switch (method) {
            case RAW:
                rawContent = compressedContent;
                rawContentSize = compressedContentSize;
                break;
            case GZIP:
                try {
                    rawContent = ByteBufferUtils.gunzip(compressedContent);
                } catch (IOException e) {
                    throw new RuntimeException("This should have never happned.", e);
                }
                break;
            case BZIP2:
                try {
                    rawContent = ByteBufferUtils.bunzip2(compressedContent);
                } catch (IOException e) {
                    throw new RuntimeException("This should have never happned.", e);
                }
                break;
            case LZMA:
                try {
                    rawContent = ByteBufferUtils.unxz(compressedContent);
                } catch (IOException e) {
                    throw new RuntimeException("This should have never happned.", e);
                }
                break;
            case RANS:
                rawContent = ByteBufferUtils.unrans(compressedContent);
                break;
            default:
                throw new RuntimeException("Unknown block compression method: "
                        + method.name());
        }
    }

    public void write(OutputStream os) throws IOException {
        CRC32_OutputStream cos = new CRC32_OutputStream(os);

        if (!isCompressed())
            compress();
        if (!isUncompressed())
            uncompress();

        cos.write(method.ordinal());
        cos.write(contentType.ordinal());

        ByteBufferUtils.writeUnsignedITF8(contentId, cos);
        ByteBufferUtils.writeUnsignedITF8(compressedContentSize, cos);
        ByteBufferUtils.writeUnsignedITF8(rawContentSize, cos);

        cos.write(getCompressedContent());

        os.write(cos.getCrc32_LittleEndian());
    }
}
