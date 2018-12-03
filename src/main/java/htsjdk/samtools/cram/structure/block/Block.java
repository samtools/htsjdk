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
public abstract class Block {
    /**
     * Only external blocks have meaningful Content IDs
     * Other blocks are required to have a Content ID of 0
     */
    public static final int NO_CONTENT_ID = 0;

    /**
     * Compression method that applied to this block's content.
     */
    private final BlockCompressionMethod method;

    /**
     * Identifies CRAM content type of the block.
     */
    private final BlockContentType contentType;

    /**
     * The content stored in this block, in compressed form (if applicable)
     */
    protected final byte[] compressedContent;

    /**
     * Abstract constructor of a generic Block, to be called by subclasses.
     *
     * @param method the block compression method.  Can be RAW, if uncompressed
     * @param type whether this is a header or data block, and which kind
     * @param compressedContent the compressed form of the data to be stored in this block
     */
    protected Block(final BlockCompressionMethod method,
                    final BlockContentType type,
                    final byte[] compressedContent) {
        this.method = method;
        this.contentType = type;
        this.compressedContent = compressedContent;

        // causes test failures.  https://github.com/samtools/htsjdk/issues/1232
//        if (type == BlockContentType.EXTERNAL && getContentId() == Block.NO_CONTENT_ID) {
//            throw new CRAMException("Valid Content ID required for external blocks.");
//        }

        if (type != BlockContentType.EXTERNAL && getContentId() != Block.NO_CONTENT_ID) {
            throw new CRAMException("Cannot set a Content ID for non-external blocks.");
        }

    }

    /**
     * Create a new file header block with the given uncompressed content.
     * The block will have RAW (no) compression and FILE_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link FileHeaderBlock} object
     */
    public static FileHeaderBlock uncompressedFileHeaderBlock(final byte[] rawContent) {
        return new FileHeaderBlock(BlockCompressionMethod.RAW, rawContent);
    }

    /**
     * Create a new compression header block with the given uncompressed content.
     * The block will have RAW (no) compression and COMPRESSION_HEADER content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link CompressionHeaderBlock} object
     */
    public static CompressionHeaderBlock uncompressedCompressionHeaderBlock(final byte[] rawContent) {
        return new CompressionHeaderBlock(BlockCompressionMethod.RAW, rawContent);
    }

    /**
     * Create a new slice header block with the given uncompressed content.
     * The block will have RAW (no) compression and MAPPED_SLICE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link SliceHeaderBlock} object
     */
    public static SliceHeaderBlock uncompressedSliceHeaderBlock(final byte[] rawContent) {
        return new SliceHeaderBlock(BlockCompressionMethod.RAW, rawContent);
    }

    /**
     * Create a new core data block with the given uncompressed content.
     * The block will have RAW (no) compression and CORE content type.
     *
     * @param rawContent the uncompressed content of the block
     * @return a new {@link CoreDataBlock} object
     */
    public static CoreDataBlock uncompressedCoreBlock(final byte[] rawContent) {
        return new CoreDataBlock(BlockCompressionMethod.RAW, rawContent);
    }

    /**
     * Create a new external data block with the given content ID, compressor, and uncompressed content.
     * The block will have EXTERNAL content type.
     *
     * @param contentId the external identifier for the block
     * @param compressor which external compressor to use on this block
     * @param rawContent the uncompressed content of the block
     * @return a new {@link ExternalDataBlock} object
     */
    public static ExternalDataBlock externalDataBlock(final int contentId, final ExternalCompressor compressor, final byte[] rawContent) {
        // remove after https://github.com/samtools/htsjdk/issues/1232
        if (contentId == Block.NO_CONTENT_ID) {
            throw new CRAMException("Valid Content ID required.  Given: " + contentId);
        }

        return new ExternalDataBlock(compressor.getMethod(), compressor.compress(rawContent), contentId);
    }

    public final BlockCompressionMethod getMethod() {
        return method;
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
        return ExternalCompression.uncompress(method, compressedContent);
    }

    /**
     * The size of the uncompressed content in bytes.
     */
    public int getUncompressedContentSize() {
        return getUncompressedContent().length;
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

            // TODO: is this check worthwhile?  it may be expensive.
            final byte[] uncompressedContent = ExternalCompression.uncompress(method, compressedContent);
            if (uncompressedContent.length != rawSize) {
                throw new CRAMException(String.format("Block uncompressed size did not match expected size: %04x vs %04x", rawSize, uncompressedContent.length));
            }

            switch (type) {
                case FILE_HEADER:
                    return new FileHeaderBlock(method, compressedContent);
                case COMPRESSION_HEADER:
                    return new CompressionHeaderBlock(method, compressedContent);
                case MAPPED_SLICE:
                    return new SliceHeaderBlock(method, compressedContent);
                case EXTERNAL:
                    return new ExternalDataBlock(method, compressedContent, contentId);
                case CORE:
                    return new CoreDataBlock(method, compressedContent);
                default:
                    throw new CRAMException("Unknown BlockContentType " + type.name());
            }
        }
        catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Write the block out to the the specified {@link OutputStream}. The method is parameterized with CRAM major version number.
     *
     * @param major CRAM version major number
     * @param outputStream    output stream to write to
     * @throws IOException as per java IO contract
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
        outputStream.write(getMethod().getMethodId());
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

        return String.format("method=%s, type=%s, id=%d, raw size=%d, compressed size=%d, raw=%s, comp=%s.", getMethod().name(),
                getContentType().name(), getContentId(), getUncompressedContentSize(), getCompressedContentSize(), raw, comp);
    }
}
