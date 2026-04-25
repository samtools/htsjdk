package htsjdk.samtools.cram.compression.rans;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Decoder for the CRAM 3.1 rANSNx16 codec. All internal operations use byte[] with explicit
 * offset tracking for performance. The public API accepts and returns byte[].
 */
public class RANSNx16Decode extends RANSDecode {
    private static final int FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK = 0x01;
    private static final int RLE_META_OPTIONALLY_COMPRESSED_MASK = 0x01;

    /**
     * Uncompress a rANS Nx16 encoded byte stream. The format flags byte at the start
     * of the stream determines which transformations (PACK, RLE, STRIPE, CAT) are applied,
     * along with the order (0 or 1) and interleave width (4 or 32).
     *
     * @param input the compressed byte stream
     * @return the uncompressed data
     */
    @Override
    public byte[] uncompress(final byte[] input) {
        if (input.length == 0) {
            return new byte[0];
        }
        return uncompressInternal(input, new int[] {0}, 0);
    }

    /**
     * Internal uncompress that works on byte[] with an explicit read position.
     * Used for recursive calls (stripe, freq table decompression).
     */
    private byte[] uncompressInternal(final byte[] in, final int[] inPos, final int outSize) {
        if (inPos[0] >= in.length) {
            return new byte[0];
        }

        final int formatFlags = in[inPos[0]++] & 0xFF;
        final RANSNx16Params ransNx16Params = new RANSNx16Params(formatFlags);

        int uncompressedSize = ransNx16Params.isNosz() ? outSize : CompressionUtils.readUint7(in, inPos);

        // Stripe: decode each sub-stream and transpose
        if (ransNx16Params.isStripe()) {
            return decodeStripe(in, inPos, uncompressedSize);
        }

        // Pack metadata
        int packDataLength = 0;
        int numSymbols = 0;
        byte[] packMappingTable = null;
        if (ransNx16Params.isPack()) {
            packDataLength = uncompressedSize;
            numSymbols = in[inPos[0]++] & 0xFF;
            if (numSymbols <= 16 && numSymbols != 0) {
                packMappingTable = new byte[numSymbols];
                System.arraycopy(in, inPos[0], packMappingTable, 0, numSymbols);
                inPos[0] += numSymbols;
                uncompressedSize = CompressionUtils.readUint7(in, inPos);
            } else {
                throw new CRAMException(
                        "Bit Packing is not permitted when number of distinct symbols is greater than 16 or equal to 0. "
                                + "Number of distinct symbols: " + numSymbols);
            }
        }

        // RLE metadata
        int uncompressedRLEOutputLength = 0;
        int[] rleSymbols = null;
        byte[] rleMetaData = null;
        int[] rleMetaPos = null; // position into rleMetaData for reading run-lengths
        if (ransNx16Params.isRLE()) {
            rleSymbols = new int[Constants.NUMBER_OF_SYMBOLS];
            final int uncompressedRLEMetaDataLength = CompressionUtils.readUint7(in, inPos);
            uncompressedRLEOutputLength = uncompressedSize;
            uncompressedSize = CompressionUtils.readUint7(in, inPos);
            rleMetaPos = new int[] {0};
            rleMetaData =
                    decodeRLEMeta(in, inPos, uncompressedRLEMetaDataLength, rleSymbols, rleMetaPos, ransNx16Params);
        }

        byte[] out;

        if (ransNx16Params.isCAT()) {
            out = new byte[uncompressedSize];
            System.arraycopy(in, inPos[0], out, 0, uncompressedSize);
            inPos[0] += uncompressedSize;
        } else {
            if (uncompressedSize == 0) {
                throw new CRAMException("Unexpected uncompressed size of 0 in RANSNx16 stream");
            }
            out = new byte[uncompressedSize];
            switch (ransNx16Params.getOrder()) {
                case ZERO:
                    uncompressOrder0WayN(in, inPos, out, uncompressedSize, ransNx16Params);
                    break;
                case ONE:
                    uncompressOrder1WayN(in, inPos, out, uncompressedSize, ransNx16Params);
                    break;
                default:
                    throw new CRAMException("Unknown rANSNx16 order: " + ransNx16Params.getOrder());
            }
        }

        if (ransNx16Params.isRLE()) {
            out = decodeRLE(out, rleSymbols, rleMetaData, rleMetaPos, uncompressedRLEOutputLength);
        }

        if (ransNx16Params.isPack()) {
            // decodePack still uses ByteBuffer — bridge at this boundary
            final ByteBuffer packed = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
            final ByteBuffer unpacked =
                    CompressionUtils.decodePack(packed, packMappingTable, numSymbols, packDataLength);
            out = new byte[unpacked.remaining()];
            unpacked.get(out);
        }
        return out;
    }

