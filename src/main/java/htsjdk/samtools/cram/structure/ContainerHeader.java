package htsjdk.samtools.cram.structure;

import htsjdk.samtools.cram.build.CramIO;
import htsjdk.samtools.cram.io.*;
import htsjdk.samtools.cram.ref.ReferenceContext;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// TODO: if MULTIPLE_REFERENCE_ID, all slices in the container must also be MULTIPLE_REFERENCE_ID

public class ContainerHeader {
    // Container Header as defined in the specs, in addition to sequenceId from ReferenceContext
    // total length of all blocks in this container (total length of this container, minus the Container Header).
    public int containerBlocksByteSize;
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
    private int[] landmarks;

    //TODO: where is the checksum validation code ?? where ?
    private int checksum = 0;

    // Note: this is the case where the header is read in from a stream, or is a temporary holder for a SAMFileHeader
    public ContainerHeader(
            final AlignmentContext alignmentContext,
            int containerBlocksByteSize,
            int recordCount,
            long globalRecordCounter,
            long baseCount,
            int blockCount,
            int[] landmarks,
            int checksum) {
        this.alignmentContext = alignmentContext;
        this.containerBlocksByteSize = containerBlocksByteSize;
        this.recordCount = recordCount;
        this.globalRecordCounter = globalRecordCounter;
        this.baseCount = baseCount;
        this.blockCount = blockCount;
        this.landmarks = landmarks;
        this.checksum = checksum;
    }

    // Note: this is the case where we're writing a container from SAMRecords, but don't have all of the values yet (landmarks, etc)
    public ContainerHeader(
            final AlignmentContext alignmentContext,
            final long globalRecordCounter,
            final int blockCount,
            final int noOfRecords,
            final int baseCount) {
        this.alignmentContext = alignmentContext;
        this.globalRecordCounter = globalRecordCounter;
        this.blockCount = blockCount;
        this.recordCount = noOfRecords;
        this.baseCount = baseCount;
        this.landmarks = new int[0];
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

    public int[] getLandmarks() {
        return landmarks;
    }

    public void setLandmarks(int[] landmarks) {
        this.landmarks = landmarks;
    }

    public int getChecksum() {
        return checksum;
    }

    /**
     * Reads container header only from an {@link InputStream}.
     *
     * @param major the CRAM version to assume
     * @param inputStream the input stream to read from
     * @return a new {@link ContainerHeader} object with container header values filled out but empty body (no slices and blocks).
     */
    public static ContainerHeader readContainerHeader(final int major, final InputStream inputStream) {
        final byte[] peek = new byte[4];
        try {
            int character = inputStream.read();
            if (character == -1) {
                // Apparently this is synthesizing an EOF container for v2.1 if one isn't already present
                // in the input stream. Not sure why thats necessary ?
                final int majorVersionForEOF = 2;
                final byte[] eofMarker = major >= 3 ? CramIO.ZERO_F_EOF_MARKER : CramIO.ZERO_B_EOF_MARKER;

                try (final ByteArrayInputStream eofBAIS = new ByteArrayInputStream(eofMarker)) {
                    return readContainerHeader(majorVersionForEOF, eofBAIS);
                }
            }
            peek[0] = (byte) character;
            for (int i = 1; i < peek.length; i++) {
                character = inputStream.read();
                if (character == -1)
                    throw new RuntimeException("Incomplete or broken stream.");
                peek[i] = (byte) character;
            }
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        final int containerByteSize = CramInt.readInt32(peek);
        final ReferenceContext refContext = new ReferenceContext(ITF8.readUnsignedITF8(inputStream));
        final int alignmentStart = ITF8.readUnsignedITF8(inputStream);
        final int alignmentSpan = ITF8.readUnsignedITF8(inputStream);
        final int nofRecords = ITF8.readUnsignedITF8(inputStream);
        final long globalRecordCounter = LTF8.readUnsignedLTF8(inputStream);
        final long bases = LTF8.readUnsignedLTF8(inputStream);
        final int blockCount = ITF8.readUnsignedITF8(inputStream);
        final int landmarks[] = CramIntArray.array(inputStream);
        final int checksum = major >= 3 ? CramInt.readInt32(inputStream) : 0;

        return new ContainerHeader(
                new AlignmentContext(refContext, alignmentStart, alignmentSpan),
                containerByteSize,
                nofRecords,
                globalRecordCounter,
                bases,
                blockCount,
                landmarks,
                checksum);
    }

    /**
     * Write CRAM {@link Container} (header only) out into the given {@link OutputStream}.
     * @param major CRAM major version
     * @param outputStream the output stream to write the container header to
     * @return number of bytes written out to the output stream
     */
    public int writeContainerHeader(final int major, final OutputStream outputStream) {
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

        if (major >= 3) {
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
        return String
                .format("%s, nRecords=%d, nBlocks=%d",
                        alignmentContext, recordCount, blockCount);
    }

    public boolean isEOF() {
        // TODO: add AlignmentContext.isEOF
        final boolean v3 = containerBlocksByteSize == 15 && alignmentContext.getReferenceContext().isUnmappedUnplaced()
                && alignmentContext.getAlignmentStart() == 4542278 && blockCount == 1
                && recordCount == 0;

        final boolean v2 = containerBlocksByteSize == 11 && alignmentContext.getReferenceContext().isUnmappedUnplaced()
                && alignmentContext.getAlignmentStart() == 4542278 && blockCount == 1
                && recordCount == 0;

        return v3 || v2;
    }

}
