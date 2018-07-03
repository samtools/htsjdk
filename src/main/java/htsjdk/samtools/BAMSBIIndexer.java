package htsjdk.samtools;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.seekablestream.SeekablePathStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.IOUtil;

import java.io.EOFException;
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
        Path splittingBaiFile = IOUtil.addExtension(bamFile, SBIIndex.FILE_EXTENSION);
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
            final ByteBuffer byteBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN); // BAM is little-endian
            SBIIndexWriter indexWriter = new SBIIndexWriter(out, granularity);
            while (true) {
                try {
                    recordStart = blockIn.getFilePointer();
                    InputStreamUtils.readFully(blockIn, byteBuffer.array(), 0, 4);
                    final int blockSize = byteBuffer.getInt(0); // length of remainder of alignment record
                    indexWriter.processRecord(recordStart);
                    InputStreamUtils.skipFully(blockIn, blockSize);
                } catch (EOFException e) {
                    break;
                }
            }
            indexWriter.finish(recordStart, in.length());
        }
    }
}
