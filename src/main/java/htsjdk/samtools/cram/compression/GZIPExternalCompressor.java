/*
 * Copyright (c) 2019 The Broad Institute
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package htsjdk.samtools.cram.compression;

import htsjdk.samtools.Defaults;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import htsjdk.utils.ValidationUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class GZIPExternalCompressor extends ExternalCompressor {
    // The writeCompressionLevel value is used for write only. When this class is used to read
    // (uncompress) data read from a CRAM block, writeCompressionLevel does not necessarily reflect
    // the level that was used to compress that data (the compression level that  used to create a
    // gzip compressed stream is not recovered from Slice block itself).
    private final int writeCompressionLevel;

    public GZIPExternalCompressor() {
        this(Defaults.COMPRESSION_LEVEL);
    }

    public GZIPExternalCompressor(final int compressionLevel) {
        super(BlockCompressionMethod.GZIP);
        ValidationUtils.validateArg(compressionLevel >= Deflater.NO_COMPRESSION  && compressionLevel <= Deflater.BEST_COMPRESSION,
                String.format("Invalid compression level (%d) requested for CRAM GZIP compression", compressionLevel));
        this.writeCompressionLevel = compressionLevel;
    }

    /**
     * @return the gzip compression level used by this compressor's compress method
     */
    public int getWriteCompressionLevel() { return writeCompressionLevel; }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final GZIPOutputStream gos = new GZIPOutputStream(byteArrayOutputStream) {
            {
                def.setLevel(writeCompressionLevel);
            }
        }) {
            IOUtil.copyStream(new ByteArrayInputStream(data), gos);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }

        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public byte[] uncompress(byte[] data) {
        // Note that when uncompressing data that was retrieved from a (slice) data block
        // embedded in a CRAM stream, the writeCompressionLevel value is not recovered
        // from the block, and therefore does not necessarily reflect the value that was used
        // to compress the data that is now being uncompressed
        try (final GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(data))) {
            return InputStreamUtils.readFully(gzipInputStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", super.toString(), writeCompressionLevel);
    }

}
