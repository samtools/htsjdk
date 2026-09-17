package htsjdk.samtools.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/** Builders for in-memory gzip and BGZF test inputs. */
public final class GzipTestStreams {
    private GzipTestStreams() {}

    /**
     * Compresses each string as its own gzip member and concatenates the members, which is what
     * {@code cat a.gz b.gz} produces.
     */
    public static byte[] multiMemberGzip(final String... members) throws IOException {
        final byte[][] memberBytes = new byte[members.length][];
        for (int i = 0; i < members.length; i++) {
            memberBytes[i] = members[i].getBytes(StandardCharsets.UTF_8);
        }
        return multiMemberGzip(memberBytes);
    }

    /** As {@link #multiMemberGzip(String...)}, for binary content. */
    public static byte[] multiMemberGzip(final byte[]... members) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] member : members) {
            try (GZIPOutputStream gzip = new GZIPOutputStream(out)) {
                gzip.write(member);
            }
        }
        return out.toByteArray();
    }

    /** Compresses each string as its own BGZF block, followed by the BGZF terminator block. */
    public static byte[] multiBlockBgzf(final String... blocks) throws IOException {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (BlockCompressedOutputStream bgzf = new BlockCompressedOutputStream(out, (Path) null)) {
            for (final String block : blocks) {
                bgzf.write(block.getBytes(StandardCharsets.UTF_8));
                bgzf.flush();
            }
        }
        return out.toByteArray();
    }

    /**
     * A stream over {@code bytes} that behaves like a slow pipe: {@link InputStream#available()} always
     * returns 0 and each read delivers a single byte.
     *
     * <p>Both halves are needed to reliably reproduce JDK-7036144. {@link java.util.zip.GZIPInputStream}
     * only consults {@code available()} when a member ends at the end of the bytes it has buffered, so
     * single-byte reads make every member boundary an opportunity to stop early.
     */
    public static InputStream asSlowPipe(final byte[] bytes) {
        return new FilterInputStream(new ByteArrayInputStream(bytes)) {
            @Override
            public int available() {
                return 0;
            }

            @Override
            public int read(final byte[] buffer, final int offset, final int length) throws IOException {
                return super.read(buffer, offset, Math.min(length, 1));
            }
        };
    }
}
