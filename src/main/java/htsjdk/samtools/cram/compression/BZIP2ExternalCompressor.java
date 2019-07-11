package htsjdk.samtools.cram.compression;

import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;
import htsjdk.samtools.util.IOUtil;
import htsjdk.samtools.util.RuntimeIOException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class BZIP2ExternalCompressor extends ExternalCompressor {

    public BZIP2ExternalCompressor() {
        super(BlockCompressionMethod.BZIP2);
    }

    @Override
    public byte[] compress(final byte[] data) {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (final BZip2CompressorOutputStream bos = new BZip2CompressorOutputStream(byteArrayOutputStream)) {
            IOUtil.copyStream(new ByteArrayInputStream(data), bos);
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public byte[] uncompress(byte[] data) {
        try (final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(data)) {
            return InputStreamUtils.readFully(new BZip2CompressorInputStream(byteArrayInputStream));
        } catch (final IOException e) {
            throw new RuntimeIOException(e);
        }
    }
}
