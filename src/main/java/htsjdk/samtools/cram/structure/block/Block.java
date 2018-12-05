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
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.util.RuntimeIOException;

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
     * Only external blocks have meaningful Content IDs
     * Other blocks are required to have a Content ID of 0
     */
    public static final int NO_CONTENT_ID = 0;

    /**
     * Compression method that applied to this block's content.
     */
    private final BlockCompressionMethod compressionMethod;

    /**
     * Identifies CRAM content type of the block.
     */
    private final BlockContentType contentType;

    /**
     * The content stored in this block, in compressed form (if applicable)
     */
    protected final byte[] compressedContent;

    /**
     * The length of the content stored in this block when uncompressed
     */
    private final int uncompressedLength;

    /**
     * Private constructor of a generic Block, to be called by static factory methods and subclasses.
     *
     * @param compressionMethod the block compression method.  Can be RAW, if uncompressed
     * @param contentType whether this is a header or data block, and which kind
     * @param compressedContent the compressed form of the data to be stored in this block
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    protected Block(final BlockCompressionMethod compressionMethod,
                    final BlockContentType contentType,
                    final byte[] compressedContent,
                    final int uncompressedLength) {
        this.compressionMethod = compressionMethod;
        this.contentType = contentType;
        this.compressedContent = compressedContent;
        this.uncompressedLength = uncompressedLength;

        // causes test failures.  https://github.com/samtools/htsjdk/issues/1232
//        if (type == BlockContentType.EXTERNAL && getContentId() == Block.NO_CONTENT_ID) {
//            throw new CRAMException("Valid Content ID required for external blocks.");
//        }

        if (contentType != BlockContentType.EXTERNAL && getContentId() != Block.NO_CONTENT_ID) {
            throw new CRAMException("Cannot set a Content ID for non-external blocks.");
        }

    }

    /**
     * Create a new file header block with the given uncompressed content.
     * The block will have RAW (no) compression and FILE_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawFileHeaderBlock(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.FILE_HEADER, rawContent, rawContent.length);
    }

    /**
     * Create a new compression header block with the given uncompressed content.
     * The block will have RAW (no) compression and COMPRESSION_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawCompressionHeaderBlock(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.COMPRESSION_HEADER, rawContent, rawContent.length);
    }

    /**
     * Create a new slice header block with the given uncompressed content.
     * The block will have RAW (no) compression and MAPPED_SLICE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawSliceHeaderBlock(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.MAPPED_SLICE, rawContent, rawContent.length);
    }

    /**
     * Create a new core data block with the given uncompressed content.
     * The block will have RAW (no) compression and CORE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawCoreDataBlock(final byte[] rawContent) {
        return new Block(BlockCompressionMethod.RAW, BlockContentType.CORE, rawContent, rawContent.length);
    }

    public final BlockCompressionMethod getCompressionMethod() {
        return compressionMethod;
    }

    /**
     * Identifies whether this is a header or data block, and which kind
     * @return the CRAM content type of the block
     */
    public final BlockContentType getContentType() {
        return contentType;
    }

    /**
     * Return the External Content ID for this block.
     *
     * Only ExternalBlocks have a meaningful Content ID, so that class overrides this method.
     *
     * @return the External Content ID, or NO_CONTENT_ID
     */
    public int getContentId() {
        return NO_CONTENT_ID;
    }

    public final byte[] getUncompressedContent() {
        final byte[] uncompressedContent = ExternalCompression.uncompress(compressionMethod, compressedContent);
        if (uncompressedContent.length != uncompressedLength) {
            throw new CRAMException(String.format("Block uncompressed length did not match expected length: %04x vs %04x", uncompressedLength, uncompressedContent.length));
        }
        return uncompressedContent;
    }

    /**
     * The size of the uncompressed content in bytes.
     */
    public int getUncompressedContentSize() {
        return uncompressedLength;
    }

    /**
     * Return the compressed block content
     */

    public final byte[] getCompressedContent() {
        return compressedContent;
    }


    public final int getCompressedContentSize() {
        return compressedContent.length;
    }

    /**
     * Deserialize the Block from the {@link InputStream}. The reading is parameterized by the major CRAM version number.
     *
     * @param major CRAM version major number
     * @param inputStream    input stream to read the block from
     * @return a subtype of {@link Block} object with fields and content from the input stream
     */
    public static Block read(final int major, InputStream inputStream) {
        final boolean v3OrHigher = major >= CramVersions.CRAM_v3.major;
        if (v3OrHigher) {
            inputStream = new CRC32InputStream(inputStream);
        }

        try {
            final BlockCompressionMethod compressionMethod = BlockCompressionMethod.byId(inputStream.read());
            final BlockContentType contentType = BlockContentType.byId(inputStream.read());
            final int contentId = ITF8.readUnsignedITF8(inputStream);
            final int compressedSize = ITF8.readUnsignedITF8(inputStream);
            final int uncompressedSize = ITF8.readUnsignedITF8(inputStream);

            final byte[] compressedContent = new byte[compressedSize];
            InputStreamUtils.readFully(inputStream, compressedContent, 0, compressedSize);
            if (v3OrHigher) {
                final int actualChecksum = ((CRC32InputStream) inputStream).getCRC32();
                final int checksum = CramInt.readInt32(inputStream);
                if (checksum != actualChecksum) {
                    throw new RuntimeException(String.format("Block CRC32 mismatch: %04x vs %04x", checksum, actualChecksum));
                }
            }

            if (contentType == BlockContentType.EXTERNAL) {
                return new ExternalDataBlock(compressionMethod, contentId, compressedContent, uncompressedSize);
            } else {
                return new Block(compressionMethod, contentType, compressedContent, uncompressedSize);
            }
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Write the block out to the the specified {@link OutputStream}.
     * The method is parameterized with the CRAM major version number.
     *
     * @param major CRAM version major number
     * @param outputStream output stream to write to
     */
    public final void write(final int major, final OutputStream outputStream) {
        try {
            if (major >= CramVersions.CRAM_v3.major) {
                final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);
                doWrite(crc32OutputStream);
                outputStream.write(crc32OutputStream.getCrc32_LittleEndian());
            } else {
                doWrite(outputStream);
            }
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    private void doWrite(final OutputStream outputStream) throws IOException {
        outputStream.write(getCompressionMethod().getMethodId());
        outputStream.write(getContentType().getContentTypeId());

        ITF8.writeUnsignedITF8(getContentId(), outputStream);
        ITF8.writeUnsignedITF8(getCompressedContentSize(), outputStream);
        ITF8.writeUnsignedITF8(getUncompressedContentSize(), outputStream);

        outputStream.write(getCompressedContent());
    }

    @Override
    public String toString() {
        final byte[] uncompressed = getUncompressedContent();
        final byte[] compressed = getCompressedContent();

        final String raw = Arrays.toString(Arrays.copyOf(uncompressed, Math.min(5, uncompressed.length)));
        final String comp = Arrays.toString(Arrays.copyOf(compressed, Math.min(5, compressed.length)));

        return String.format("compression method=%s, content type=%s, id=%d, raw size=%d, compressed size=%d, raw=%s, comp=%s.",
                getCompressionMethod().name(), getContentType().name(), getContentId(),
                getUncompressedContentSize(), getCompressedContentSize(), raw, comp);
    }
}
