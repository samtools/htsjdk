package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.RuntimeIOException;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class LZMAExternalCompressor extends ExternalCompressor {

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
