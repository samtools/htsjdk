package htsjdk.samtools.cram.io;

import htsjdk.samtools.cram.encoding.rans.RANS;
import htsjdk.samtools.util.IOUtil;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Methods to provide CRAM external compression/decompression features.
 */
public class ExternalCompression {
    private static final int GZIP_COMPRESSION_LEVEL = Integer.valueOf(System.getProperty("gzip.compression.level", "5"));

    /**
     * Compress a byte array into GZIP blob. The method obeys {@link ExternalCompression#GZIP_COMPRESSION_LEVEL} compression level.
     *
     * @param data byte array to compress
     * @return compressed blob
     */
    public static byte[] gzip(final byte[] data) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final GZIPOutputStream gos = new GZIPOutputStream(byteArrayOutputStream) {
            {
                def.setLevel(GZIP_COMPRESSION_LEVEL);
            }
        };
        IOUtil.copyStream(new ByteArrayInputStream(data), gos);
        gos.close();

        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Uncompress a GZIP data blob into a new byte array.
     *
     * @param data compressed data blob
     * @return uncompressed data
     * @throws IOException as per java IO contract
     */
    public static byte[] gunzip(final byte[] data) throws IOException {
        final GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data));
        return InputStreamUtils.readFully(gzipInputStream);
    }

    /**
     * Compress a byte array into BZIP2 blob.
     *
     * @param data byte array to compress
     * @return compressed blob
     */
    public static byte[] bzip2(final byte[] data) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        final BZip2CompressorOutputStream bos = new BZip2CompressorOutputStream(byteArrayOutputStream);
        IOUtil.copyStream(new ByteArrayInputStream(data), bos);
        bos.close();
        return byteArrayOutputStream.toByteArray();
    }

    /**
     * Uncompress a BZIP2 data blob into a new byte array.
     *
     * @param data compressed data blob
     * @return uncompressed data
     * @throws IOException as per java IO contract
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static byte[] unbzip2(final byte[] data) throws IOException {
        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data);
        return InputStreamUtils.readFully(new BZip2CompressorInputStream(byteArrayInputStream));
    }

    /**
     * Compress a byte array into rANS blob.
     *
     * @param data  byte array to compress
     * @param order rANS order
     * @return compressed blob
     */
    public static byte[] rans(final byte[] data, final RANS.ORDER order) {
        final ByteBuffer buffer = RANS.compress(ByteBuffer.wrap(data), order, null);
        return toByteArray(buffer);
    }

    /**
     * Compress a byte array into rANS blob.
     *
     * @param data  byte array to compress
     * @param order rANS order
     * @return compressed blob
     */
    public static byte[] rans(final byte[] data, final int order) {
        final ByteBuffer buffer = RANS.compress(ByteBuffer.wrap(data), RANS.ORDER.fromInt(order), null);
        return toByteArray(buffer);
    }

    /**
     * Uncompress a rANS data blob into a new byte array.
     *
     * @param data compressed data blob
     * @return uncompressed data
     */
    public static byte[] unrans(final byte[] data) {
        final ByteBuffer buf = RANS.uncompress(ByteBuffer.wrap(data), null);
        return toByteArray(buf);
    }


    /**
     * Compress a byte array into XZ blob.
     *
     * @param data byte array to compress
     * @return compressed blob
     */
    public static byte[] xz(final byte[] data) throws IOException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length * 2);
        final XZCompressorOutputStream xzCompressorOutputStream = new XZCompressorOutputStream(byteArrayOutputStream);
        xzCompressorOutputStream.write(data);
        xzCompressorOutputStream.close();
        return byteArrayOutputStream.toByteArray();
    }


    /**
     * Uncompress a XZ data blob into a new byte array.
     *
     * @param data compressed data blob
     * @return uncompressed data
     * @throws IOException as per java IO contract
     */
    public static byte[] unxz(final byte[] data) throws IOException {
        final XZCompressorInputStream xzCompressorInputStream = new XZCompressorInputStream(new ByteArrayInputStream(data));
        return InputStreamUtils.readFully(xzCompressorInputStream);
    }


    private static byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) return buffer.array();

        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
