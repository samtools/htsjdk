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
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.GzipCodec;
import htsjdk.utils.ValidationUtils;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

public final class GZIPExternalCompressor extends ExternalCompressor {
    private final int writeCompressionLevel;
    private final GzipCodec codec;

    public GZIPExternalCompressor() {
        this(Defaults.COMPRESSION_LEVEL);
    }

    public GZIPExternalCompressor(final int compressionLevel) {
        this(compressionLevel, Deflater.DEFAULT_STRATEGY);
    }

    public GZIPExternalCompressor(final int compressionLevel, final int deflateStrategy) {
        super(BlockCompressionMethod.GZIP);
        ValidationUtils.validateArg(compressionLevel >= Deflater.NO_COMPRESSION && compressionLevel <= Deflater.BEST_COMPRESSION,
                String.format("Invalid compression level (%d) requested for CRAM GZIP compression", compressionLevel));
        this.writeCompressionLevel = compressionLevel;
        this.codec = new GzipCodec(compressionLevel, deflateStrategy);
    }

    /** @return the gzip compression level used by this compressor's compress method */
    public int getWriteCompressionLevel() { return writeCompressionLevel; }

    @Override
    public byte[] compress(final byte[] data, final CRAMCodecModelContext unused_contextModel) {
        final ByteBuffer compressed = codec.compress(ByteBuffer.wrap(data));
        final byte[] result = new byte[compressed.remaining()];
        compressed.get(result);
        return result;
    }

    @Override
    public byte[] uncompress(byte[] data) {
        final ByteBuffer decompressed = codec.decompress(ByteBuffer.wrap(data));
        final byte[] result = new byte[decompressed.remaining()];
        decompressed.get(result);
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (!super.equals(o)) return false;
        return writeCompressionLevel == ((GZIPExternalCompressor) o).writeCompressionLevel;
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + writeCompressionLevel;
    }

    @Override
    public String toString() {
        return String.format("%s(%d)", super.toString(), writeCompressionLevel);
    }
}
