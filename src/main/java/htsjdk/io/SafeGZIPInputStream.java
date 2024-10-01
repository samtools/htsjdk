package htsjdk.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

/**
 * GZIP input stream which doesn't suffer from the available() bug.
 *
 * This will be deprecated and removed after moving to java 23.
 */
public class SafeGZIPInputStream extends GZIPInputStream {

    public SafeGZIPInputStream(InputStream in, int size) throws IOException {
        super(new SafeGzipInput(in), size);
    }

    public SafeGZIPInputStream(InputStream in) throws IOException {
        super(new SafeGzipInput(in));
    }

    private static class SafeGzipInput extends InputStream {
        private final InputStream wrappedStream;

        private SafeGzipInput(InputStream wrappedStream) {
            this.wrappedStream = wrappedStream.markSupported() ? wrappedStream : new BufferedInputStream(wrappedStream);
        }

        @Override
        public int read() throws IOException {
            return wrappedStream.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return wrappedStream.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return wrappedStream.read(b, off, len);
        }

        @Override
        public byte[] readAllBytes() throws IOException {
            return wrappedStream.readAllBytes();
        }

        @Override
        public byte[] readNBytes(int len) throws IOException {
            return wrappedStream.readNBytes(len);
        }

        @Override
        public int readNBytes(byte[] b, int off, int len) throws IOException {
            return wrappedStream.readNBytes(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return wrappedStream.skip(n);
        }

        @Override
        public void skipNBytes(long n) throws IOException {
            wrappedStream.skipNBytes(n);
        }

        @Override
        public int available() throws IOException {
            int available = wrappedStream.available();
            if(available <= 0){
                wrappedStream.mark(2);
                available = read() >= 0 ? 1 : 0;
                wrappedStream.reset();
            }
            return available;
        }

        @Override
        public void close() throws IOException {
            wrappedStream.close();
        }

        @Override
        public void mark(int readlimit) {
            wrappedStream.mark(readlimit);
        }

        @Override
        public void reset() throws IOException {
            wrappedStream.reset();
        }

        @Override
        public boolean markSupported() {
            return wrappedStream.markSupported();
        }

        @Override
        public long transferTo(OutputStream out) throws IOException {
            return wrappedStream.transferTo(out);
        }

    }
}
