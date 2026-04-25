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
 * <p>The encoder analyzes quality data to select context model parameters, matching the adaptive
 * parameter training in htslib's {@code fqz_pick_parameters} ({@code fqzcomp_qual.c}). Features:
 * <ul>
 *   <li>Quality map for sparse symbol sets (e.g., NovaSeq 4-bin quality)</li>
 *   <li>Delta table with approximate-sqrt mapping for running quality difference context</li>
 *   <li>Symbol-count-based tuning (NovaSeq, HiSeqX, small datasets)</li>
 *   <li>Duplicate detection for consecutive identical quality strings</li>
 *   <li>Quality reversal for reverse-complemented reads (GFLAG_DO_REV)</li>
 *   <li>Fixed-length read optimization</li>
 * </ul>
 *
 * @see FQZCompDecode
 * @see <a href="https://samtools.github.io/hts-specs/CRAMv3.pdf">CRAM 3.1 specification</a>
 */
public class FQZCompEncode {

    private static final int FQZ_VERSION = 5;
    private static final int CTX_SIZE = 1 << 16;
    private static final int NUMBER_OF_SYMBOLS = 256;

    // Reusable buffer to avoid per-call allocation of quality data array
    private byte[] pooledQualData;

    // Pooled model arrays to avoid 65K+ allocations per compress() call.
    // qualityModels are keyed by maxSymbol+1 (numSymbols) since that varies per block.
    private ByteModel[] pooledQualityModels;
    private int pooledQualityModelsNumSymbols;
    private final ByteModel[] pooledLengthModels = new ByteModel[4];
    private ByteModel pooledReverseModel;
    private ByteModel pooledDupModel;

    private ByteModel[] getOrCreateQualityModels(final int numSymbols) {
        if (pooledQualityModels == null || pooledQualityModelsNumSymbols != numSymbols) {
            pooledQualityModels = new ByteModel[CTX_SIZE];
            for (int i = 0; i < CTX_SIZE; i++) {
                pooledQualityModels[i] = new ByteModel(numSymbols);
            }
            pooledQualityModelsNumSymbols = numSymbols;
        } else {
            for (int i = 0; i < CTX_SIZE; i++) {
                pooledQualityModels[i].reset();
            }
        }
        return pooledQualityModels;
    }

    private ByteModel[] getOrCreateLengthModels() {
        for (int i = 0; i < 4; i++) {
            if (pooledLengthModels[i] == null) {
                pooledLengthModels[i] = new ByteModel(NUMBER_OF_SYMBOLS);
            } else {
                pooledLengthModels[i].reset();
            }
        }
        return pooledLengthModels;
    }

    private ByteModel getOrCreateReverseModel() {
        if (pooledReverseModel == null) {
            pooledReverseModel = new ByteModel(2);
        } else {
            pooledReverseModel.reset();
        }
        return pooledReverseModel;
    }

    private ByteModel getOrCreateDupModel() {
        if (pooledDupModel == null) {
            pooledDupModel = new ByteModel(2);
        } else {
            pooledDupModel.reset();
        }
        return pooledDupModel;
    }

    // Parameter flag masks (same as in FQZParam)
    private static final int PFLAG_DO_DEDUP  = 0x02;
    private static final int PFLAG_DO_LEN    = 0x04;
    private static final int PFLAG_HAVE_QMAP = 0x10;
    private static final int PFLAG_HAVE_PTAB = 0x20;
    private static final int PFLAG_HAVE_DTAB = 0x40;

    // Global flag masks (same as in FQZGlobalFlags)
    private static final int GFLAG_DO_REV = 0x04;

    // Approximate sqrt table for delta context mapping (from htslib fqzcomp_qual.c)
    private static final int[] DSQR = {
        0, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3, 3,
        4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5,
        5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6,
        6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7
    };

    /** BAM flag for reverse-complemented read */
    private static final int BAM_FREVERSE = 0x10;

