package htsjdk.variant.utils;

import htsjdk.samtools.SamStreams;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.BlockCompressedInputStream;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.tribble.readers.AsciiLineReader;
import htsjdk.tribble.readers.AsciiLineReaderIterator;
import htsjdk.tribble.readers.PositionalBufferedStream;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/**
 * Utility class to read a VCF header without being told beforehand whether the input is VCF or BCF.
 */
public final class VCFHeaderReader {
    /**
     * Read a VCF header from a stream assuming first that it is a VCF file (possibly gzip or block compressed),
     * and falling back to trying BCF if reading VCF fails. After successfully reading a header the stream is
     * positioned immediately after the header, otherwise, if an exception is thrown, the state of the stream is
     * undefined.
     *
     * @param in the stream to read the header from
     * @return the VCF header read from the stream
     * @throws IOException if no VCF header is found
     */
    public static VCFHeader readHeaderFrom(final SeekableStream in) throws IOException {
        FeatureCodecHeader headerCodec;
        final long initialPos = in.position();
        try {
            BufferedInputStream bis = new BufferedInputStream(in);
            // despite the name, SamStreams.isGzippedSAMFile looks for any gzipped stream (including block compressed)
            InputStream is = SamStreams.isGzippedSAMFile(bis) ? new GZIPInputStream(bis) : bis;
            headerCodec = new VCFCodec().readHeader(new AsciiLineReaderIterator(AsciiLineReader.from(is)));
        } catch (TribbleException e) {
            in.seek(initialPos);
            InputStream bin = new BufferedInputStream(in);
            if (BlockCompressedInputStream.isValidFile(bin)) {
                bin = new BlockCompressedInputStream(bin);
            }
            try {
                headerCodec = new BCF2Codec().readHeader(new PositionalBufferedStream(bin));
            } catch (TribbleException e2) {
                throw new IOException("No VCF header found", e2);
            }
        }
        return (VCFHeader) headerCodec.getHeaderValue();
    }
}
