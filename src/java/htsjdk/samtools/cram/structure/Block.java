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

import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.io.CRC32_InputStream;
import htsjdk.samtools.cram.io.CRC32_OutputStream;
import htsjdk.samtools.cram.io.CramInt;
import htsjdk.samtools.cram.io.ExternalCompression;
import htsjdk.samtools.cram.io.ITF8;
import htsjdk.samtools.cram.io.InputStreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Class representing CRAM block concept and some methods to operate with block content. CRAM block is used to hold some (usually
 * homogeneous) binary data. An external compression can be applied to the content of a block. The class provides some instantiation static
 * methods, for example to read a block from an input stream. Blocks can be written out to an output stream, this may be considered as a way
 * to serialize/deserialize blocks.
 */
public class Block {
    /**
     * Compression method that applied to this block's content.
     */
    private BlockCompressionMethod method;
    /**
     * Identifies CRAM content type of the block.
     */
    private BlockContentType contentType;
    /**
     * A handle to bind the block with it's metadata.
     */
    private int contentId;
    /**
     * The size of the compressed content in bytes.
     */
    private int compressedContentSize;
    /**
     * The size of the uncompressed content in bytes.
     */
    private int rawContentSize;

    /**
     * Uncompressed and compressed contents respectively.
     */
    private byte[] rawContent, compressedContent;

    public Block() {
    }

    /**
     * Deserialize the block from the {@link InputStream}. The reading is parametrized by the major CRAM version number.
     *
     * @param major CRAM version major number
     * @param is    input stream to read the block from
     * @return a new {@link Block} object with fields and content from the input stream
     * @throws IOException as per java IO contract
     */
    public static Block readFromInputStream(final int major, InputStream is) throws IOException {
        final Block block = new Block();
        final boolean v3_orHigher = major >= CramVersions.CRAM_v3.major;
        if (v3_orHigher) is = new CRC32_InputStream(is);
        block.setMethod(BlockCompressionMethod.values()[is.read()]);

        final int contentTypeId = is.read();
        block.setContentType(BlockContentType.values()[contentTypeId]);

        block.setContentId(ITF8.readUnsignedITF8(is));
        block.compressedContentSize = ITF8.readUnsignedITF8(is);
        block.rawContentSize = ITF8.readUnsignedITF8(is);

        block.compressedContent = new byte[block.compressedContentSize];
        InputStreamUtils.readFully(is, block.compressedContent, 0, block.compressedContent.length);
        if (v3_orHigher) {
            final int actualChecksum = ((CRC32_InputStream) is).getCRC32();
            final int checksum = CramInt.int32(is);
            if (checksum != actualChecksum)
                throw new RuntimeException(String.format("Block CRC32 mismatch: %04x vs %04x", checksum, actualChecksum));
        }

        block.uncompress();
        return block;
    }

