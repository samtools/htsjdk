package htsjdk.samtools;

import htsjdk.samtools.cram.structure.CramHeader;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.samtools.util.BlockCompressedStreamConstants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Utilities related to processing of {@link java.io.InputStream}s encoding SAM data
 * 
 * @author mccowan
 */
public class SamStreams {
    private static int readBytes(final InputStream stream, final byte[] buffer, final int offset, final int length)
            throws IOException {
        int bytesRead = 0;
        while (bytesRead < length) {
            final int count = stream.read(buffer, offset + bytesRead, length - bytesRead);
            if (count <= 0) {
                break;
            }
            bytesRead += count;
        }
        return bytesRead;
    }

    public static boolean isCRAMFile(final InputStream stream) throws IOException {
        stream.mark(4);
        final int buffSize = CramHeader.MAGIC.length;
        final byte[] buffer = new byte[buffSize];
        readBytes(stream, buffer, 0, buffSize);
        stream.reset();

        return Arrays.equals(buffer, CramHeader.MAGIC);
    }

    /**
     * @param stream stream.markSupported() must be true
     * @return true if this looks like a BAM file.
     */
    public static boolean isBAMFile(final InputStream stream)
            throws IOException {
        if (!BlockCompressedInputStream.isValidFile(stream)) {
            return false;
        }
        final int buffSize = BlockCompressedStreamConstants.MAX_COMPRESSED_BLOCK_SIZE;
        stream.mark(buffSize);
        final byte[] buffer = new byte[buffSize];
        readBytes(stream, buffer, 0, buffSize);
        stream.reset();
        try(final BlockCompressedInputStream bcis = new BlockCompressedInputStream(new ByteArrayInputStream(buffer))){
            final byte[] magicBuf = new byte[4];
            final int magicLength = readBytes(bcis, magicBuf, 0, 4);
            return magicLength == BAMFileConstants.BAM_MAGIC.length && Arrays.equals(BAMFileConstants.BAM_MAGIC, magicBuf);
        }
    }

    /**
     * Checks whether the file is a gzipped sam file.  Returns true if it
     * is and false otherwise.
     */
    public static boolean isGzippedSAMFile(final InputStream stream) {
        if (!stream.markSupported()) {
            throw new IllegalArgumentException("Cannot test a stream that doesn't support marking.");
        }
        stream.mark(8000);

        try {
            final GZIPInputStream gunzip = new GZIPInputStream(stream);
            final int ch = gunzip.read();
            return true;
        } catch (final IOException ioe) {
            return false;
        } finally {
            try {
                stream.reset();
            } catch (final IOException ioe) {
                throw new IllegalStateException("Could not reset stream.");
            }
        }
    }

    // Its too expensive to examine the remote file to determine type.
    // Rely on file extension.
    public static boolean sourceLikeBam(final SeekableStream strm) {
        String source = strm.getSource();
        if (source == null) {
            // assume any stream with a null source is a BAM file
            // (https://github.com/samtools/htsjdk/issues/619)
            return true;
        }

        //Source will typically be a file path or URL
        //If it's a URL we require one of the query parameters to be a cram file
        try {
            final URL sourceURL = new URL(source);
            final String urlPath = sourceURL.getPath().toLowerCase();
            String queryParams = sourceURL.getQuery();
            if (queryParams != null) {
                queryParams = queryParams.toLowerCase();
            }
            return urlPath.endsWith(".bam") ||
                    (queryParams != null &&
                            (queryParams.endsWith(".bam") ||
                                    queryParams.contains(".bam?") ||
                                    queryParams.contains(".bam&") ||
                                    queryParams.contains(".bam%26"))
                    );
        }
        catch (MalformedURLException e) {
            source = source.toLowerCase();
            return source.endsWith(".bam") ||
                    source.contains(".bam?") ||
                    source.contains(".bam&") ||
                    source.contains(".bam%26");
        }
    }

    // Its too expensive to examine the remote file to determine type.
    // Rely on file extension.
    public static boolean sourceLikeCram(final SeekableStream strm) {
        String source = strm.getSource();
        if (source == null) {
            // sourceLikeBam assumes any stream with a null source is a BAM file
            // (https://github.com/samtools/htsjdk/issues/619); in order to not
            // propagate more chaos we return false here
            return false;
        }

        // Source will typically be a file path or URL
        // If it's a URL we require one of the query parameters to be a cram file
        try {
            final URL sourceURL = new URL(source);
            final String urlPath = sourceURL.getPath().toLowerCase();
            String queryParams = sourceURL.getQuery();
            if (queryParams != null) {
                queryParams = queryParams.toLowerCase();
            }
            return urlPath.endsWith(".cram") ||
                    (queryParams != null &&
                            (queryParams.endsWith(".cram") ||
                             queryParams.contains(".cram?") ||
                             queryParams.contains(".cram&") ||
                             queryParams.contains(".cram%26"))
                    );
        }
        catch (MalformedURLException e) {
            source = source.toLowerCase();
            return source.endsWith(".cram") ||
                    source.contains(".cram?") ||
                    source.contains(".cram&") ||
                    source.contains(".cram%26");
        }
    }

}
