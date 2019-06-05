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

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.RuntimeIOException;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public final class LZMAExternalCompressor extends ExternalCompressor {

    public LZMAExternalCompressor() {
        super(BlockCompressionMethod.LZMA);
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream(data.length * 2);
        try (final XZCompressorOutputStream xzCompressorOutputStream = new XZCompressorOutputStream(byteArrayOutputStream)) {
            xzCompressorOutputStream.write(data);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public byte[] uncompress(byte[] data) {
        try (final XZCompressorInputStream xzCompressorInputStream = new XZCompressorInputStream(new ByteArrayInputStream(data))) {
            return InputStreamUtils.readFully(xzCompressorInputStream);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }

}
