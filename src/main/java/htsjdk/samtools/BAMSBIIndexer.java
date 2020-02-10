/*
 * The MIT License
 *
 * Copyright (c) 2018 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeEOFException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Writes SBI files for BAM files, as understood by {@link SBIIndex}.
 */
public final class BAMSBIIndexer {

    /**
     * Perform indexing on the given BAM file, at the granularity level specified.
     *
     * @param bamFile the path to the BAM file
     * @param granularity write the offset of every n-th alignment to the index
     * @throws IOException as per java IO contract
     */
    public static void createIndex(final Path bamFile, final long granularity) throws IOException {
        final Path splittingBaiFile = IOUtil.addExtension(bamFile, FileExtensions.SBI);
        try (SeekableStream in = new SeekablePathStream(bamFile); OutputStream out = Files.newOutputStream(splittingBaiFile)) {
            createIndex(in, out, granularity);
        }
    }

    /**
     * Perform indexing on the given BAM file, at the granularity level specified.
     *
     * @param in a seekable stream for reading the BAM file from
     * @param out the stream to write the index to
     * @param granularity write the offset of every n-th alignment to the index
     * @throws IOException as per java IO contract
     */
    public static void createIndex(final SeekableStream in, final OutputStream out, final long granularity) throws IOException {
        long recordStart = SAMUtils.findVirtualOffsetOfFirstRecordInBam(in);
        try (BlockCompressedInputStream blockIn = new BlockCompressedInputStream(in)) {
            blockIn.seek(recordStart);
            // Create a buffer for reading the BAM record lengths. BAM is little-endian.
            final ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            final SBIIndexWriter indexWriter = new SBIIndexWriter(out, granularity);
            while (true) {
                try {
                    recordStart = blockIn.getFilePointer();
                    // Read the length of the remainder of the BAM record (`block_size` in the SAM spec)
                    InputStreamUtils.readFully(blockIn, byteBuffer.array(), 0, 4);
                    final int blockSize = byteBuffer.getInt(0);
                    // Process the record start position, then skip to the start of the next BAM record
                    indexWriter.processRecord(recordStart);
                    InputStreamUtils.skipFully(blockIn, blockSize);
                } catch (RuntimeEOFException e) {
                    break;
                }
            }
            indexWriter.finish(recordStart, in.length());
        }
    }
}