    private void uncompressOrder0WayN(
            final byte[] in,
            final int[] inPos,
            final byte[] out,
            final int outSize,
            final RANSNx16Params ransNx16Params) {
        resetDecoderState();
        readFrequencyTableOrder0(in, inPos);

        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final long[] rans = new long[Nway];
        for (int r = 0; r < Nway; r++) {
            rans[r] = readLittleEndianInt(in, inPos);
        }

        final int interleaveSize = (Nway == 4) ? (outSize >> 2) : (outSize >> 5);
        int remSize = outSize - (interleaveSize * Nway);
        final int out_end = outSize - remSize;
        final byte[] reverseLookup0 = getReverseLookup()[0];
        final RANSDecodingSymbol[] syms = getDecodingSymbols()[0];

        for (int i = 0; i < out_end; i += Nway) {
            for (int r = 0; r < Nway; r++) {
                final byte decodedSymbol =
                        reverseLookup0[Utils.RANSGetCumulativeFrequency(rans[r], Constants.TOTAL_FREQ_SHIFT)];
                out[i + r] = decodedSymbol;
                rans[r] = syms[0xFF & decodedSymbol].advanceSymbolStep(rans[r], Constants.TOTAL_FREQ_SHIFT);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], in, inPos);
            }
        }

        int reverseIndex = 0;
        int outIdx = out_end;
        while (remSize > 0) {
            final byte remainingSymbol =
                    reverseLookup0[Utils.RANSGetCumulativeFrequency(rans[reverseIndex], Constants.TOTAL_FREQ_SHIFT)];
            rans[reverseIndex] =
                    syms[0xFF & remainingSymbol].advanceSymbolStep(rans[reverseIndex], Constants.TOTAL_FREQ_SHIFT);
            rans[reverseIndex] = Utils.RANSDecodeRenormalizeNx16(rans[reverseIndex], in, inPos);
            out[outIdx++] = remainingSymbol;
            remSize--;
            reverseIndex++;
        }
    }

    private void uncompressOrder1WayN(
            final byte[] in,
            final int[] inPos,
            final byte[] out,
            final int outputSize,
            final RANSNx16Params ransNx16Params) {

        final int frequencyTableFirstByte = in[inPos[0]++] & 0xFF;
        final boolean optionalCompressFlag = ((frequencyTableFirstByte & FREQ_TABLE_OPTIONALLY_COMPRESSED_MASK) != 0);

        byte[] freqTableBytes;
        int[] freqTablePos;
        if (optionalCompressFlag) {
            final int uncompressedLength = CompressionUtils.readUint7(in, inPos);
            final int compressedLength = CompressionUtils.readUint7(in, inPos);
            final byte[] compressedFreqTable = new byte[compressedLength];
            System.arraycopy(in, inPos[0], compressedFreqTable, 0, compressedLength);
            inPos[0] += compressedLength;

            // Decompress freq table using raw Order-0 (no format-flags framing)
            freqTableBytes = new byte[uncompressedLength];
            final int[] compPos = new int[] {0};
            uncompressOrder0WayN(
                    compressedFreqTable,
                    compPos,
                    freqTableBytes,
                    uncompressedLength,
                    new RANSNx16Params(~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK)));
            freqTablePos = new int[] {0};
        } else {
            freqTableBytes = in;
            freqTablePos = inPos;
        }

        // Re-initialize decoder after nested O0 call may have clobbered state
        resetDecoderState();
        final int shift = frequencyTableFirstByte >> 4;
        readFrequencyTableOrder1(freqTableBytes, freqTablePos, shift);

        // If we used a separate freqTableBytes, inPos wasn't advanced by reading the freq table.
        // If freqTableBytes == in, inPos was advanced. Nothing to do here.

        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        final long[] rans = new long[Nway];
        final int[] interleaveStreamIndex = new int[Nway];
        final int[] context = new int[Nway];
        final int interleaveSize = (Nway == 4) ? (outputSize >> 2) : (outputSize >> 5);

        for (int r = 0; r < Nway; r++) {
            rans[r] = readLittleEndianInt(in, inPos);
            interleaveStreamIndex[r] = r * interleaveSize;
            context[r] = 0;
        }

        final byte[][] reverseLookup = getReverseLookup();
        final RANSDecodingSymbol[][] syms = getDecodingSymbols();
        final int[] symbol = new int[Nway];

        while (interleaveStreamIndex[0] < interleaveSize) {
            for (int r = 0; r < Nway; r++) {
                symbol[r] = 0xFF & reverseLookup[context[r]][Utils.RANSGetCumulativeFrequency(rans[r], shift)];
                out[interleaveStreamIndex[r]] = (byte) symbol[r];
                rans[r] = syms[context[r]][symbol[r]].advanceSymbolStep(rans[r], shift);
                rans[r] = Utils.RANSDecodeRenormalizeNx16(rans[r], in, inPos);
                context[r] = symbol[r];
            }
            for (int r = 0; r < Nway; r++) {
                interleaveStreamIndex[r]++;
            }
        }

        // Remainder
        for (; interleaveStreamIndex[Nway - 1] < outputSize; interleaveStreamIndex[Nway - 1]++) {
            symbol[Nway - 1] =
                    0xFF & reverseLookup[context[Nway - 1]][Utils.RANSGetCumulativeFrequency(rans[Nway - 1], shift)];
            out[interleaveStreamIndex[Nway - 1]] = (byte) symbol[Nway - 1];
            rans[Nway - 1] = syms[context[Nway - 1]][symbol[Nway - 1]].advanceSymbolStep(rans[Nway - 1], shift);
            rans[Nway - 1] = Utils.RANSDecodeRenormalizeNx16(rans[Nway - 1], in, inPos);
            context[Nway - 1] = symbol[Nway - 1];
        }
    }

    private void readFrequencyTableOrder0(final byte[] in, final int[] inPos) {
        final int[] alphabet = readAlphabet(in, inPos);
        markRowUsed(0);
        final int[] freq = getFrequencies()[0];
        final byte[] revLookup = getReverseLookup()[0];

        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (alphabet[j] > 0) {
                freq[j] = CompressionUtils.readUint7(in, inPos);
            }
        }
        Utils.normaliseFrequenciesOrder0Shift(freq, Constants.TOTAL_FREQ_SHIFT);

        final RANSDecodingSymbol[] decodingSymbols = getDecodingSymbols()[0];
        int cumulativeFrequency = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (alphabet[j] > 0) {
                decodingSymbols[j].set(cumulativeFrequency, freq[j]);
                Arrays.fill(revLookup, cumulativeFrequency, cumulativeFrequency + freq[j], (byte) j);
                cumulativeFrequency += freq[j];
            }
        }
    }

    private void readFrequencyTableOrder1(final byte[] in, final int[] inPos, final int shift) {
        final int[][] freq = getFrequencies();
        final byte[][] revLookup = getReverseLookup();
        final RANSDecodingSymbol[][] decodingSymbols = getDecodingSymbols();
        final int[] alphabet = readAlphabet(in, inPos);

        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (alphabet[i] > 0) {
                markRowUsed(i);
                int run = 0;
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    if (alphabet[j] > 0) {
                        if (run > 0) {
                            run--;
                        } else {
                            freq[i][j] = CompressionUtils.readUint7(in, inPos);
                            if (freq[i][j] == 0) {
                                run = in[inPos[0]++] & 0xFF;
                            }
                        }
                    }
                }

                Utils.normaliseFrequenciesOrder0Shift(freq[i], shift);
                int cumulativeFreq = 0;
                for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                    decodingSymbols[i][j].set(cumulativeFreq, freq[i][j]);
                    Arrays.fill(revLookup[i], cumulativeFreq, cumulativeFreq + freq[i][j], (byte) j);
                    cumulativeFreq += freq[i][j];
                }
            }
        }
    }

    private static int[] readAlphabet(final byte[] in, final int[] inPos) {
        final int[] alphabet = new int[Constants.NUMBER_OF_SYMBOLS];
        int rle = 0;
        int symbol = in[inPos[0]++] & 0xFF;
        int lastSymbol = symbol;
        do {
            alphabet[symbol] = 1;
            if (rle != 0) {
                rle--;
                symbol++;
            } else {
                symbol = in[inPos[0]++] & 0xFF;
                if (symbol == lastSymbol + 1) {
                    rle = in[inPos[0]++] & 0xFF;
                }
            }
            lastSymbol = symbol;
        } while (symbol != 0);
        return alphabet;
    }

    /**
     * Decode RLE metadata: extract the symbol list and set rleMetaPos to the start of
     * the run-length data within the returned byte array.
     */
    private byte[] decodeRLEMeta(
            final byte[] in,
            final int[] inPos,
            final int uncompressedRLEMetaDataLength,
            final int[] rleSymbols,
            final int[] rleMetaPos,
            final RANSNx16Params ransNx16Params) {
        final byte[] uncompressedRLEMetaData;

        if ((uncompressedRLEMetaDataLength & RLE_META_OPTIONALLY_COMPRESSED_MASK) != 0) {
            final int len = (uncompressedRLEMetaDataLength - 1) / 2;
            uncompressedRLEMetaData = new byte[len];
            System.arraycopy(in, inPos[0], uncompressedRLEMetaData, 0, len);
            inPos[0] += len;
        } else {
            final int compressedLen = CompressionUtils.readUint7(in, inPos);
            final byte[] compressed = new byte[compressedLen];
            System.arraycopy(in, inPos[0], compressed, 0, compressedLen);
            inPos[0] += compressedLen;
            // Decompress using raw Order-0 (not through uncompressInternal, since the data
            // doesn't have format-flags framing — it was compressed with compressOrder0WayN directly)
            uncompressedRLEMetaData = new byte[uncompressedRLEMetaDataLength / 2];
            final int[] compPos = new int[] {0};
            uncompressOrder0WayN(
                    compressed,
                    compPos,
                    uncompressedRLEMetaData,
                    uncompressedRLEMetaDataLength / 2,
                    new RANSNx16Params(0x00 | ransNx16Params.getFormatFlags() & RANSNx16Params.N32_FLAG_MASK));
        }

        // Read symbol list from the metadata; rleMetaPos[0] advances past it
        int pos = 0;
        int numRLESymbols = uncompressedRLEMetaData[pos++] & 0xFF;
        if (numRLESymbols == 0) {
            numRLESymbols = Constants.NUMBER_OF_SYMBOLS;
        }
        for (int i = 0; i < numRLESymbols; i++) {
            rleSymbols[uncompressedRLEMetaData[pos++] & 0xFF] = 1;
        }

        // Set rleMetaPos to point past the symbol list, at the run-length data
        rleMetaPos[0] = pos;
        return uncompressedRLEMetaData;
    }

    private static byte[] decodeRLE(
            final byte[] in,
            final int[] rleSymbols,
            final byte[] rleMetaData,
            final int[] rleMetaPos,
            final int uncompressedRLEOutputLength) {
        final byte[] out = new byte[uncompressedRLEOutputLength];
        int j = 0;
        for (int i = 0; j < uncompressedRLEOutputLength; i++) {
            final byte sym = in[i];
            if (rleSymbols[sym & 0xFF] != 0) {
                final int run = CompressionUtils.readUint7(rleMetaData, rleMetaPos);
                for (int r = 0; r <= run; r++) {
                    out[j++] = sym;
                }
            } else {
                out[j++] = sym;
            }
        }
        return out;
    }

    private byte[] decodeStripe(final byte[] in, final int[] inPos, final int outSize) {
        final int numInterleaveStreams = in[inPos[0]++] & 0xFF;

        // Read (and discard) compressed lengths
        for (int j = 0; j < numInterleaveStreams; j++) {
            CompressionUtils.readUint7(in, inPos);
        }

        // Decode each sub-stream
        final int[] uncompressedLengths = new int[numInterleaveStreams];
        final byte[][] transposedData = new byte[numInterleaveStreams][];
        for (int j = 0; j < numInterleaveStreams; j++) {
            uncompressedLengths[j] = outSize / numInterleaveStreams;
            if ((outSize % numInterleaveStreams) > j) {
                uncompressedLengths[j]++;
            }
            transposedData[j] = uncompressInternal(in, inPos, uncompressedLengths[j]);
        }

        // Transpose
        final byte[] out = new byte[outSize];
        for (int j = 0; j < numInterleaveStreams; j++) {
            for (int i = 0; i < uncompressedLengths[j]; i++) {
                out[(i * numInterleaveStreams) + j] = transposedData[j][i];
            }
        }
        return out;
    }

    /** Read a 4-byte little-endian int from in at inPos[0], advancing inPos[0] by 4. */
    private static long readLittleEndianInt(final byte[] in, final int[] inPos) {
        int pos = inPos[0];
        final long value = (in[pos] & 0xFFL)
                | ((in[pos + 1] & 0xFFL) << 8)
                | ((in[pos + 2] & 0xFFL) << 16)
                | ((in[pos + 3] & 0xFFL) << 24);
        inPos[0] = pos + 4;
        return value;
    }
}