    /**
     * Compress concatenated quality scores using the FQZComp codec.
     *
     * @param inBuffer concatenated quality scores for all records (position to limit is compressed)
     * @param recordLengths per-record quality score lengths; sum must equal inBuffer.remaining()
     * @return a rewound ByteBuffer containing the compressed data
     */
    public ByteBuffer compress(final ByteBuffer inBuffer, final int[] recordLengths) {
        return compress(inBuffer, recordLengths, null);
    }

    /**
     * Compress concatenated quality scores using the FQZComp codec with per-record BAM flags.
     *
     * @param inBuffer concatenated quality scores for all records (position to limit is compressed)
     * @param recordLengths per-record quality score lengths; sum must equal inBuffer.remaining()
     * @param bamFlags per-record BAM flags (for reverse-complement and dedup detection), or null
     * @return a rewound ByteBuffer containing the compressed data
     */
    public ByteBuffer compress(final ByteBuffer inBuffer, final int[] recordLengths, final int[] bamFlags) {
        final int uncompressedSize = inBuffer.remaining();
        if (uncompressedSize == 0) {
            return CompressionUtils.allocateByteBuffer(0);
        }

        // Build parameters from data analysis
        final EncoderParams params = buildParameters(inBuffer, recordLengths, bamFlags, uncompressedSize);

        // Allocate output buffer (worst case: header + data with some growth)
        final int worstCase = (int) ((uncompressedSize + recordLengths.length * 5) * 1.1) + 10000;
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(worstCase);
        outBuffer.order(ByteOrder.LITTLE_ENDIAN);

        // Write header: uncompressed size + parameters
        CompressionUtils.writeUint7(uncompressedSize, outBuffer);
        writeParameters(outBuffer, params);

        // Pre-shift ptab and dtab values to eliminate shifts in hot loop (matches htslib optimization)
        final int[] ptab = params.ptab != null ? params.ptab.clone() : null;
        if (ptab != null) {
            for (int i = 0; i < ptab.length; i++) {
                ptab[i] <<= params.ploc;
            }
        }
        final int[] dtab = params.dtab != null ? params.dtab.clone() : null;
        if (dtab != null) {
            for (int i = 0; i < dtab.length; i++) {
                dtab[i] <<= params.dloc;
            }
        }

        // Pre-process: copy quality data into reusable buffer, reverse if needed
        if (pooledQualData == null || pooledQualData.length < uncompressedSize) {
            pooledQualData = new byte[uncompressedSize];
        }
        final byte[] qualData = pooledQualData;
        inBuffer.get(qualData, 0, uncompressedSize);
        if (params.doReverse) {
            reverseQualitiesInPlace(qualData, recordLengths, bamFlags);
        }

        // Initialize models (pooled to avoid 65K+ allocations per call) and range coder
        final ByteModel[] qualityModels = getOrCreateQualityModels(params.maxSymbol + 1);
        final ByteModel[] lengthModels = getOrCreateLengthModels();
        final ByteModel reverseModel = getOrCreateReverseModel();
        final ByteModel dupModel = getOrCreateDupModel();
        final RangeCoder rangeCoder = new RangeCoder();

        // Set up range coder to write to byte[] starting after the header
        final int headerSize = outBuffer.position();
        final byte[] outArray = outBuffer.array();
        rangeCoder.setOutput(outArray, headerSize);

        // Encoding loop
        int last = 0;
        int qctx = 0;
        int delta = 0;
        int prevQ = 0;
        int recIdx = 0;
        int basesRemaining = 0;
        boolean firstLen = true;
        int lastLen = 0;

        for (int i = 0; i < uncompressedSize; i++) {
            if (basesRemaining == 0) {
                // Start of new record
                final int len = recordLengths[recIdx];

                // Encode length (unless fixed-length and not first record)
                if (!params.fixedLen || firstLen) {
                    lengthModels[0].modelEncode(rangeCoder, (len) & 0xFF);
                    lengthModels[1].modelEncode(rangeCoder, (len >> 8) & 0xFF);
                    lengthModels[2].modelEncode(rangeCoder, (len >> 16) & 0xFF);
                    lengthModels[3].modelEncode(rangeCoder, (len >> 24) & 0xFF);
                    firstLen = false;
                }

                // Encode reverse flag
                if (params.doReverse) {
                    final boolean isReverse = bamFlags != null && (bamFlags[recIdx] & BAM_FREVERSE) != 0;
                    reverseModel.modelEncode(rangeCoder, isReverse ? 1 : 0);
                }

                // Encode duplicate flag
                if (params.doDedup) {
                    final boolean isDup = i > 0 && len == lastLen &&
                            arraysEqual(qualData, i - lastLen, qualData, i, len);
                    dupModel.modelEncode(rangeCoder, isDup ? 1 : 0);
                    if (isDup) {
                        lastLen = len;
                        i += len - 1; // skip quality encoding, -1 because loop increments
                        recIdx++;
                        basesRemaining = 0;
                        continue;
                    }
                }

                lastLen = len;
                basesRemaining = len;
                last = 0;
                qctx = 0;
                delta = 0;
                prevQ = 0;
                recIdx++;
            }

            // Map quality through qmap and encode
            final int rawQ = qualData[i] & 0xFF;
            final int q = params.qmap[rawQ];
            qualityModels[last].modelEncode(rangeCoder, q);

            // Update context
            qctx = (qctx << params.qshift) + params.qtab[q];
            last = (qctx & params.qmask) << params.qloc;
            if (ptab != null) {
                last += ptab[Math.min(basesRemaining, 1023)];
            }
            if (dtab != null) {
                last += dtab[Math.min(delta, 255)];
                delta += (prevQ != q) ? 1 : 0;
                prevQ = q;
            }
            last &= (CTX_SIZE - 1);

            basesRemaining--;
        }

        rangeCoder.rangeEncodeEnd();
        // Update the ByteBuffer position to match what the range coder wrote
        outBuffer.position(rangeCoder.getOutputPosition());

        // Post-process: undo reversal to restore input data
        if (params.doReverse) {
            reverseQualitiesInPlace(qualData, recordLengths, bamFlags);
        }

        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
        return outBuffer;
    }

