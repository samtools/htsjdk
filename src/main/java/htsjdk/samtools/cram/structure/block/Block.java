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
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.common.CramVersions;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.compression.GZIPExternalCompressor;
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.cram.structure.CompressorCache;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
     * The External Block Content ID, or NO_CONTENT_ID for non-External block
     */
    private final int contentId;

    /**
     * The content stored in this block, in compressed form (if applicable). For blocks with
     * BlockCompressionMethod.RAW, this is the raw content, which is the same in compressed
     * and uncompressed form.
     */
    private final byte[] compressedContent;

    /**
     * The length of the content stored in this block when uncompressed
     */
    private final int uncompressedLength;

    /**
     * Protected constructor of a generic Block, to be called by static factory methods and subclasses.
     *
     * @param compressionMethod the block compression method.  Can be RAW, if uncompressed
     * @param contentType whether this is a header or data block, and which kind
     * @param contentId the External Block Content ID, or NO_CONTENT_ID for non-External block
     * @param compressedContent the compressed form of the data to be stored in this block
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    protected Block(final BlockCompressionMethod compressionMethod,
                    final BlockContentType contentType,
                    final int contentId,
                    final byte[] compressedContent,
                    final int uncompressedLength) {
        this.compressionMethod = compressionMethod;
        this.contentType = contentType;
        this.contentId = contentId;
        this.compressedContent = compressedContent;
        this.uncompressedLength = uncompressedLength;

        // There are quite a few htsjdk and GATk test files around that contain external blocks that violate this
        // (that is they have contentID==0). So we may have to leave this out, and only validate that we don't violate
        // this on write. See https://github.com/samtools/htsjdk/issues/1232
        //if (type == BlockContentType.EXTERNAL && getContentId() == Block.NO_CONTENT_ID) {
        //    throw new CRAMException("Valid Content ID required for external blocks.");
        //}

        if (contentType != BlockContentType.EXTERNAL && contentId != Block.NO_CONTENT_ID) {
            throw new CRAMException("Cannot set a Content ID for non-external blocks.");
        }
    }

    /**
     * Create a new non-external block with the given uncompressed content.
     * The block will have RAW (no) compression.
     *
     * @param contentType whether this is a header or data block, and which kind
     * @param rawContent the raw data to be stored in this block
     */
    private static Block createRawNonExternalBlock(final BlockContentType contentType, final byte[] rawContent) {
        if (contentType == BlockContentType.EXTERNAL) {
            throw new CRAMException("Code error: cannot use the non-external factory method for EXTERNAL blocks.");
        }

        return new Block(BlockCompressionMethod.RAW, contentType, NO_CONTENT_ID, rawContent, rawContent.length);
    }

    /**
     * Create a new file header block with the given uncompressed content.
     * The block will have GZIP compression and FILE_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createGZIPFileHeaderBlock(final byte[] rawContent) {
        return new Block(
                BlockCompressionMethod.GZIP,
                BlockContentType.FILE_HEADER, NO_CONTENT_ID,
                (new GZIPExternalCompressor()).compress(rawContent),
                rawContent.length);
    }

    /**
     * Create a new compression header block with the given uncompressed content.
     * The block will have RAW (no) compression and COMPRESSION_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawCompressionHeaderBlock(final byte[] rawContent) {
        return createRawNonExternalBlock(BlockContentType.COMPRESSION_HEADER, rawContent);
    }

    /**
     * Create a new slice header block with the given uncompressed content.
     * The block will have RAW (no) compression and MAPPED_SLICE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawSliceHeaderBlock(final byte[] rawContent) {
        return createRawNonExternalBlock(BlockContentType.MAPPED_SLICE, rawContent);
    }

    /**
     * Create a new core data block with the given uncompressed content.
     * The block will have RAW (no) compression and CORE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link Block} object
     */
    public static Block createRawCoreDataBlock(final byte[] rawContent) {
        return createRawNonExternalBlock(BlockContentType.CORE, rawContent);
    }

    /**
     * Create a new external data block with the given compression method, uncompressed content, and content ID.
     * The block will have EXTERNAL content type.
     *
     * @param compressionMethod the compression method used in this block
     * @param contentId the external identifier for the block
     * @param compressedContent the content of this block, in compressed mode
     * @param uncompressedLength the length of the content stored in this block when uncompressed
     */
    public static Block createExternalBlock(final BlockCompressionMethod compressionMethod,
                                            final int contentId,
                                            final byte[] compressedContent,
                                            final int uncompressedLength) {
        ValidationUtils.validateArg(contentId >= 0, "Invalid external block content id");
        return new Block(compressionMethod, BlockContentType.EXTERNAL,
                contentId, compressedContent, uncompressedLength);
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
     * Return the External Content ID for this block.  Only ExternalBlocks have a meaningful Content ID.
     *
     * @return the External Content ID, or NO_CONTENT_ID
     */
    public int getContentId() {
        return contentId;
    }

    /**
     * Return the raw (uncompressed) content from a block. The block must have {@code BlockCompressionMethod}
     * {@link BlockCompressionMethod#RAW}.
     *
     * @return The raw, uncompressed block content.
     * @throws IllegalArgumentException if the block is not {@link BlockCompressionMethod#RAW}.
     */
    public final byte[] getRawContent() {
        ValidationUtils.validateArg(getCompressionMethod() == BlockCompressionMethod.RAW,
                "getRawContent should only be called on blocks with RAW compression method");
        return compressedContent;
    }

    /**
     * Uncompress the stored block content (if not RAW) and return the uncompressed content.
     *
     * @return The uncompressed block content.
     * @throws CRAMException The uncompressed length did not match what was expected.
     * @param compressorCache
     */
    public final byte[] getUncompressedContent(final CompressorCache compressorCache) {
        // when uncompressing, no compressor-specific args are required since any variant of the compressor will do
        final ExternalCompressor compressor = compressorCache.getCompressorForMethod(compressionMethod, ExternalCompressor.NO_COMPRESSION_ARG);
        final byte[] uncompressedContent = compressor.uncompress(compressedContent);
        if (uncompressedContent.length != uncompressedLength) {
            throw new CRAMException(String.format(
                    "Block uncompressed length did not match expected length: %04x vs %04x",
                    uncompressedLength,
                    uncompressedContent.length));
        }
        return uncompressedContent;
    }

    /**
     * @return The size of the uncompressed content in bytes.
     */
    public int getUncompressedContentSize() {
        return uncompressedLength;
    }

    /**
     * @return The compressed block content.
     */
    public final byte[] getCompressedContent() {
        return compressedContent;
    }

    /**
     * @return The size of the compressed content in bytes.
     */
    public final int getCompressedContentSize() {
        return compressedContent.length;
    }

    /**
     * Deserialize the Block from the {@link InputStream}. The reading is parameterized by the major CRAM version number.
     *
     * @param cramVersion CRAM version
     * @param inputStream    input stream to read the block from
     * @return a subtype of {@link Block} object with fields and content from the input stream
     */
    public static Block read(final CRAMVersion cramVersion, InputStream inputStream) {
        final boolean v3OrHigher = cramVersion.getMajor() >= CramVersions.CRAM_v3.getMajor();
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
                    throw new RuntimeException(String.format("Block CRC32 mismatch, actual: %04x expected: %04x", checksum, actualChecksum));
                }
            }

            return new Block(compressionMethod, contentType, contentId, compressedContent, uncompressedSize);
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Write the block out to the the specified {@link OutputStream}.
     * The method is parameterized with the CRAM major version number.
     *
     * @param cramVersion CRAM version major number
     * @param outputStream output stream to write to
     */
    public final void write(final CRAMVersion cramVersion, final OutputStream outputStream) {
        try {
            if (cramVersion.getMajor() >= CramVersions.CRAM_v3.getMajor()) {
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
        return String.format("method=%s, type=%s, id=%d, raw size=%d, compressed size=%d",
                getCompressionMethod().name(),
                getContentType().name(),
                getContentId(),
                getUncompressedContentSize(),
                getCompressedContentSize());
    }

}
