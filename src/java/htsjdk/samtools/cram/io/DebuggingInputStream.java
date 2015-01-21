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
import java.util.Arrays;

public class DebuggingInputStream extends InputStream {
    private InputStream delegate;

    public DebuggingInputStream(InputStream delegate) {
        super();
        this.delegate = delegate;
    }

    public int read() throws IOException {
        int value = delegate.read();
        System.err.println(value);
        return value;
    }

    public int read(byte[] b) throws IOException {
        int value = delegate.read(b);
        System.err.println(Arrays.toString(b));
        return value;
    }

    public int read(byte[] b, int off, int len) throws IOException {
        int value = delegate.read(b, off, len);
        System.err.println(Arrays.toString(Arrays.copyOfRange(b, off, off + len)));
        return value;
    }

    public long skip(long n) throws IOException {
        long value = delegate.skip(n);
        System.err.println("Skipping: " + n + ", " + value);
        return value;
    }

    public int available() throws IOException {
        int value = delegate.available();
        System.err.println("Availble: " + value);
        return value;
    }

    public void close() throws IOException {
        System.err.println("Close");
        delegate.close();
    }

    public void mark(int readlimit) {
        System.err.println("Mark: " + readlimit);
        delegate.mark(readlimit);
    }

    public void reset() throws IOException {
        System.err.println("Reset");
        delegate.reset();
    }

    public boolean markSupported() {
        boolean value = delegate.markSupported();
        System.err.println("Mark supported: " + value);
        return value;
    }

}
