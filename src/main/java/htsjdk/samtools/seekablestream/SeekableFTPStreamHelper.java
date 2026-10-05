/*
 * Copyright (c) 2007-2011 by The Broad Institute of MIT and Harvard.  All Rights Reserved.
 *
 * This software is licensed under the terms of the GNU Lesser General Public License (LGPL),
 * Version 2.1 which is available at http://www.opensource.org/licenses/lgpl-2.1.php.
 *
 * THE SOFTWARE IS PROVIDED "AS IS." THE BROAD AND MIT MAKE NO REPRESENTATIONS OR
 * WARRANTES OF ANY KIND CONCERNING THE SOFTWARE, EXPRESS OR IMPLIED, INCLUDING,
 * WITHOUT LIMITATION, WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE, NONINFRINGEMENT, OR THE ABSENCE OF LATENT OR OTHER DEFECTS, WHETHER
 * OR NOT DISCOVERABLE.  IN NO EVENT SHALL THE BROAD OR MIT, OR THEIR RESPECTIVE
 * TRUSTEES, DIRECTORS, OFFICERS, EMPLOYEES, AND AFFILIATES BE LIABLE FOR ANY DAMAGES
 * OF ANY KIND, INCLUDING, WITHOUT LIMITATION, INCIDENTAL OR CONSEQUENTIAL DAMAGES,
 * ECONOMIC DAMAGES OR INJURY TO PROPERTY AND LOST PROFITS, REGARDLESS OF WHETHER
 * THE BROAD OR MIT SHALL BE ADVISED, SHALL HAVE OTHER REASON TO KNOW, OR IN FACT
 * SHALL KNOW OF THE POSSIBILITY OF THE FOREGOING.
 */

package htsjdk.samtools.seekablestream;

import htsjdk.samtools.util.ftp.FTPClient;
import htsjdk.samtools.util.ftp.FTPReply;
import htsjdk.samtools.util.ftp.FTPUtils;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author jrobinso
 * @date Oct 27, 2010
 */
public class SeekableFTPStreamHelper {

    private long position = 0;
    private long contentLength = -1;
    private final URL url;
    private final String path;
    FTPClient ftp = null;
    private UserPasswordInput userPasswordInput;

    SeekableFTPStreamHelper(URL url, UserPasswordInput userPasswordInput) throws IOException {
        this.url = url;
        this.path = FTPUtils.getDecodedPath(url);
        this.userPasswordInput = userPasswordInput;
        ftp = FTPUtils.connect(url, userPasswordInput);

        ftp.binary();
        FTPReply reply = ftp.size(path);
        if (reply.isSuccess()) {
            contentLength = Long.parseLong(reply.getReplyString());
        }
    }

    public void seek(long position) {
        this.position = position;
    }

    public long position() {
        return position;
    }

    public boolean eof() throws IOException {
        return false;
    }

    public long length() {
        return contentLength;
    }

    public long skip(long n) throws IOException {
        long bytesToSkip = n;
        position += bytesToSkip;
        if (ftp != null) {
            ftp.setRestPosition(position);
        }
        return bytesToSkip;
    }

    public int read(byte[] buffer, int offset, int len) throws IOException {

        if (ftp == null) {
            ftp = FTPUtils.connect(url, userPasswordInput);
        }

        if (offset < 0 || len < 0 || (offset + len) > buffer.length) {
            throw new IndexOutOfBoundsException();
        }

        if (len == 0) {
            return 0;
        }

        int n = 0;
        try {

            FTPReply reply = ftp.pasv();
            if (!reply.isPositiveCompletion()) {
                throw new IOException(failure("PASV", reply));
            }

            // If we are positioned at or beyond the EOF return -1
            if (contentLength >= 0 && position >= contentLength) {
                return -1;
            }

            if (position > 0) ftp.setRestPosition(position);

            // a server that can't send the file (550 for a missing one) replies to REST or RETR with an error and may
            // leave the data connection open, so reading it would hang or look like an empty file
            reply = ftp.retr(path);
            if (!reply.isPositivePreliminary()) {
                throw new IOException(failure("RETR", reply));
            }

            InputStream is = ftp.getDataStream();

            while (n < len) {
                int count = is.read(buffer, offset + n, len - n);
                if (count < 0) {
                    if (n == 0) {
                        return -1;
                    } else {
                        break;
                    }
                }
                n += count;
            }

            position += n;

            return n;
        } catch (EOFException e) {
            if (n < 0) {
                return -1;
            } else {
                position += n;
                return n;
            }
        } finally {
            // ALWAYS close ftp connection,  this is more robust than trying to resue them,
            // and we don't want open connections hanging about
            ftp.disconnect();
            ftp = null;
        }
    }

    private void reconnect() throws IOException {
        if (ftp != null) {
            ftp.disconnect();
        }
        ftp = FTPUtils.connect(url, userPasswordInput);
    }

    private String failure(final String command, final FTPReply reply) throws IOException {
        return command + " of " + path + " on " + url.getHost() + " failed: " + reply.getCode() + " "
                + reply.getReplyString();
    }

    public void close() throws IOException {
        if (ftp != null) {
            ftp.disconnect();
            ftp = null;
        }
    }

    public int read() throws IOException {
        throw new UnsupportedOperationException("read() is not supported on SeekableHTTPStream.  Must read in blocks.");
    }
}
