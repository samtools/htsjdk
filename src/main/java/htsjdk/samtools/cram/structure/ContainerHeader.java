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

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.common.CRAMVersion;
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContainerHeader {
    // Container header values as defined in the specs, in addition to sequenceId from ReferenceContext
    // total length of all blocks in this container (total length of this container, minus the Container Header).
    private final AlignmentContext alignmentContext;
    private final int recordCount;
    private final long globalRecordCounter;
    private final long baseCount;
    private final int blockCount;

    /**
     * {@code landmarks} contains the byte offsets of the beginning of each slice (the offset of each slice's header
     * block), starting from the end of the Container header. Since the Container's compression header block is located
     * immediately after the container header, it has offset 0, so the first entry in the landmarks array will be
     * have the value sizeof(containerHeaderBlock). The same values are redundantly stored within the slices themselves,
     * accessible as {@link Slice#getByteOffsetOfSliceHeaderBlock()}.
     *
     * As an example, suppose we have:
     * - landmarks[0] = 9000
     * - landmarks[1] = 109000
     * - containerBlocksByteSize = 123456
     *
     * Therefore:
     * - the compression header block size = 9000
     * - Slice 0 has offset 9000 and size 100000 (109000 - 9000)
     * - Slice 1 has offset 109000 and size 14456 (123456 - 109000)
     */
    private List<Integer> landmarks;
    private int checksum = 0;
    private int containerBlocksByteSize;

    /**
     * Create a ContainerHeader.
     * @param alignmentContext the alignment context for this container
     * @param blockCount block count for this container
     * @param containerBlocksByteSize
     * @param recordCount record count for this container
     * @param globalRecordCounter global record count for this container
     * @param baseCount base count for this container
     * @param landmarks List of landmarks for this container (may be empty)
     * @param checksum
     */
    public ContainerHeader(
            final AlignmentContext alignmentContext,
            final int blockCount,
            final int containerBlocksByteSize,
            final int recordCount,
            final long globalRecordCounter,
            final long baseCount,
            final List<Integer> landmarks,
            final int checksum) {
        this.alignmentContext = alignmentContext;
        this.blockCount = blockCount;
        this.containerBlocksByteSize = containerBlocksByteSize;
        this.recordCount = recordCount;
        this.globalRecordCounter = globalRecordCounter;
        this.baseCount = baseCount;
        this.landmarks = landmarks;
        this.checksum = checksum;
    }

    /**
     * Create a Container from a partial set of values. Used in cases where we don't yet have all of the
     * values (such as landmarks, which, being stream offsets, can't be calculated until the container is written
     * out to a stream).
     * @param alignmentContext the alignment context for this container
     * @param blockCount block count for this container
     * @param recordCount record count for this container
     * @param globalRecordCounter global record count for this container
     * @param baseCount base count for this container
     */
    public ContainerHeader(
            final AlignmentContext alignmentContext,
            final int blockCount,
            final int recordCount,
            final long globalRecordCounter,
            final int baseCount) {
        this(alignmentContext,
                blockCount,
                0,
                recordCount,
                globalRecordCounter,
                baseCount,
                new ArrayList<>(),
                0);
    }

    /**
     * Create a container header from an {@link InputStream}.
     *
     * @param cramVersion the CRAM version to assume
     * @param inputStream the input stream from which to read
     * @return a new {@link ContainerHeader} object with container header values filled out but empty body (no slices and blocks).
     */
    public ContainerHeader(final CRAMVersion cramVersion, final InputStream inputStream) {
        this.containerBlocksByteSize = CramInt.readInt32(inputStream);
        final ReferenceContext refContext = new ReferenceContext(ITF8.readUnsignedITF8(inputStream));
        final int alignmentStart = ITF8.readUnsignedITF8(inputStream);
        final int alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        this.alignmentContext = new AlignmentContext(refContext, alignmentStart, alignmentSpan);
        this.recordCount = ITF8.readUnsignedITF8(inputStream);
        this.globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        this.baseCount = LTF8.readUnsignedLTF8(inputStream);
        this.blockCount = ITF8.readUnsignedITF8(inputStream);
        this.landmarks = CramIntArray.arrayAsList(inputStream);
        this.checksum = cramVersion.getMajor() >= 3 ? CramInt.readInt32(inputStream) : 0;
    }

    /**
     * Create a ContainerHeader for a SAMFileHeader container.
     * @param containerBlocksByteSize size of the SAMFileHeader block to be embedded in this container
     */
    public static ContainerHeader makeSAMFileHeaderContainer(final int containerBlocksByteSize) {
         return new ContainerHeader(
                 // we need to assign SOME alignment context for this bogus/special header container...
                 AlignmentContext.UNMAPPED_UNPLACED_CONTEXT,
                 1, // block count
                 containerBlocksByteSize,
                 0, // record count
                 0, // global record count
                 0, // base count
                 Collections.emptyList(), // landmarks
                 0); // checksum
    }

    public int getContainerBlocksByteSize() {
        return containerBlocksByteSize;
    }

    public void setContainerBlocksByteSize(int containerBlocksByteSize) {
        this.containerBlocksByteSize = containerBlocksByteSize;
    }

    public AlignmentContext getAlignmentContext() { return alignmentContext; }

    public int getNumberOfRecords() {
        return recordCount;
    }

    public long getGlobalRecordCounter() {
        return globalRecordCounter;
    }

    public long getBaseCount() {
        return baseCount;
    }

    public int getBlockCount() {
        return blockCount;
    }

    public List<Integer> getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(List<Integer> landmarks) {
        this.landmarks = landmarks;
    }

    public int getChecksum() {
        return checksum;
    }

    /**
     * Write CRAM {@link Container} (header only) out into the given {@link OutputStream}.
     * @param cramVersion CRAM version
     * @param outputStream the output stream to write the container header to
     * @return number of bytes written out to the output stream
     */
    public int write(final CRAMVersion cramVersion, final OutputStream outputStream) {
        final CRC32OutputStream crc32OutputStream = new CRC32OutputStream(outputStream);

        int length = (CramInt.writeInt32(getContainerBlocksByteSize(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(alignmentContext.getReferenceContext().getReferenceContextID(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(alignmentContext.getAlignmentStart(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(alignmentContext.getAlignmentSpan(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(getNumberOfRecords(), crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(getGlobalRecordCounter(), crc32OutputStream) + 7) / 8;
        length += (LTF8.writeUnsignedLTF8(getBaseCount(), crc32OutputStream) + 7) / 8;
        length += (ITF8.writeUnsignedITF8(getBlockCount(), crc32OutputStream) + 7) / 8;
        length += (CramIntArray.write(getLandmarks(), crc32OutputStream) + 7) / 8;

        if (cramVersion.getMajor() >= 3) {
            try {
                outputStream.write(crc32OutputStream.getCrc32_LittleEndian());
            } catch (final IOException e) {
                throw new RuntimeIOException(e);
            }
            length += 4 ;
        }

        return length;
    }

    @Override
    public String toString() {
        return String.format(
                "%s, nRecords=%d, nBlocks=%d, nBases=%d, globalCounter=%d",
                        alignmentContext, recordCount, blockCount, baseCount, globalRecordCounter);
    }

    public boolean isEOF() {
        final boolean v3 = containerBlocksByteSize == CramIO.EOF_BLOCK_SIZE_V3 && alignmentContext.getReferenceContext().isUnmappedUnplaced()
                && alignmentContext.getAlignmentStart() == CramIO.EOF_ALIGNMENT_START && blockCount == 1
                && recordCount == 0;

        final boolean v2 = containerBlocksByteSize == CramIO.EOF_BLOCK_SIZE_V2 && alignmentContext.getReferenceContext().isUnmappedUnplaced()
                && alignmentContext.getAlignmentStart() == CramIO.EOF_ALIGNMENT_START && blockCount == 1
                && recordCount == 0;

        return v3 || v2;
    }

}
