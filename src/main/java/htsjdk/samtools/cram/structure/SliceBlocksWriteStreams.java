/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.io.BitOutputStream;
import htsjdk.samtools.cram.io.DefaultBitOutputStream;
import htsjdk.samtools.cram.structure.block.Block;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Provides a layer over a {@link SliceBlocks} object and acts as a bridge between the DataSeries codecs
 * and their underlying blocks when writing a CRAM stream by presenting a bit (core) or byte (external) stream
 * for each block.
 */
public class SliceBlocksWriteStreams {

    private final CompressionHeader compressionHeader;
    private final ByteArrayOutputStream coreBlockByteOutputStream;
    private final BitOutputStream coreBlockBitOutputStream;

    // content ID to ByteArrayOutputStream
    private final Map<Integer, ByteArrayOutputStream> externalOutputStreams = new TreeMap<>();

    /**
     * @param compressionHeader {@link CompressionHeader} for the container containing the slice
     */
    public SliceBlocksWriteStreams(final CompressionHeader compressionHeader) {
        this.compressionHeader = compressionHeader;

        coreBlockByteOutputStream = new ByteArrayOutputStream();
        coreBlockBitOutputStream = new DefaultBitOutputStream(coreBlockByteOutputStream);

        // Create an output stream for each external content ID in the encoding map
        for (final Integer contentID : compressionHeader.getEncodingMap().getExternalIDs()) {
            externalOutputStreams.put(contentID, new ByteArrayOutputStream());
        }
    }

    /**
     * @return the {@link BitOutputStream}  for the core block
     */
    public BitOutputStream getCoreOutputStream() { return coreBlockBitOutputStream; }

    /**
     * Get the ByteArrayOutputStream corresponding to the requested contentID
     * @param contentID ID of content being requested
     * @return ByteArrayOutputStream for contentID
     */
    public ByteArrayOutputStream getExternalOutputStream(final Integer contentID) { return externalOutputStreams.get(contentID); }

    /**
     * Compress and write each each stream to a corresponding Block (note that this does not write
     * the blocks themselves to a container output stream - that can't happen until the slice is aggregated
     * into a container.
     */
    public SliceBlocks flushStreamsToBlocks() {
        closeAllStreams();

        // core block is raw (no compression) and must be written first (prescribed by the spec)
        final Block coreBlock = Block.createRawCoreDataBlock(coreBlockByteOutputStream.toByteArray());

        final List<Block> externalBlocks = new ArrayList<>();
        externalOutputStreams.forEach((contentId, contentStream) -> {
            if (contentId.equals(Block.NO_CONTENT_ID)) {
                throw new CRAMException("A valid content ID is required.  Given: " + contentId);
            }
            externalBlocks.add(compressionHeader.getEncodingMap().createCompressedBlockForStream(contentId, contentStream));
         });

        return new SliceBlocks(coreBlock, externalBlocks);
     }

    private void closeAllStreams() {
        try {
            getCoreOutputStream().close();
            for (ByteArrayOutputStream baos : externalOutputStreams.values()) {
                baos.close();
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
