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
package htsjdk.samtools.seekablestream;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import no.uio.ifi.crypt4gh.stream.Crypt4GHSeekableStreamInternal;

/**
 *
 * @author asenf
 */
public class SeekableCrypt4GHStream extends SeekableStream {

    private final Crypt4GHSeekableStreamInternal wrappedStreamInternal;

    public SeekableCrypt4GHStream(SeekableStream sourceStream, PrivateKey pK) throws IOException, GeneralSecurityException {
        this.wrappedStreamInternal = new Crypt4GHSeekableStreamInternal(sourceStream, pK);
    }
    
    @Override
    public long length() {
        return this.wrappedStreamInternal.length();
    }

    @Override
    public long position() throws IOException {
        return this.wrappedStreamInternal.position();
    }

    @Override
    public void seek(long position) throws IOException {
        this.wrappedStreamInternal.seek(position);
    }

    @Override
    public int read(byte[] bytes, int i, int i1) throws IOException {
        return this.wrappedStreamInternal.read();
    }

    @Override
    public void close() throws IOException {
        this.wrappedStreamInternal.close();
    }

    @Override
    public boolean eof() throws IOException {
        return this.wrappedStreamInternal.eof();
    }

    @Override
    public String getSource() {
        return this.wrappedStreamInternal.getSource();
    }

    @Override
    public int read() throws IOException {
        return this.wrappedStreamInternal.read();
    }
    
}
