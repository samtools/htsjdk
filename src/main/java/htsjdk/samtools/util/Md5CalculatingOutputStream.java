/*
 * The MIT License
 *
 * Copyright (c) 2010 The Broad Institute
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

import htsjdk.samtools.SAMException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Class to generate an MD5 string for a file as it is being read
 *
 * @author ktibbett@broadinstitue.org
 */
public class Md5CalculatingOutputStream extends OutputStream {

    private final OutputStream os;
    private final MessageDigest md5;
    private final Path digestFile;
    private String hash;

    /**
     * Constructor that takes in the OutputStream that we are wrapping
     * and creates the MD5 MessageDigest
     */
    public Md5CalculatingOutputStream(OutputStream os, Path digestFile) {
        super();
        this.hash = null;
        this.os = os;
        this.digestFile = digestFile;

        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.reset();
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public Md5CalculatingOutputStream(OutputStream os, File digestFile) {
        this(os, IOUtil.toPath(digestFile));
    }

    @Override
    public void write(int b) throws IOException {
        md5.update((byte)b);
        os.write(b);
    }

    @Override
    public void write(byte[] b) throws IOException {
        md5.update(b);
        os.write(b);
    }


    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        md5.update(b, off, len);
        os.write(b, off, len);
    }

    public String md5() {
        if(hash == null) {
            throw new SAMException("Attempting to access md5 digest before the entire file is written!  Call close first.");
        }

        return hash;
    }

    private String makeHash() {
        if(hash == null) {
            hash = new BigInteger(1, md5.digest()).toString(16);
            if (hash.length() != 32) {
                final String zeros = "00000000000000000000000000000000";
                hash = zeros.substring(0, 32 - hash.length()) + hash;
            }
            return hash;
        } else {
            throw new SAMException("Calling close on Md5CalculatingOutputStream twice!");
        }
    }

    @Override
    public void close() throws IOException {
        os.close();
        makeHash();

        if(digestFile != null) {
            BufferedWriter writer = Files.newBufferedWriter(digestFile);
            writer.write(hash);
            writer.close();
        }
    }

    // Pass-through method
    @Override
    public void flush() throws IOException { os.flush(); }

}