    /**
     * Create a new slice header block with the given uncompressed content. The block wil have RAW (no compression) and MAPPED_SLICE content
     * type.
     *
     * @param rawContent the content of the block
     * @return a new mapped slice {@link Block} object
     * @throws IOException as per java IO contract
     */
    public static Block buildNewSliceHeaderBlock(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.MAPPED_SLICE, 0, rawContent);
    }

    /**
     * Create a new core block with the given uncompressed content. The block wil have RAW (no compression) and CORE content type.
     *
     * @param rawContent the content of the block
     * @return a new core {@link Block} object
     * @throws IOException as per java IO contract
     */
    public static Block buildNewCore(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.CORE, 0, rawContent);
    }

    /**
     * Create a new core block with the given uncompressed content. The block wil have RAW (no compression) and CORE content type.
     *
     * @param rawContent the content of the block
     * @return a new core {@link Block} object
     * @throws IOException as per java IO contract
     */
    public static Block buildNewFileHeaderBlock(final byte[] rawContent) {
        final Block block = new Block(BlockCompressionMethod.RAW, BlockContentType.FILE_HEADER, 0, rawContent);
        block.compress();
        return block;
    }

    private Block(final BlockCompressionMethod method, final BlockContentType contentType, final int contentId, final byte[] rawContent) {
        this.setMethod(method);
        this.setContentType(contentType);
        this.setContentId(contentId);
        if (rawContent != null) setRawContent(rawContent);
    }

    @Override
    public String toString() {
        final String raw = rawContent == null ? "NULL" : Arrays.toString(Arrays.copyOf(rawContent, Math.min(5, rawContent.length)));
        final String comp = compressedContent == null ? "NULL" : Arrays.toString(Arrays.copyOf(compressedContent, Math.min(5,
                compressedContent.length)));

        return String.format("method=%s, type=%s, id=%d, raw size=%d, compressed size=%d, raw=%s, comp=%s.", getMethod().name(),
                getContentType().name(), getContentId(), rawContentSize, compressedContentSize, raw, comp);
    }

    boolean isCompressed() {
        return compressedContent != null;
    }

    boolean isUncompressed() {
        return rawContent != null;
    }

    public void setRawContent(final byte[] raw) {
        rawContent = raw;
        rawContentSize = raw == null ? 0 : raw.length;

        compressedContent = null;
        compressedContentSize = 0;
    }

    public byte[] getRawContent() {
        if (rawContent == null) uncompress();
        return rawContent;
    }

    public int getRawContentSize() {
        return rawContentSize;
    }

    public void setContent(final byte[] raw, final byte[] compressed) {
        rawContent = raw;
        compressedContent = compressed;

        if (raw == null) rawContentSize = 0;
        else rawContentSize = raw.length;

        if (compressed == null) compressedContentSize = 0;
        else compressedContentSize = compressed.length;
    }

    void setCompressedContent(final byte[] compressed) {
        this.compressedContent = compressed;
        compressedContentSize = compressed == null ? 0 : compressed.length;

        rawContent = null;
        rawContentSize = 0;
    }

    byte[] getCompressedContent() {
        if (compressedContent == null) compress();
        return compressedContent;
    }

    private void compress() {
        if (compressedContent != null || rawContent == null) return;

        switch (getMethod()) {
            case RAW:
                compressedContent = rawContent;
                compressedContentSize = rawContentSize;
                break;
            case GZIP:
                try {
                    compressedContent = ExternalCompression.gzip(rawContent);
                } catch (final IOException e) {
                    throw new RuntimeException("This should have never happened.", e);
                }
                compressedContentSize = compressedContent.length;
                break;
            case RANS:
                compressedContent = ExternalCompression.rans(rawContent, 1);
                compressedContentSize = compressedContent.length;
                break;
            default:
                break;
        }
    }

    private void uncompress() {
        if (rawContent != null || compressedContent == null) return;

        switch (getMethod()) {
            case RAW:
                rawContent = compressedContent;
                rawContentSize = compressedContentSize;
                break;
            case GZIP:
                try {
                    rawContent = ExternalCompression.gunzip(compressedContent);
                } catch (final IOException e) {
                    throw new RuntimeException("This should have never happened.", e);
                }
                break;
            case BZIP2:
                try {
                    rawContent = ExternalCompression.unbzip2(compressedContent);
                } catch (final IOException e) {
                    throw new RuntimeException("This should have never happened.", e);
                }
                break;
            case LZMA:
                try {
                    rawContent = ExternalCompression.unxz(compressedContent);
                } catch (final IOException e) {
                    throw new RuntimeException("This should have never happened.", e);
                }
                break;
            case RANS:
                rawContent = ExternalCompression.unrans(compressedContent);
                break;
            default:
                throw new RuntimeException("Unknown block compression method: " + getMethod().name());
        }
    }

    /**
     * Write the block out to the the specified {@link: OutputStream}. The method is parametrized with CRAM major version number.
     *
     * @param major CRAM version major number
     * @param os    output stream to write to
     * @throws IOException as per java IO contract
     */
    public void write(final int major, final OutputStream os) throws IOException {
        if (major >= CramVersions.CRAM_v3.major) {

            final CRC32_OutputStream cos = new CRC32_OutputStream(os);

            doWrite(cos);

            os.write(cos.getCrc32_LittleEndian());
        } else doWrite(os);
    }

    private void doWrite(final OutputStream os) throws IOException {
        if (!isCompressed()) compress();
        if (!isUncompressed()) uncompress();

        os.write(getMethod().ordinal());
        os.write(getContentType().ordinal());

        ITF8.writeUnsignedITF8(getContentId(), os);
        ITF8.writeUnsignedITF8(compressedContentSize, os);
        ITF8.writeUnsignedITF8(rawContentSize, os);

        os.write(getCompressedContent());
    }

    public BlockCompressionMethod getMethod() {
        return method;
    }

    public void setMethod(final BlockCompressionMethod method) {
        this.method = method;
    }

    public BlockContentType getContentType() {
        return contentType;
    }

    public void setContentType(final BlockContentType contentType) {
        this.contentType = contentType;
    }

    public int getContentId() {
        return contentId;
    }

    public void setContentId(final int contentId) {
        this.contentId = contentId;
    }

    public int getCompressedContentSize() {
        return compressedContentSize;
    }
}
