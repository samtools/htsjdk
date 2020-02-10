/*
 * The MIT License
 *
 * Copyright (c) 2019 The Broad Institute
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

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.BlockCompressedOutputStream;
import htsjdk.samtools.util.RuntimeIOException;
import org.apache.commons.compress.utils.CountingOutputStream;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Class for writing SAMRecords in BAM format to an output stream.
 */
public class BAMStreamWriter {
    private final CountingOutputStream countingOut;
    private final BlockCompressedOutputStream compressedOut;
    private final BAMRecordCodec bamRecordCodec;
    private final BAMIndexer bamIndexer;
    private final SBIIndexWriter sbiIndexWriter;
    private SAMRecord previousSamRecord;
    private Chunk previousSamRecordChunk;

    /**
     * Create a BAMStreamWriter for writing. All output streams should be uncompressed streams, since this class
     * performs the necessary compression. All output streams are closed by this class after {@link #finish(boolean)} returns.
     * @param outputStream an output stream for the main BAM output
     * @param indexStream an output stream for the BAI index, or null if no index is required
     * @param sbiStream an output stream for the SBI index, or null if no index is required
     * @param sbiGranularity the granularity of the SBI index (reads per entry)
     * @param header the SAM header
     */
    public BAMStreamWriter(OutputStream outputStream, OutputStream indexStream, OutputStream sbiStream, long sbiGranularity, SAMFileHeader header) {
        countingOut = new CountingOutputStream(outputStream);
        compressedOut = new BlockCompressedOutputStream(countingOut, (Path) null);
        bamRecordCodec = new BAMRecordCodec(header);
        bamRecordCodec.setOutputStream(compressedOut);
        if (indexStream != null) {
            // note we set fillInUninitializedValues to false to get an index that is the same as the unmerged one
            bamIndexer = new BAMIndexer(indexStream, header, false);
        } else {
            bamIndexer = null;
        }
        if (sbiStream != null) {
            sbiIndexWriter = new SBIIndexWriter(sbiStream, sbiGranularity);
        } else {
            sbiIndexWriter = null;
        }
    }

    public void writeHeader(final SAMFileHeader header) {
        final BinaryCodec outputBinaryCodec = new BinaryCodec(compressedOut);
        BAMFileWriter.writeHeader(outputBinaryCodec, header);
        try {
            compressedOut.flush();
        } catch (final IOException ioe) {
            throw new RuntimeIOException(ioe);
        }
    }

    public void writeAlignment(final SAMRecord alignment) {
        if (bamIndexer != null && previousSamRecord != null) {
            // index the previous record since we know it's not the last one (which needs special
            // handling, see the close method)
            previousSamRecord.setFileSource(new SAMFileSource(null, new BAMFileSpan(previousSamRecordChunk)));
            bamIndexer.processAlignment(previousSamRecord);
        }
        final long startOffset = compressedOut.getFilePointer();
        if (sbiIndexWriter != null) {
            sbiIndexWriter.processRecord(startOffset);
        }
        bamRecordCodec.encode(alignment);
        final long stopOffset = compressedOut.getFilePointer();
        previousSamRecord = alignment;
        previousSamRecordChunk = new Chunk(startOffset, stopOffset);
    }

    /**
     * Finish writing and close all the streams.
     * @param writeTerminatorBlock whether to write a BAM terminator block to the main output stream
     */
    public void finish(final boolean writeTerminatorBlock) {
        try {
            compressedOut.close(writeTerminatorBlock);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }

        long finalVirtualOffset = compressedOut.getFilePointer();

        long dataFileLength = countingOut.getBytesWritten();
        if (sbiIndexWriter != null) {
            sbiIndexWriter.finish(finalVirtualOffset, dataFileLength);
        }

        // Adjust the end of the chunk in previousSamRecordChunk so that it is a valid virtual offset
        // the flush operation (above) forces the final block to be written out, and makes sure
        // that finalVirtualOffset has an uncompressed offset of 0, which is always valid even after
        // concatenating BGZF files and shifting their virtual offsets.
        // If we didn't do this then we would have an invalid virtual file pointer if a BGZF file
        // were concatenated following this one.
        if (bamIndexer != null && previousSamRecord != null) {
            previousSamRecordChunk =
                    new Chunk(previousSamRecordChunk.getChunkStart(), finalVirtualOffset);
            previousSamRecord.setFileSource(new SAMFileSource(null, new BAMFileSpan(previousSamRecordChunk)));
            bamIndexer.processAlignment(previousSamRecord);
        }
        if (bamIndexer != null) {
            bamIndexer.finish();
        }
    }
}
