package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Writes SBI files as understood by {@link SBIIndex}.
 * <p>
 * To use this class, first construct an instance from an output stream, and a desired granularity. Then for each
 * record in the file being indexed, pass the virtual file offset of the record to the {@link #processRecord} method.
 * The indexer will keep a count of the records passed in an index every <i>n</i>th record. When there are no records
 * left call {@link #finish} to complete writing the index.
 */
public final class SBIIndexWriter {

    // Default to a granularity level of 4096. This is generally sufficient
    // for very large BAM files, relative to a maximum heap size in the
    // gigabyte range.
    public static final long DEFAULT_GRANULARITY = 4096;

    static final byte[] EMPTY_MD5 = new byte[16];
    static final byte[] EMPTY_UUID = new byte[16];

    private final OutputStream out;
    private final long granularity;
    private final File tempOffsetsFile;
    private final BinaryCodec tempOffsetsCodec;
    private long prev = -1;
    private long recordCount;
    private long virtualOffsetCount;

    /**
     * Prepare to write an SBI index with the default granularity.
     *
     * @param out the stream to write the index to
     */
    public SBIIndexWriter(final OutputStream out) {
        this(out, SBIIndexWriter.DEFAULT_GRANULARITY);
    }

    /**
     * Prepare to write an SBI index.
     *
     * @param out         the stream to write the index to
     * @param granularity write the offset of every <i>n</i>th record to the index
     */
    public SBIIndexWriter(final OutputStream out, final long granularity) {
        this.out = out;
        this.granularity = granularity;
        try {
            // Write the offsets to a temporary file, then write the entire file contents to the output stream at
            // the end, once we know the number of offsets. This is more efficient than using a List<Long> for very
            // large numbers of offsets (e.g. 10^8, which is possible for low granularity), since the list resizing
            // operation is slow.
            this.tempOffsetsFile = File.createTempFile("offsets-", ".headerless.sbi");
            this.tempOffsetsCodec = new BinaryCodec(new BufferedOutputStream(new FileOutputStream(tempOffsetsFile)));
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Process a record for the index: the offset of every <i>n</i>th record will be written to the index.
     *
     * @param virtualOffset virtual file pointer of the record
     */
    public void processRecord(final long virtualOffset) {
        if (recordCount++ % granularity == 0) {
            writeVirtualOffset(virtualOffset);
        }
    }

    void writeVirtualOffset(long virtualOffset) {
        if (prev > virtualOffset) {
            throw new IllegalArgumentException(String.format(
                    "Offsets not in order: %#x > %#x",
                    prev, virtualOffset));
        }
        tempOffsetsCodec.writeLong(virtualOffset);
        virtualOffsetCount++;
        prev = virtualOffset;
    }

    /**
     * Complete the index, and close the output stream.
     *
     * @param finalVirtualOffset the virtual offset at which the next record would start if it were added to the file
     * @param dataFileLength the length of the data file in bytes
     */
    public void finish(long finalVirtualOffset, long dataFileLength) {
        finish(finalVirtualOffset, dataFileLength, null, null);
    }

    /**
     * Complete the index, and close the output stream.
     *
     * @param finalVirtualOffset the virtual offset at which the next record would start if it were added to the file
     * @param dataFileLength the length of the data file in bytes
     * @param md5 the MD5 hash of the data file, or null if not specified
     * @param uuid the UUID for the data file, or null if not specified
     */
    public void finish(long finalVirtualOffset, long dataFileLength, byte[] md5, byte[] uuid) {
        if (md5 != null && md5.length != 16) {
            throw new IllegalArgumentException("Invalid MD5 length: " + md5.length);
        }
        if (uuid != null && uuid.length != 16) {
            throw new IllegalArgumentException("Invalid UUID length: " + uuid.length);
        }
        SBIIndex.Header header = new SBIIndex.Header(dataFileLength, md5 == null ? EMPTY_MD5 : md5, uuid == null ? EMPTY_UUID : uuid, recordCount, granularity);
        finish(header, finalVirtualOffset);
    }

    void finish(SBIIndex.Header header, long finalVirtualOffset) {
        // complete writing the temp offsets file
        writeVirtualOffset(finalVirtualOffset);
        tempOffsetsCodec.close();
        try (BinaryCodec binaryCodec = new BinaryCodec(out);
             InputStream tempOffsets = new BufferedInputStream(new FileInputStream(tempOffsetsFile))) {
            // header
            binaryCodec.writeBytes(SBIIndex.SBI_MAGIC);
            binaryCodec.writeLong(header.getFileLength());
            binaryCodec.writeBytes(header.getMd5());
            binaryCodec.writeBytes(header.getUuid());
            binaryCodec.writeLong(header.getTotalNumberOfRecords());
            binaryCodec.writeLong(header.getGranularity());
            binaryCodec.writeLong(virtualOffsetCount);

            // offsets
            IOUtil.copyStream(tempOffsets, out);
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        } finally {
            tempOffsetsFile.delete();
        }
    }

}
