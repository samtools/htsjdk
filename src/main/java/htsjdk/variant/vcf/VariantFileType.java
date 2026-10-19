package htsjdk.variant.vcf;

import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/**
 * The kinds of variant file htsjdk reads and writes, and the one place that recognises them: by file name with
 * {@link #fromFileName(String)}, or by the first bytes of a file or stream with {@link #fromContents(byte[])}.
 */
public enum VariantFileType {
    /** Uncompressed VCF text. */
    VCF,
    /** VCF text compressed with BGZF, or, when recognised by content, with plain gzip. */
    BLOCK_COMPRESSED_VCF,
    /** BCF version 2, raw or BGZF-compressed. */
    BCF;

    /**
     * The most leading bytes the stream and {@link Path} overloads of {@code fromContents} read: one maximal BGZF
     * block, enough for a gzip header with a long file name and the start of the data it compresses. They stop
     * sooner once the bytes read are enough to tell the type.
     */
    public static final int PROBE_LENGTH = BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE;

    /** The starts of the VCF lines that {@link VCFCodec} accepts as a file's first line. */
    private static final List<byte[]> VCF_MAGIC_LINES = List.of(
            VCFCodec.VCF4_MAGIC_HEADER.getBytes(StandardCharsets.US_ASCII),
            VCFCodec.VCF3_MAGIC_HEADER.getBytes(StandardCharsets.US_ASCII),
            VCFCodec.VCF3_2_MAGIC_HEADER.getBytes(StandardCharsets.US_ASCII));

    /** How many decompressed bytes it takes to tell VCF from BCF from neither. */
    private static final int MAGIC_LENGTH = Math.max(
            BCF2Codec.SIZEOF_BCF_HEADER,
            VCF_MAGIC_LINES.stream().mapToInt(magic -> magic.length).max().getAsInt());

    /**
     * Recognises a VCF or BCF file by its name, ignoring case: {@code .bcf} is {@link #BCF}; {@code .vcf} and
     * {@code .gvcf} are {@link #VCF}; {@code .vcf} or {@code .gvcf} followed by one of
     * {@link FileExtensions#BLOCK_COMPRESSED} ({@code .gz}, {@code .gzip}, {@code .bgz}, {@code .bgzf}) is
     * {@link #BLOCK_COMPRESSED_VCF}. Nothing else is recognised, including {@code .bcf.gz} and a name that ends in a
     * compression suffix alone.
     *
     * @param fileName a file name; only its ending is examined, so a path or URI path works too
     * @return the type the name indicates, or empty if it indicates none
     */
    public static Optional<VariantFileType> fromFileName(final String fileName) {
        ValidationUtils.nonNull(fileName, "fileName");
        final String name = fileName.toLowerCase(Locale.ROOT);
        for (final String compression : FileExtensions.BLOCK_COMPRESSED) {
            if (name.endsWith(compression)) {
                return hasVcfExtension(name.substring(0, name.length() - compression.length()))
                        ? Optional.of(BLOCK_COMPRESSED_VCF)
                        : Optional.empty();
            }
        }
        if (name.endsWith(FileExtensions.BCF)) {
            return Optional.of(BCF);
        }
        return hasVcfExtension(name) ? Optional.of(VCF) : Optional.empty();
    }

    /**
     * Recognises a VCF or BCF file by the name of the file a path points to, as {@link #fromFileName(String)} does.
     *
     * @param path a path
     * @return the type the path's file name indicates, or empty if it indicates none or the path has no file name
     */
    public static Optional<VariantFileType> fromFileName(final Path path) {
        ValidationUtils.nonNull(path, "path");
        final Path fileName = path.getFileName();
        return fileName == null ? Optional.empty() : fromFileName(fileName.toString());
    }

    private static boolean hasVcfExtension(final String lowerCaseName) {
        return lowerCaseName.endsWith(FileExtensions.VCF) || lowerCaseName.endsWith(FileExtensions.GVCF);
    }

    /**
     * Recognises a VCF or BCF file by its first bytes. Gzip-compressed bytes (BGZF or plain gzip) are inflated as
     * far as the prefix allows; a prefix that ends inside the compressed data is expected. Then {@code BCF} with
     * major version 2 is {@link #BCF}, whatever the minor version, and a first line that {@link VCFCodec} accepts
     * ({@code ##fileformat=VCFv3} or {@code VCFv4}, or {@code ##format=VCRv3.2}) is {@link #VCF}, or
     * {@link #BLOCK_COMPRESSED_VCF} if it was compressed, plain gzip included.
     *
     * @param prefix the first bytes of a file, of any length
     * @return the type the bytes indicate, or empty if they indicate none, are too few to tell, or are corrupt
     */
    public static Optional<VariantFileType> fromContents(final byte[] prefix) {
        ValidationUtils.nonNull(prefix, "prefix");
        final boolean compressed = isGzip(prefix);
        final byte[] start = decompressedStart(prefix, MAGIC_LENGTH);
        if (isBcf2(start)) {
            return Optional.of(BCF);
        }
        for (final byte[] magic : VCF_MAGIC_LINES) {
            if (startsWith(start, magic)) {
                return Optional.of(compressed ? BLOCK_COMPRESSED_VCF : VCF);
            }
        }
        return Optional.empty();
    }

