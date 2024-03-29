/*
 * The MIT License
 *
 * Copyright (c) 2009 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package htsjdk.samtools.util;

import htsjdk.samtools.Defaults;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets; 

/**
 * Implementation of LineReader that is a thin wrapper around BufferedReader.  On Linux, this is faster
 * than AsciiLineReaderImpl.  If you use AsciiLineReader rather than this class, it will detect the OS
 * and delegate to the preferred implementation.
 * 
 * @author alecw@broadinstitute.org
 */
public class BufferedLineReader extends LineNumberReader implements LineReader {
    
    private static final float MAX_BYTES_PER_CHAR_UTF8 = StandardCharsets.UTF_8.newEncoder().maxBytesPerChar();

    private static class StringBackedInputStream extends InputStream {
        private int idx = 0;
        private final String str;
        private final int len;

        StringBackedInputStream(String str) {
            this.str = str;
            this.len = str.length();
        }

        @Override
        public int read() throws IOException {
            if(idx >= len) {
                return -1;
            }
            return (int) str.charAt(idx++);
        }
    }

    public BufferedLineReader(final InputStream is) {
        this(is, Defaults.NON_ZERO_BUFFER_SIZE);
    }

    public BufferedLineReader(final InputStream is, final int bufferSize) {
        super(new InputStreamReader(is, Charset.forName("UTF-8")), bufferSize);
    }

    /**
     * Returns a {@link BufferedLineReader} that gets its input from a String. No charset conversion
     * is necessary because the String is in unicode.
     */
    public static BufferedLineReader fromString(final String s) {
        final InputStream is;
        // Developer Note: if the string is long enough such that the # of bytes needed exceeds the
        // maximum array size, we need to use a custom string-backed input stream, versus a byte array
        // backed input stream.  The # of bytes needed is the length of the string times the number
        // bytes per character to store in UTF8.
        if (s.length() * MAX_BYTES_PER_CHAR_UTF8 >= Integer.MAX_VALUE) {
            is = new StringBackedInputStream(s);
        }
        else {
            is = new ByteArrayInputStream(s.getBytes());
        }
        return new BufferedLineReader(is);
    }

    /**
     * Read a line and remove the line terminator
     *
     * @return the line read, or null if EOF has been reached.
     */
    @Override
    public String readLine() {
        try {
            return super.readLine();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    /**
     * Non-destructive one-character look-ahead.
     *
     * @return If not eof, the next character that would be read.  If eof, {@value EOF_VALUE}.
     */
    @Override
    public int peek() {
        try {
            mark(2); // Two characters required here as the next read will collapse \r\n to a single \n
            final int ret = read();
            reset();
            return ret;
        } catch (IOException e) {
                throw new RuntimeIOException(e);
        }
    }

    @Override
    public void close() {
        try {
            super.close();
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
