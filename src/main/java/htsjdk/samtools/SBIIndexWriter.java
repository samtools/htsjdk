package htsjdk.samtools;

import htsjdk.samtools.util.BinaryCodec;

import java.io.OutputStream;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Writes SBI files as understood by {@link SBIIndex}.
 * <p>
 * To use this class, first construct an instance from an output stream, and a desired granualrity. Then for each
 * record in the file being indexed, pass the virtual file offset of the record to the {@link #processRecord} method.
 * The indexer will keep a count of the records passed in an index every <i>n</i>th record. When there are no records
 * left call {@link #finish(long)} to complete writing the index.
 */
public final class SBIIndexWriter {

    // Default to a granularity level of 4096. This is generally sufficient
    // for very large BAM files, relative to a maximum heap size in the
    // gigabyte range.
    public static final long DEFAULT_GRANULARITY = 4096;

    private static final byte[] EMPTY_MD5 = new byte[16];
    private static final byte[] EMPTY_UUID = new byte[16];

    private final BinaryCodec binaryCodec;
    private final long granularity;
    private final NavigableSet<Long> virtualOffsets = new TreeSet<>();
    private long count;

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
        this.binaryCodec = new BinaryCodec(out);
        this.granularity = granularity;
    }

    /**
     * Process a record for the index: the offset of every <i>n</i>th record will be written to the index.
     *
     * @param virtualOffset virtual file pointer of the record
     */
    public void processRecord(final long virtualOffset) {
        if (count++ % granularity == 0) {
            writeVirtualOffset(virtualOffset);
        }
    }

    /**
     * Write the given virtual offset to the index. The offset is always written to the index, no account is taken
     * of the granularity.
     *
     * @param virtualOffset virtual file pointer of the record
     */
    public void writeVirtualOffset(long virtualOffset) {
        virtualOffsets.add(virtualOffset);
    }

    /**
     * Complete the index, and close the output stream.
     *
     * @param dataFileLength the length of the data file in bytes
     */
    public void finish(long dataFileLength) {
        finish(dataFileLength, null, null);
    }

    /**
     * Complete the index, and close the output stream.
     *
     * @param dataFileLength the length of the data file in bytes
     * @param md5 the MD5 hash of the data file, or null if not specified
     * @param uuid the UUID for the data file, or null if not specified
     */
    public void finish(long dataFileLength, byte[] md5, byte[] uuid) {
        if (md5 != null && md5.length != 16) {
            throw new IllegalArgumentException("Invalid MD5 length: " + md5.length);
        }
        if (uuid != null && uuid.length != 16) {
            throw new IllegalArgumentException("Invalid UUID length: " + uuid.length);
        }

        // header
        binaryCodec.writeBytes(SBIIndex.SBI_MAGIC);
        binaryCodec.writeLong(dataFileLength);
        binaryCodec.writeBytes(md5 == null ? EMPTY_MD5 : md5);
        binaryCodec.writeBytes(uuid == null ? EMPTY_UUID : uuid);
        binaryCodec.writeLong(count);
        binaryCodec.writeLong(granularity);
        binaryCodec.writeLong(virtualOffsets.size());

        // offsets
        for (long virtualOffset : virtualOffsets) {
            binaryCodec.writeLong(virtualOffset);
        }
        binaryCodec.close();
    }

}
