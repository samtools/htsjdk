package htsjdk.variant.utils;

import htsjdk.samtools.SamStreams;
import htsjdk.samtools.cram.io.InputStreamUtils;
import htsjdk.samtools.seekablestream.SeekableStream;
import htsjdk.samtools.util.IOUtil;
import htsjdk.tribble.Feature;
import htsjdk.tribble.FeatureCodec;
import htsjdk.tribble.FeatureCodecHeader;
import htsjdk.tribble.TribbleException;
import htsjdk.variant.bcf2.BCF2Codec;
import htsjdk.variant.bcf2.BCFVersion;
import htsjdk.variant.vcf.VCFCodec;
import htsjdk.variant.vcf.VCFHeader;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;

/**
 * Utility class to read a VCF header without being told beforehand whether the input is VCF or BCF.
 */
public final class VCFHeaderReader {

    private VCFHeaderReader(){}

    /**
     * Read a VCF header from a stream that may be a VCF file (possibly gzip or block compressed) or a BCF file.
     * After successfully reading a header the stream is positioned immediately after the header, otherwise, if an
     * exception is thrown, the state of the stream is undefined.
     *
     * @param in the stream to read the header from
     * @return the VCF header read from the stream
     * @throws TribbleException.InvalidHeader if the header in the file is invalid
     * @throws IOException if an IOException occurs while reading the header
     */
    public static VCFHeader readHeaderFrom(final SeekableStream in) throws IOException {
        final long initialPos = in.position();
        byte[] magicBytes = InputStreamUtils.readFully(bufferAndDecompressIfNecessary(in), BCFVersion.MAGIC_HEADER_START.length);
        in.seek(initialPos);
        if (magicBytes[0] == '#') { // VCF
            return readHeaderFrom(in, new VCFCodec());
        } else if (Arrays.equals(magicBytes, BCFVersion.MAGIC_HEADER_START)) {
            return readHeaderFrom(in, new BCF2Codec());
        }
        throw new TribbleException.InvalidHeader("No VCF header found in " + in.getSource());
    }

    private static InputStream bufferAndDecompressIfNecessary(final InputStream in) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(in);
        // despite the name, SamStreams.isGzippedSAMFile looks for any gzipped stream (including block compressed)
        return IOUtil.isGZIPInputStream(bis) ? new GZIPInputStream(bis) : bis;
    }

    private static <FEATURE_TYPE extends Feature, SOURCE> VCFHeader readHeaderFrom(final InputStream in, final FeatureCodec<FEATURE_TYPE, SOURCE> featureCodec) throws IOException {
        InputStream is = bufferAndDecompressIfNecessary(in);
        FeatureCodecHeader headerCodec = featureCodec.readHeader(featureCodec.makeSourceFromStream(is));
        return (VCFHeader) headerCodec.getHeaderValue();
    }
}
