/*******************************************************************************
 * Copyright 2013 EMBL-EBI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package htsjdk.samtools.cram.io;

import java.io.IOException;
import java.io.InputStream;

/**
 * An input stream that counts the bytes read from it.
 */
public class CountingInputStream extends InputStream {
    private final InputStream delegate;
    private long count = 0;

    public CountingInputStream(InputStream inputStream) {
        delegate = inputStream;
    }

    @Override
    public int read() throws IOException {
        count++;
        return delegate.read();
    }

    public int read(@SuppressWarnings("NullableProblems") byte[] b) throws IOException {
        int read = delegate.read(b);
        count += read;
        return read;
    }

    public int read(@SuppressWarnings("NullableProblems") byte[] b, int off, int len) throws IOException {
        int read = delegate.read(b, off, len);
        count += read;
        return read;
    }

    public long skip(long n) throws IOException {
        long skipped = delegate.skip(n);
        count += skipped;
        return skipped;
    }

    public int available() throws IOException {
        return delegate.available();
    }

    public void close() throws IOException {
        if (delegate != null)
            delegate.close();
    }

    public void mark(int readLimit) {
        delegate.mark(readLimit);
    }

    public void reset() throws IOException {
        delegate.reset();
        count = 0;
    }

    public boolean markSupported() {
        return delegate.markSupported();
    }

    public long getCount() {
        return count;
    }
}
