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
package htsjdk.samtools.cram.structure.block;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.ExternalCompression;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.io.*;

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
public abstract class Block {
    /**
     * Only external blocks have meaningful Content IDs
     * Other blocks are required to have a Content ID of 0
     */
    public static final int NO_CONTENT_ID = 0;

    /**
     * Identifies CRAM content type of the block.
     */
    private final BlockContentType contentType;

    /**
     * Abstract constructor for Block.  Sets the block content type only.
     *
     * @see RawBlock
     * @see CompressibleBlock
     * @param contentType whether this is a header or data block, and which kind
     */
    protected Block(final BlockContentType contentType) {
        this.contentType = contentType;
    }

    /**
     * Deserialize the Block from the {@link InputStream}. The reading is parametrized by the major CRAM version number.
     *
     * @param major CRAM version major number
     * @param inputStream    input stream to read the block from
     * @return a new {@link CompressibleBlock} object with fields and content from the input stream
     * @throws IOException as per java IO contract
     */
    public static CompressibleBlock readFromInputStream(final int major, InputStream inputStream) throws IOException {
        final boolean v3OrHigher = major >= CramVersions.CRAM_v3.major;
        if (v3OrHigher) {
            inputStream = new CRC32InputStream(inputStream);
        }
        final BlockCompressionMethod method = BlockCompressionMethod.byId(inputStream.read());
        final BlockContentType type = BlockContentType.byId(inputStream.read());
        final int contentId = ITF8.readUnsignedITF8(inputStream);
        final int compressedSize = ITF8.readUnsignedITF8(inputStream);
        final int rawSize = ITF8.readUnsignedITF8(inputStream);

        final byte[] compressedContent = new byte[compressedSize];
        InputStreamUtils.readFully(inputStream, compressedContent, 0, compressedSize);
        if (v3OrHigher) {
            final int actualChecksum = ((CRC32InputStream) inputStream).getCRC32();
            final int checksum = CramInt.readInt32(inputStream);
            if (checksum != actualChecksum) {
                throw new RuntimeException(String.format("Block CRC32 mismatch: %04x vs %04x", checksum, actualChecksum));
            }
        }

        final byte[] uncompressedContent = ExternalCompression.uncompress(method, compressedContent);
        if (uncompressedContent.length != rawSize) {
            throw new CRAMException(String.format("Block uncompressed size did not match expected size: %04x vs %04x", rawSize, uncompressedContent.length));
        }

        return new CompressibleBlock(method, type, contentId, uncompressedContent, compressedContent);
    }

    /**
     * Create a new file header block with the given uncompressed content.
     * The block will have RAW (no) compression and FILE_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new file header {@link RawBlock} object
     */
    public static RawBlock buildNewFileHeaderBlock(final byte[] rawContent) {
        return new RawBlock(BlockContentType.FILE_HEADER, rawContent);
    }

    /**
     * Create a new compression header block with the given uncompressed content.
     * The block will have RAW (no) compression and COMPRESSION_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new compression header {@link RawBlock} object
     */
    public static RawBlock buildNewCompressionHeaderBlock(final byte[] rawContent) {
        return new RawBlock(BlockContentType.COMPRESSION_HEADER, rawContent);
    }

    /**
     * Create a new slice header block with the given uncompressed content.
     * The block will have RAW (no) compression and MAPPED_SLICE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new mapped slice {@link RawBlock} object
     */
    public static RawBlock buildNewSliceHeaderBlock(final byte[] rawContent) {
        return new RawBlock(BlockContentType.MAPPED_SLICE, rawContent);
    }

    /**
     * Create a new external block with the given uncompressed content, content ID and compression method.
     * The block will have EXTERNAL content type.
     *
     * @param contentId the external identifier for the block
     * @param compressor which external compressor to use on this block
     * @param rawContent the uncompressed content of the block
     * @return a new external {@link CompressibleBlock} object
     */
    public static CompressibleBlock buildNewExternalBlock(final int contentId, final ExternalCompressor compressor, final byte[] rawContent) {
        if (contentId == Block.NO_CONTENT_ID) {
            throw new CRAMException("Valid Content ID required.  Given: " + contentId);
        }
        
        final byte[] compressedContent = compressor.compress(rawContent);
        return new CompressibleBlock(compressor.getMethod(), BlockContentType.EXTERNAL, contentId, rawContent, compressedContent);
    }

    /**
     * Create a new core block with the given uncompressed content.
     * The block will have RAW (no) compression and CORE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new mapped slice {@link RawBlock} object
     */
    public static RawBlock buildNewCoreBlock(final byte[] rawContent) {
        return new RawBlock(BlockContentType.CORE, rawContent);
    }

    public abstract BlockCompressionMethod getMethod();

    /**
     * Identifies whether this is a header or data block, and which kind
     * @return the CRAM content type of the block
     */
    public final BlockContentType getContentType() {
        return contentType;
    }

    /**
     * Return the External Content ID for this block.
     * By default this is NO_CONTENT_ID (0) but CompressibleBlocks override this
     *
     * @return the External Content ID, or NO_CONTENT_ID
     */
    public int getContentId() {
        return NO_CONTENT_ID;
    }

    public abstract byte[] getRawContent();

    /**
     * The size of the uncompressed content in bytes.
     */
    public abstract int getRawContentSize();

    public abstract byte[] getCompressedContent();

    /**
     * The size of the compressed content in bytes.
     */
    public abstract int getCompressedContentSize();

    /**
     * Write the block out to the the specified {@link OutputStream}. The method is parametrized with CRAM major version number.
     *
     * @param major CRAM version major number
     * @param outputStream    output stream to write to
     * @throws IOException as per java IO contract
     */
    public final void write(final int major, final OutputStream outputStream) throws IOException {
        if (major >= CramVersions.CRAM_v3.major) {

            final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);

            doWrite(crc32OutputStream);

            outputStream.write(crc32OutputStream.getCrc32_LittleEndian());
        } else {
            doWrite(outputStream);
        }
    }

    private void doWrite(final OutputStream outputStream) throws IOException {
        outputStream.write(getMethod().getMethodId());
        outputStream.write(getContentType().getContentTypeId());

        ITF8.writeUnsignedITF8(getContentId(), outputStream);
        ITF8.writeUnsignedITF8(getCompressedContentSize(), outputStream);
        ITF8.writeUnsignedITF8(getRawContentSize(), outputStream);

        outputStream.write(getCompressedContent());
    }

    @Override
    public String toString() {
        final byte[] rawContent = getRawContent();
        final byte[] compressedContent = getCompressedContent();

        final String raw = Arrays.toString(Arrays.copyOf(rawContent, Math.min(5, rawContent.length)));
        final String comp = Arrays.toString(Arrays.copyOf(compressedContent, Math.min(5, compressedContent.length)));

        return String.format("method=%s, type=%s, id=%d, raw size=%d, compressed size=%d, raw=%s, comp=%s.", getMethod().name(),
                getContentType().name(), getContentId(), getRawContentSize(), getCompressedContentSize(), raw, comp);
    }
}
