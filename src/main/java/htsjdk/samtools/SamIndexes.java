package htsjdk.samtools;

import htsjdk.samtools.cram.CRAIIndex;
import htsjdk.samtools.seekablestream.SeekableBufferedStream;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The index formats of BAM and CRAM files, with their file name extensions and magic numbers.
 *
 * <p>The methods that open an index as a stream of BAI bytes are deprecated: they convert a CRAI to a BAI but not a
 * CSI, and each says exactly what it returns. {@link #getSAMIndexTypeFromStream} identifies all three formats.
 */
public enum SamIndexes {
    BAI(FileExtensions.BAI_INDEX, "BAI\1".getBytes()),
    // CRAI is gzipped text, so it's magic is same as {@link java.util.zip.GZIPInputStream.GZIP_MAGIC}
    CRAI(FileExtensions.CRAM_INDEX, new byte[] {(byte) 0x1f, (byte) 0x8b}),
    CSI(FileExtensions.CSI, "CSI\1".getBytes());

    public final String fileNameSuffix;
    public final byte[] magic;

    SamIndexes(final String fileNameSuffix, final byte[] magic) {
        this.fileNameSuffix = fileNameSuffix;
        this.magic = magic;
    }

    /**
     * Opens an index chosen by the extension of its file name. A BAI is returned as it is and a CRAI is converted to a
     * BAI; a CSI is returned as the file's bytes, BGZF-compressed and not converted.
     *
     * @param path file whose name ends {@code .bai}, {@code .crai} or {@code .csi}, in any case
     * @param dictionary sequence dictionary of the indexed file, needed only to convert a CRAI
     * @return the index's bytes, or null if the name has none of those extensions
     * @deprecated ask a {@link SamReader} for its index ({@link SamReader.Indexing#getHtsIndex()}), or read one on its
     *     own with {@link htsjdk.index.FileBackedBinningIndex} (BAI or CSI) or {@link CRAMCRAIIndexer#readIndex} (CRAI)
     */
    @Deprecated
    public static InputStream openIndexFileAsBaiOrNull(final Path path, final SAMSequenceDictionary dictionary)
            throws IOException {
        // Resolve via the path's own filesystem rather than java.net.URL.openStream(), which is not
        // NIO-SPI aware and would fail for non-default-filesystem paths (e.g. jimfs, S3, GCS).
        final String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(BAI.fileNameSuffix.toLowerCase())) {
            return Files.newInputStream(path);
        }
        if (name.endsWith(CRAI.fileNameSuffix.toLowerCase())) {
            return CRAIIndex.openCraiFileAsBaiStream(Files.newInputStream(path), dictionary);
        }
        if (name.endsWith(CSI.fileNameSuffix.toLowerCase())) {
            return Files.newInputStream(path);
        }
        return null;
    }

    /**
     * Opens an index chosen by the extension of its URL's path, as {@link #openIndexFileAsBaiOrNull} does for a file: a
     * BAI as it is, a CRAI converted to a BAI, and a CSI as its bytes, BGZF-compressed and not converted.
     *
     * @param dictionary sequence dictionary of the indexed file, needed only to convert a CRAI
     * @return the index's bytes, or null if the URL's path has none of those extensions
     * @deprecated ask a {@link SamReader} for its index ({@link SamReader.Indexing#getHtsIndex()}), or read one on its
     *     own with {@link htsjdk.index.FileBackedBinningIndex} (BAI or CSI) or {@link CRAMCRAIIndexer#readIndex} (CRAI)
     */
    @Deprecated
    public static InputStream openIndexUrlAsBaiOrNull(final URL url, final SAMSequenceDictionary dictionary)
            throws IOException {
        if (url.getFile().toLowerCase().endsWith(BAI.fileNameSuffix.toLowerCase())) {
            return url.openStream();
        }
        if (url.getFile().toLowerCase().endsWith(CRAI.fileNameSuffix.toLowerCase())) {
            return CRAIIndex.openCraiFileAsBaiStream(url.openStream(), dictionary);
        }
        if (url.getFile().toLowerCase().endsWith(CSI.fileNameSuffix.toLowerCase())) {
            return url.openStream();
        }

        return null;
    }

    /**
     * Identifies an index by its first bytes. A BAI is returned as it is. Anything that starts with the gzip magic
     * number is taken for a CRAI and converted to a BAI, so a CSI as stored, which is BGZF-compressed, fails as a
     * malformed CRAI; only a CSI whose bytes are already decompressed is recognised, and it is returned as it is.
     *
     * @param dictionary sequence dictionary of the indexed file, needed only to convert a CRAI
     * @return the index's bytes, or null if they start with none of those magic numbers
     * @deprecated ask a {@link SamReader} for its index ({@link SamReader.Indexing#getHtsIndex()}), or read one on its
     *     own with {@link htsjdk.index.FileBackedBinningIndex} (BAI or CSI) or {@link CRAMCRAIIndexer#readIndex} (CRAI)
     */
    @Deprecated
    public static InputStream asBaiStreamOrNull(final InputStream inputStream, final SAMSequenceDictionary dictionary)
            throws IOException {
        final BufferedInputStream bis = new BufferedInputStream(inputStream);
        bis.mark(BAI.magic.length);
        if (doesStreamStartWith(bis, BAI.magic)) {
            bis.reset();
            return bis;
        } else {
            bis.reset();
        }

        bis.mark(CRAI.magic.length);
        if (doesStreamStartWith(bis, CRAI.magic)) {
            bis.reset();
            return CRAIIndex.openCraiFileAsBaiStream(bis, dictionary);
        } else {
            bis.reset();
        }

        bis.mark(CSI.magic.length);
        if (doesStreamStartWith(bis, CSI.magic)) {
            bis.reset();
            return bis;
        } else {
            bis.reset();
        }

        return null;
    }

    /**
     * Identifies an index by its first bytes, as {@link #asBaiStreamOrNull} does: a BAI is returned as it is, anything
     * that starts with the gzip magic number is taken for a CRAI and converted to a BAI (so a CSI as stored fails as a
     * malformed CRAI), and a CSI whose bytes are already decompressed is returned as it is.
     *
     * @param dictionary sequence dictionary of the indexed file, needed only to convert a CRAI
     * @return the index's bytes, or null if they start with none of those magic numbers
     * @deprecated ask a {@link SamReader} for its index ({@link SamReader.Indexing#getHtsIndex()}), or read one on its
     *     own with {@link htsjdk.index.FileBackedBinningIndex} (BAI or CSI) or {@link CRAMCRAIIndexer#readIndex} (CRAI)
     */
    @Deprecated
    public static SeekableStream asBaiSeekableStreamOrNull(
            final SeekableStream inputStream, final SAMSequenceDictionary dictionary) throws IOException {
        final SeekableBufferedStream bis = new SeekableBufferedStream(inputStream);
        bis.seek(0);
        if (doesStreamStartWith(bis, BAI.magic)) {
            bis.seek(0);
            return bis;
        }

        bis.seek(0);
        if (doesStreamStartWith(bis, CRAI.magic)) {
            bis.seek(0);
            return CRAIIndex.openCraiFileAsBaiStream(bis, dictionary);
        } else {
            bis.reset();
        }

        bis.seek(0);
        if (doesStreamStartWith(bis, CSI.magic)) {
            bis.seek(0);
            return bis;
        }

        return null;
    }

    public static SamIndexes getSAMIndexTypeFromStream(final SeekableStream seekableStream) {
        SamIndexes indexType = null;
        try {
            seekableStream.seek(0);
            final SeekableBufferedStream bss = new SeekableBufferedStream(seekableStream);

            if (IOUtil.isGZIPInputStream(bss)) {
                bss.seek(0);
                if (doesStreamStartWith(IOUtil.openGzipOrBgzfStream(bss), CSI.magic)) {
                    indexType = CSI;
                } else {
                    // the CRAI format has no signature bytes, so optimistically call it CRAI
                    // if its gzipped but not CSI
                    indexType = CRAI;
                }
            } else {
                bss.seek(0);
                if (doesStreamStartWith(bss, BAI.magic)) {
                    indexType = BAI;
                }
            }
            seekableStream.seek(0);
        } catch (final IOException e) {
            throw new RuntimeIOException("Error interrogating index input stream", e);
        }

        return indexType;
    }

    private static boolean doesStreamStartWith(final InputStream is, final byte[] bytes) throws IOException {
        for (final byte b : bytes) {
            if (is.read() != (0xFF & b)) {
                return false;
            }
        }
        return true;
    }
}
