package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.ExternalCompressor;
import htsjdk.samtools.cram.structure.CRAMCodecModelContext;
import htsjdk.samtools.cram.structure.block.BlockCompressionMethod;

/**
 * External compressor wrapper for the FQZComp quality score codec. Bridges the
 * {@link ExternalCompressor} interface with the FQZComp encoder/decoder, extracting
 * per-record metadata from the {@link CRAMCodecModelContext} that the encoder needs
 * to determine record boundaries.
 */
public class FQZCompExternalCompressor extends ExternalCompressor {

    private final FQZCompEncode fqzCompEncoder;
    private final FQZCompDecode fqzCompDecoder;

    public FQZCompExternalCompressor(
            final FQZCompEncode fqzCompEncoder,
            final FQZCompDecode fqzCompDecoder) {
        super(BlockCompressionMethod.FQZCOMP);
        this.fqzCompEncoder = fqzCompEncoder;
        this.fqzCompDecoder = fqzCompDecoder;
    }

    /**
     * Compress quality score data using FQZComp. Per-record quality score lengths are extracted
     * from the context model; if unavailable or mismatched, the data is treated as a single record.
     * Empty data is returned as-is.
     *
     * @param data concatenated quality scores
     * @param contextModel context containing per-record lengths and flags, or null
     * @return compressed data
     */
    @Override
    public byte[] compress(byte[] data, final CRAMCodecModelContext contextModel) {
        if (data.length == 0) {
            return data;
        }

        // FQZComp iterates quality bytes and advances its record index when it exhausts each
        // record's length.  Records with zero quality bytes (missing quality scores) are never
        // visited, so we must filter them out of the lengths array.
        final int[] lengths = filterNonZero(getRecordLengths(data.length, contextModel));
        return CompressionUtils.toByteArray(
                fqzCompEncoder.compress(CompressionUtils.wrap(data), lengths));
    }

    /**
     * Determine per-record quality score lengths. Uses the context model when available and
     * the lengths sum to the data size (indicating all records have preserved quality scores).
     * Falls back to treating the entire data as a single record otherwise.
     */
    private static int[] getRecordLengths(final int dataLength, final CRAMCodecModelContext contextModel) {
        if (contextModel != null && contextModel.getQualityScoreLengths() != null) {
            final int[] lengths = contextModel.getQualityScoreLengths();
            int sum = 0;
            for (final int len : lengths) {
                sum += len;
            }
            if (sum == dataLength) {
                return lengths;
            }
        }
        // Fall back: treat the entire data as a single record
        return new int[]{dataLength};
    }

    /** Returns a new array containing only the non-zero values from the input. */
    private static int[] filterNonZero(final int[] values) {
        int count = 0;
        for (final int v : values) {
            if (v != 0) count++;
        }
        if (count == values.length) return values;
        final int[] result = new int[count];
        int j = 0;
        for (final int v : values) {
            if (v != 0) result[j++] = v;
        }
        return result;
    }

    /** Returns elements from {@code values} at positions where {@code filter} is non-zero. */
    private static int[] filterByNonZero(final int[] values, final int[] filter) {
        int count = 0;
        for (final int f : filter) {
            if (f != 0) count++;
        }
        if (count == filter.length) return values;
        final int[] result = new int[count];
        int j = 0;
        for (int i = 0; i < filter.length; i++) {
            if (filter[i] != 0) result[j++] = values[i];
        }
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public byte[] uncompress(byte[] data) {
        if (data.length == 0) {
            return data;
        }
        return fqzCompDecoder.uncompress(CompressionUtils.wrap(data)).array();
    }

}