    /**
     * Build encoder parameters by analyzing the quality data. Adapts context model parameters
     * based on symbol count, data size, and record characteristics, matching htslib's
     * {@code fqz_pick_parameters}.
     */
    private EncoderParams buildParameters(final ByteBuffer inBuffer, final int[] recordLengths,
                                          final int[] bamFlags, final int dataSize) {
        final EncoderParams params = new EncoderParams();

        // Scan for symbol statistics
        final int[] qhist = new int[NUMBER_OF_SYMBOLS];
        int maxSymbol = 0;
        int nsym = 0;
        for (int i = inBuffer.position(); i < inBuffer.limit(); i++) {
            final int q = inBuffer.get(i) & 0xFF;
            qhist[q]++;
            if (q > maxSymbol) maxSymbol = q;
        }
        for (int i = 0; i <= maxSymbol; i++) {
            if (qhist[i] > 0) nsym++;
        }

        // Start with strategy 0 defaults (basic, matching htslib strat_opts[0])
        int qbits = 10, qshift = 5, pbits = 4, dbits = 2, dshift = 1;
        int qloc = 0, sloc = 14, ploc = 10, dloc = 14;
        int pshift = -1; // auto

        // Symbol-count-based tuning (htslib lines 817-835)
        if (nsym <= 4) {
            // NovaSeq-style binned quality (4 values)
            qshift = 2;
            if (dataSize < 5_000_000) {
                pbits = 2;
                pshift = 5;
            }
        } else if (nsym <= 8) {
            // HiSeqX-style quality (8 values)
            qbits = Math.min(qbits, 9);
            qshift = 3;
            if (dataSize < 5_000_000) {
                qbits = 6;
            }
        }

        // Small dataset adjustment (htslib line 832-835)
        if (dataSize < 300_000) {
            qbits = qshift;
            dbits = 2;
        }

        // Auto-compute pshift from read length (htslib line 814-815)
        if (pshift < 0) {
            pshift = recordLengths.length > 0 ?
                    Math.max(0, (int) (Math.log((double) recordLengths[0] / (1 << pbits)) / Math.log(2) + 0.5)) : 0;
        }

        params.qbits = qbits;
        params.qshift = qshift;
        params.qmask = (1 << qbits) - 1;
        params.qloc = qloc;
        params.sloc = sloc;
        params.ploc = ploc;
        params.dloc = dloc;

        // Quality map for sparse symbol sets (htslib line 800, 849-863)
        final boolean storeQmap = (nsym <= 8 && nsym * 2 < maxSymbol);
        params.qmap = new int[NUMBER_OF_SYMBOLS];
        if (storeQmap) {
            // Forward map: qmap[originalQ] = encodedIndex
            // Reverse map: reverseQmap[encodedIndex] = originalQ (for serialization)
            params.reverseQmap = new int[nsym];
            int j = 0;
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                if (qhist[i] > 0) {
                    params.qmap[i] = j;
                    params.reverseQmap[j] = i;
                    j++;
                } else {
                    params.qmap[i] = 0;
                }
            }
            params.maxSymbol = nsym;
        } else {
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                params.qmap[i] = i;
            }
            params.maxSymbol = maxSymbol;
        }

        // Quality context table (identity - htslib line 867-873)
        params.qtab = new int[NUMBER_OF_SYMBOLS];
        for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
            params.qtab[i] = i;
        }

        // Position table (htslib line 877-885)
        if (pbits > 0) {
            params.ptab = new int[1024];
            for (int i = 0; i < 1024; i++) {
                params.ptab[i] = Math.min((1 << pbits) - 1, i >> pshift);
            }
        }

        // Delta table with approximate-sqrt mapping (htslib line 845-890)
        if (dbits > 0) {
            // Clamp dsqr values to fit in dbits
            final int[] dsqr = DSQR.clone();
            for (int i = 0; i < dsqr.length; i++) {
                if (dsqr[i] > (1 << dbits) - 1) {
                    dsqr[i] = (1 << dbits) - 1;
                }
            }
            params.dtab = new int[NUMBER_OF_SYMBOLS];
            for (int i = 0; i < NUMBER_OF_SYMBOLS; i++) {
                params.dtab[i] = dsqr[Math.min(dsqr.length - 1, i >> dshift)];
            }
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

        // Duplicate detection (htslib: enabled when dup rate > 1/500)
        if (bamFlags != null) {
            int dupCount = 0;
            int offset = 0;
            int prevLen = 0;
            final byte[] data = new byte[inBuffer.remaining()];
            final int pos = inBuffer.position();
            inBuffer.get(data);
            inBuffer.position(pos); // restore position
            for (int rec = 0; rec < recordLengths.length; rec++) {
                final int len = recordLengths[rec];
                if (rec > 0 && len == prevLen && offset >= prevLen &&
                        arraysEqual(data, offset - prevLen, data, offset, len)) {
                    dupCount++;
                }
                offset += len;
                prevLen = len;
            }
            params.doDedup = ((recordLengths.length + 1) / (dupCount + 1) < 500);
        }

        // Reverse flag for CRAM 3.x (htslib line 763-764)
        params.doReverse = (bamFlags != null);

        // Parameter flags
        params.pflags =
                (params.ptab != null ? PFLAG_HAVE_PTAB : 0) |
                (params.dtab != null ? PFLAG_HAVE_DTAB : 0) |
                (params.fixedLen ? PFLAG_DO_LEN : 0) |
                (params.doDedup ? PFLAG_DO_DEDUP : 0) |
                (storeQmap ? PFLAG_HAVE_QMAP : 0);

        // Global flags
        params.gflags = params.doReverse ? GFLAG_DO_REV : 0;

        return params;
    }

    /**
     * Serialize FQZComp parameters to the output buffer.
     */
    private void writeParameters(final ByteBuffer outBuffer, final EncoderParams params) {
        // Version
        outBuffer.put((byte) FQZ_VERSION);

        // Global flags
        outBuffer.put((byte) params.gflags);

        // Single parameter block: context (little-endian u16)
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

        // Quality map (PFLAG_HAVE_QMAP): write the original quality values for each encoded index.
        // The decoder reads maxSymbol bytes into qualityMap[]; qualityMap[encoded_index] = original_quality.
        if ((params.pflags & PFLAG_HAVE_QMAP) != 0) {
            for (int i = 0; i < params.maxSymbol; i++) {
                outBuffer.put((byte) params.reverseQmap[i]);
            }
        }

        // Position table (PFLAG_HAVE_PTAB)
        if (params.ptab != null) {
            storeArray(outBuffer, params.ptab, 1024);
        }

        // Delta table (PFLAG_HAVE_DTAB)
        if (params.dtab != null) {
            storeArray(outBuffer, params.dtab, NUMBER_OF_SYMBOLS);
        }
    }

    /**
     * Serialize an array using the two-level run-length encoding used by FQZComp for tables.
     * This is the inverse of {@link FQZUtils#readArray(ByteBuffer, int[], int)}.
     *
     * @param outBuffer output buffer to write to
     * @param array the array to serialize
     * @param size number of elements to serialize
     */
    public static void storeArray(final ByteBuffer outBuffer, final int[] array, final int size) {
        final byte[] tmp = new byte[4096];
        int k = 0;
        int j = 0;
        for (int i = 0; i < size; j++) {
            int runLen = i;
            while (i < size && array[i] == j) {
                i++;
            }
            runLen = i - runLen;

            int r;
            do {
                r = Math.min(255, runLen);
                tmp[k++] = (byte) r;
                runLen -= r;
            } while (r == 255);
        }

        int last = -1;
        for (int i = 0; i < k; ) {
            outBuffer.put(tmp[i]);
            if ((tmp[i] & 0xFF) == last) {
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

    /**
     * Reverse quality arrays in place for reverse-complemented reads.
     * Called before encoding (to match decoder's post-decode reversal).
     */
    private static void reverseQualitiesInPlace(final byte[] data, final int[] lengths, final int[] bamFlags) {
        if (bamFlags == null) return;
        int offset = 0;
        for (int rec = 0; rec < lengths.length; rec++) {
            if ((bamFlags[rec] & BAM_FREVERSE) != 0) {
                int lo = offset;
                int hi = offset + lengths[rec] - 1;
                while (lo < hi) {
                    final byte tmp = data[lo];
                    data[lo] = data[hi];
                    data[hi] = tmp;
                    lo++;
                    hi--;
                }
            }
            offset += lengths[rec];
        }
    }

    /** Compare sub-arrays for equality. */
    private static boolean arraysEqual(final byte[] a, final int aOff, final byte[] b, final int bOff, final int len) {
        for (int i = 0; i < len; i++) {
            if (a[aOff + i] != b[bOff + i]) return false;
        }
        return true;
    }

    /** Internal parameter holder for the encoder. */
    private static class EncoderParams {
        int maxSymbol;
        int qbits, qshift, qmask;
        int qloc, sloc, ploc, dloc;
        int[] qmap;         // forward map: qmap[originalQ] = encodedIndex
        int[] reverseQmap;  // reverse map: reverseQmap[encodedIndex] = originalQ (for serialization)
        int[] qtab;
        int[] ptab;
        int[] dtab;
        boolean fixedLen;
        boolean doDedup;
        boolean doReverse;
        int pflags;
        int gflags;
    }
}
