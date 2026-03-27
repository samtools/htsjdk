package htsjdk.samtools.cram.compression.fqzcomp;

import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.range.ByteModel;
import htsjdk.samtools.cram.compression.range.RangeCoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Encoder for the CRAM 3.1 FQZComp quality score compression codec. Uses an adaptive arithmetic
 * (range) coder with a 16-bit context model built from quality history, position within read,
 * and optionally delta and selector information.
 *
 * <p>The encoder needs per-record metadata (quality score lengths and flags) in addition to the
 * raw concatenated quality byte stream. This metadata is provided via the {@code recordLengths}
 * parameter to {@link #compress(ByteBuffer, int[])}.
 *
 * <p>This implementation starts with a simple single-parameter-block strategy (matching the noodles
 * reference implementation), using position context and optionally fixed-length optimization.
 * More sophisticated strategies (quality maps, delta tables, dedup, selectors) can be added later.
 *
 * @see FQZCompDecode
 * @see <a href="https://samtools.github.io/hts-specs/CRAMv3.pdf">CRAM 3.1 specification</a>
 */
public class FQZCompEncode {

    private static final int FQZ_VERSION = 5;
    private static final int CTX_SIZE = 1 << 16;
    private static final int NUMBER_OF_SYMBOLS = 256;

    // Parameter flag masks (same as in FQZParam, but we need them here for building)
    private static final int PFLAG_DO_LEN  = 0x04;
    private static final int PFLAG_HAVE_PTAB = 0x20;

    /**
     * Compress concatenated quality scores using the FQZComp codec.
     *
     * @param inBuffer concatenated quality scores for all records (position to limit is compressed)
     * @param recordLengths per-record quality score lengths; sum must equal inBuffer.remaining()
     * @return a rewound ByteBuffer containing the compressed data
     */
    public ByteBuffer compress(final ByteBuffer inBuffer, final int[] recordLengths) {
        final int uncompressedSize = inBuffer.remaining();
        if (uncompressedSize == 0) {
            return CompressionUtils.allocateByteBuffer(0);
        }

        // Build parameters from data analysis
        final EncoderParams params = buildParameters(inBuffer, recordLengths);

        // Allocate output buffer (worst case: header + data with some growth)
        final int worstCase = (int) ((uncompressedSize + recordLengths.length * 5) * 1.1) + 10000;
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(worstCase);
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Write header: uncompressed size + parameters
        CompressionUtils.writeUint7(uncompressedSize, outBuffer);
        writeParameters(outBuffer, params);

        // Pre-shift ptab values to eliminate shifts in hot loop (matches htslib optimization)
        final int[] ptab = params.ptab.clone();
        for (int i = 0; i < ptab.length; i++) {
            ptab[i] <<= params.ploc;
        }

        // Initialize models and range coder
        final ByteModel[] qualityModels = new ByteModel[CTX_SIZE];
        for (int i = 0; i < CTX_SIZE; i++) {
            qualityModels[i] = new ByteModel(params.maxSymbol + 1);
        }
        final ByteModel[] lengthModels = new ByteModel[4];
        for (int i = 0; i < 4; i++) {
            lengthModels[i] = new ByteModel(NUMBER_OF_SYMBOLS);
        }

        final RangeCoder rangeCoder = new RangeCoder();

        // Encoding loop
        int last = 0;
        int qctx = 0;
        int recIdx = 0;
        int basesRemaining = 0;
        boolean firstLen = true;

        for (int i = 0; i < uncompressedSize; i++) {
            if (basesRemaining == 0) {
                // Start of new record
                final int len = recordLengths[recIdx++];

                // Encode length (unless fixed-length and not first record)
                if (!params.fixedLen || firstLen) {
                    lengthModels[0].modelEncode(outBuffer, rangeCoder, (len) & 0xFF);
                    lengthModels[1].modelEncode(outBuffer, rangeCoder, (len >> 8) & 0xFF);
                    lengthModels[2].modelEncode(outBuffer, rangeCoder, (len >> 16) & 0xFF);
                    lengthModels[3].modelEncode(outBuffer, rangeCoder, (len >> 24) & 0xFF);
                    firstLen = false;
                }

                basesRemaining = len;
                last = 0; // context = param.context = 0
                qctx = 0;
            }

            // Encode quality symbol
            final int q = inBuffer.get() & 0xFF;
            qualityModels[last].modelEncode(outBuffer, rangeCoder, q);

            // Update context
            qctx = (qctx << params.qshift) + params.qtab[q];
            last = (qctx & params.qmask) << params.qloc;
            last += ptab[Math.min(basesRemaining, 1023)];
            last &= (CTX_SIZE - 1);

            basesRemaining--;
        }

        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    /**
     * Build encoder parameters by analyzing the quality data. Uses a simple single-parameter-block
     * strategy: position table context + quality history context, no quality map, no delta, no dedup.
     */
    private EncoderParams buildParameters(final ByteBuffer inBuffer, final int[] recordLengths) {
        final EncoderParams params = new EncoderParams();

        // Scan for max symbol
        int maxSymbol = 0;
        for (int i = inBuffer.position(); i < inBuffer.limit(); i++) {
            final int q = inBuffer.get(i) & 0xFF;
            if (q > maxSymbol) maxSymbol = q;
        }
        params.maxSymbol = maxSymbol;

        // Context parameters (matching noodles simple strategy)
        params.qshift = 5;
        params.qbits = params.qshift > 4 ? 9 : 8;
        params.qmask = (1 << params.qbits) - 1;
        params.qloc = 7;
        params.sloc = 15;
        params.ploc = 0;
        params.dloc = 15;

        // Position table
        final int pbits = 7;
        final int pshift = recordLengths.length > 0 && recordLengths[0] > 128 ? 1 : 0;
        params.ptab = new int[1024];
        for (int i = 0; i < 1024; i++) {
            params.ptab[i] = Math.min((1 << pbits) - 1, i >> pshift);
        }

        // Quality context table (identity)
        params.qtab = new int[NUMBER_OF_SYMBOLS];
        for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
            params.qtab[i] = i;
        }

        // Check for fixed-length reads
        params.fixedLen = true;
        if (recordLengths.length > 1) {
            for (int i = 1; i < recordLengths.length; i++) {
                if (recordLengths[i] != recordLengths[0]) {
                    params.fixedLen = false;
                    break;
                }
            }
        }

        // Parameter flags
        params.pflags = PFLAG_HAVE_PTAB | (params.fixedLen ? PFLAG_DO_LEN : 0);

        return params;
    }

    /**
     * Serialize FQZComp parameters to the output buffer.
     */
    private void writeParameters(final ByteBuffer outBuffer, final EncoderParams params) {
        // Version
        outBuffer.put((byte) FQZ_VERSION);

        // Global flags: no multi-param, no selector table, no reverse
        outBuffer.put((byte) 0);

        // Single parameter block
        // context (little-endian u16)
        outBuffer.put((byte) 0);
        outBuffer.put((byte) 0);

        // pflags
        outBuffer.put((byte) params.pflags);

        // max_sym
        outBuffer.put((byte) params.maxSymbol);

        // qbits(4) | qshift(4)
        outBuffer.put((byte) ((params.qbits << 4) | params.qshift));

        // qloc(4) | sloc(4)
        outBuffer.put((byte) ((params.qloc << 4) | params.sloc));

        // ploc(4) | dloc(4)
        outBuffer.put((byte) ((params.ploc << 4) | params.dloc));

        // Write position table (PFLAG_HAVE_PTAB is always set)
        storeArray(outBuffer, params.ptab, 1024);
    }

    /**
     * Serialize an array using the two-level run-length encoding used by FQZComp for tables.
     * This is the inverse of {@link FQZUtils#readArray(ByteBuffer, int[], int)}.
     *
     * <p>The array maps indices to values where values are sequential from 0. The encoding
     * stores run lengths for each successive value, then applies a second level of RLE
     * on the run-length stream itself.
     *
     * @param outBuffer output buffer to write to
     * @param array the array to serialize
     * @param size number of elements to serialize
     */
    public static void storeArray(final ByteBuffer outBuffer, final int[] array, final int size) {
        // First level: convert array values to run lengths per value
        final byte[] tmp = new byte[2048];
        int k = 0;
        int j = 0;
        for (int i = 0; i < size; j++) {
            int runLen = i;
            while (i < size && array[i] == j) {
                i++;
            }
            runLen = i - runLen;

            // Write run length, using 255 as continuation
            int r;
            do {
                r = Math.min(255, runLen);
                tmp[k++] = (byte) r;
                runLen -= r;
            } while (r == 255);
        }

        // Second level: RLE on the first-level output
        int last = -1;
        for (int i = 0; i < k; ) {
            outBuffer.put(tmp[i]);
            if ((tmp[i] & 0xFF) == last) {
                // Count consecutive duplicates
                int n = i + 1;
                while (n < k && (tmp[n] & 0xFF) == last) {
                    n++;
                }
                outBuffer.put((byte) (n - i - 1));
                i = n;
            } else {
                last = tmp[i] & 0xFF;
                i++;
            }
        }
    }

    /** Internal parameter holder for the encoder. */
    private static class EncoderParams {
        int maxSymbol;
        int qbits, qshift, qmask;
        int qloc, sloc, ploc, dloc;
        int[] qtab;
        int[] ptab;
        boolean fixedLen;
        int pflags;
    }
}