    /**
     * Recognises a VCF or BCF stream by at most its first {@link #PROBE_LENGTH} bytes, as {@link #fromContents(byte[])}
     * does, and, unless reading fails, leaves the stream where it was.
     *
     * @param stream a stream that supports {@link InputStream#mark(int)}, such as a {@link java.io.BufferedInputStream}
     * @return the type the stream's next bytes indicate, or empty if they indicate none
     * @throws IllegalArgumentException if the stream does not support mark
     * @throws RuntimeIOException if reading or resetting the stream fails
     */
    public static Optional<VariantFileType> fromContents(final InputStream stream) {
        ValidationUtils.nonNull(stream, "stream");
        ValidationUtils.validateArg(
                stream.markSupported(), "the stream must support mark so that it can be reset after it is examined");
        stream.mark(PROBE_LENGTH);
        try {
            final byte[] prefix = readPrefix(stream, MAGIC_LENGTH);
            stream.reset();
            return fromContents(prefix);
        } catch (final IOException e) {
            throw new RuntimeIOException("Could not read the start of a stream to tell whether it is VCF or BCF", e);
        }
    }

    /**
     * Recognises a VCF or BCF stream by at most {@link #PROBE_LENGTH} bytes from its current position, as
     * {@link #fromContents(byte[])} does, and, unless reading fails, seeks back to that position.
     *
     * @param stream a seekable stream
     * @return the type the bytes from the stream's position indicate, or empty if they indicate none
     * @throws RuntimeIOException if reading or seeking the stream fails
     */
    public static Optional<VariantFileType> fromContents(final SeekableStream stream) {
        ValidationUtils.nonNull(stream, "stream");
        try {
            final long position = stream.position();
            final byte[] prefix = readPrefix(stream, MAGIC_LENGTH);
            stream.seek(position);
            return fromContents(prefix);
        } catch (final IOException e) {
            throw new RuntimeIOException("Could not read the start of " + stream.getSource(), e);
        }
    }

    /**
     * Recognises a VCF or BCF file by at most its first {@link #PROBE_LENGTH} bytes, as {@link #fromContents(byte[])}
     * does.
     *
     * @param path a readable file
     * @return the type the file's contents indicate, or empty if they indicate none
     * @throws RuntimeIOException if the file cannot be read
     */
    public static Optional<VariantFileType> fromContents(final Path path) {
        ValidationUtils.nonNull(path, "path");
        try {
            return fromContents(readPrefix(path, MAGIC_LENGTH));
        } catch (final IOException e) {
            throw new RuntimeIOException("Could not read the start of " + path.toUri(), e);
        }
    }

    /**
     * @param decompressedLength how many bytes of data, after any decompression, the prefix must hold if the file
     *     has them
     * @return the first bytes of a file, as many as {@link #readPrefix(InputStream, int)} reads
     */
    static byte[] readPrefix(final Path path, final int decompressedLength) throws IOException {
        try (InputStream in = Files.newInputStream(path)) {
            return readPrefix(in, decompressedLength);
        }
    }

    /**
     * Reads until the bytes read hold {@code decompressedLength} bytes of data after any decompression,
     * {@link #PROBE_LENGTH} bytes have been read, or the stream ends; a read that returns no bytes is taken as the
     * end too. Stopping as soon as there is enough keeps a pipe whose writer is slow, or still open, from blocking
     * the reader.
     */
    private static byte[] readPrefix(final InputStream in, final int decompressedLength) throws IOException {
        final byte[] buffer = new byte[PROBE_LENGTH];
        int length = 0;
        int n;
        while (length < buffer.length && (n = in.read(buffer, length, buffer.length - length)) > 0) {
            length += n;
            if (decompressedStart(Arrays.copyOf(buffer, length), decompressedLength).length == decompressedLength) {
                break;
            }
        }
        return Arrays.copyOf(buffer, length);
    }

    /**
     * The start of the data a file's first bytes hold: inflated if they are gzip-compressed, BGZF included, or as
     * they are otherwise.
     *
     * @param prefix the first bytes of a file
     * @param maxLength the most bytes wanted
     * @return at most {@code maxLength} bytes; fewer if the data ends, the prefix ends inside the compressed data, or
     *     the compressed data is corrupt
     */
    static byte[] decompressedStart(final byte[] prefix, final int maxLength) {
        if (!isGzip(prefix)) {
            return Arrays.copyOf(prefix, Math.min(prefix.length, maxLength));
        }
        // GzipCompressorInputStream rather than IOUtil.openGzipOrBgzfStream, which hands BGZF to a reader that
        // inflates a whole block or nothing: this inflates as far as the prefix goes, and BGZF is gzip to it.
        // Byte at a time, since its bulk read discards what it inflated when the input runs out.
        final byte[] start = new byte[maxLength];
        int length = 0;
        try (InputStream in = GzipCompressorInputStream.builder()
                .setInputStream(new ByteArrayInputStream(prefix))
                .setDecompressConcatenated(true)
                .get()) {
            int b;
            while (length < maxLength && (b = in.read()) >= 0) {
                start[length++] = (byte) b;
            }
        } catch (final IOException e) {
            // a prefix that ends inside the compressed data, or data that is corrupt: keep what was inflated
        }
        return Arrays.copyOf(start, length);
    }

    private static boolean isGzip(final byte[] prefix) {
        return IOUtil.isGZIPInputStream(new ByteArrayInputStream(prefix));
    }

    private static boolean isBcf2(final byte[] start) {
        return start.length >= BCF2Codec.SIZEOF_BCF_HEADER
                && startsWith(start, BCFVersion.MAGIC_HEADER_START)
                && start[BCFVersion.MAGIC_HEADER_START.length] == 2;
    }

    private static boolean startsWith(final byte[] bytes, final byte[] start) {
        return bytes.length >= start.length && Arrays.equals(bytes, 0, start.length, start, 0, start.length);
    }
}
