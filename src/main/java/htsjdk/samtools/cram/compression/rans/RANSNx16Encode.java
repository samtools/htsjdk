package htsjdk.samtools.cram.compression.rans;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import java.nio.ByteBuffer;

/**
 * Encoder for the CRAM 3.1 rANSNx16 codec. Internal encoding uses byte[] with backwards-write
 * to eliminate the O(N) reverse pass. Pack/RLE/Stripe preprocessing still bridges through ByteBuffer
 * where CompressionUtils methods require it.
 */
public class RANSNx16Encode extends RANSEncode<RANSNx16Params> {

    /**
     * Compress a byte array using the rANS Nx16 codec. Applies the transformations
     * specified by the params (PACK, RLE, STRIPE) as preprocessing, then encodes the
     * result with Order-0 or Order-1 rANS using 4-way or 32-way interleaving.
     *
     * @param input the data to compress
     * @param ransNx16Params encoding parameters specifying order, interleave width, and transformations
     * @return the compressed byte stream
     */
    @Override
    public byte[] compress(final byte[] input, final RANSNx16Params ransNx16Params) {
        if (input.length == 0) {
            return new byte[0];
        }
        final ByteBuffer outBuffer = CompressionUtils.allocateOutputBuffer(input.length);
        final int formatFlags = ransNx16Params.getFormatFlags();
        outBuffer.put((byte) formatFlags);

        if (!ransNx16Params.isNosz()) {
            CompressionUtils.writeUint7(input.length, outBuffer);
        }

        ByteBuffer inputBuffer = CompressionUtils.wrap(input);

        // Stripe
        if (ransNx16Params.isStripe()) {
            compressStripe(inputBuffer, outBuffer);
            final byte[] result = new byte[outBuffer.remaining()];
            outBuffer.get(result);
            return result;
        }

        // Pack
        if (ransNx16Params.isPack()) {
            final int[] frequencyTable = new int[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < input.length; i++) {
                frequencyTable[input[i] & 0xFF]++;
            }
            int numSymbols = 0;
            final int[] packMappingTable = new int[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                if (frequencyTable[i] > 0) {
                    packMappingTable[i] = numSymbols++;
                }
            }
            if (numSymbols > 1 && numSymbols <= 16) {
                inputBuffer = CompressionUtils.encodePack(
                        inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                outBuffer.put(0, (byte) (outBuffer.get(0) & ~RANSNx16Params.PACK_FLAG_MASK));
            }
        }

        // RLE
        if (ransNx16Params.isRLE()) {
            inputBuffer = encodeRLE(inputBuffer, outBuffer, ransNx16Params);
        }

        // Extract input bytes for the core encoder
        final byte[] in = new byte[inputBuffer.remaining()];
        inputBuffer.get(in);

        if (ransNx16Params.isCAT()) {
            outBuffer.put(in);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind();
            final byte[] result = new byte[outBuffer.remaining()];
            outBuffer.get(result);
            return result;
        }

        final int Nway = ransNx16Params.getNumInterleavedRANSStates();
        RANSNx16Params effectiveParams = ransNx16Params;
        if (in.length < Nway && ransNx16Params.getOrder() == RANSParams.ORDER.ONE) {
            outBuffer.put(0, (byte) (outBuffer.get(0) & ~RANSNx16Params.ORDER_FLAG_MASK));
            effectiveParams = new RANSNx16Params(outBuffer.get(0));
            if (in.length == 0) {
                outBuffer.limit(outBuffer.position());
                outBuffer.rewind();
                final byte[] result = new byte[outBuffer.remaining()];
                outBuffer.get(result);
                return result;
            }
        }

        final int prefixSize = outBuffer.position();
        final byte[] encoded;
        switch (effectiveParams.getOrder()) {
            case ZERO:
                encoded = compressOrder0WayN(in, effectiveParams);
                break;
            case ONE:
                encoded = compressOrder1WayN(in, effectiveParams);
                break;
            default:
                throw new CRAMException("Unknown rANS order: " + effectiveParams.getOrder());
        }

        final byte[] result = new byte[prefixSize + encoded.length];
        outBuffer.rewind();
        outBuffer.get(result, 0, prefixSize);
        System.arraycopy(encoded, 0, result, prefixSize, encoded.length);
        return result;
    }

    // ---- Core Order-0 encoder (byte[] backwards-write) ----

    private byte[] compressOrder0WayN(final byte[] in, final RANSNx16Params params) {
        final int inSize = in.length;
        int bitSize = inSize <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(inSize - 1);
        if (bitSize > Constants.TOTAL_FREQ_SHIFT) bitSize = Constants.TOTAL_FREQ_SHIFT;

        final int[] F = buildFrequenciesOrder0(in);
        Utils.normaliseFrequenciesOrder0(F, bitSize);

        final byte[] freqTable = new byte[1024];
        final int[] freqPos = {0};
        writeFrequenciesOrder0(freqTable, freqPos, F);
        final int frequencyTableSize = freqPos[0];

        if (bitSize != Constants.TOTAL_FREQ_SHIFT) {
            Utils.normaliseFrequenciesOrder0Shift(F, Constants.TOTAL_FREQ_SHIFT);
        }
        buildSymsOrder0(F);

        final int Nway = params.getNumInterleavedRANSStates();
        final int interleaveSize = (Nway == 4) ? (inSize >> 2) : (inSize >> 5);
        int remainingSize = inSize - (interleaveSize * Nway);
        final long[] rans = new long[Nway];
        for (int r = 0; r < Nway; r++) rans[r] = Constants.RANS_Nx16_LOWER_BOUND;

        final int maxCompressedSize = inSize + inSize / 4 + Nway * 4 + 64;
        final byte[] compressedData = new byte[maxCompressedSize];
        int pos = maxCompressedSize; // write position, decrements — kept as local for register allocation

        final RANSEncodingSymbol[] syms = getEncodingSymbols()[0];

        // Remainder symbols (inlined putSymbolNx16 to keep pos in a register)
        int reverseIndex = 1;
        while (remainingSize > 0) {
            final RANSEncodingSymbol sym = syms[in[inSize - reverseIndex] & 0xFF];
            long x = rans[remainingSize - 1];
            if (x >= sym.xMax) {
                compressedData[--pos] = (byte) ((x >> 8) & 0xFF);
                compressedData[--pos] = (byte) (x & 0xFF);
                x >>= 16;
                if (x >= sym.xMax) {
                    compressedData[--pos] = (byte) ((x >> 8) & 0xFF);
                    compressedData[--pos] = (byte) (x & 0xFF);
                    x >>= 16;
                }
            }
            rans[remainingSize - 1] = x + sym.bias + ((x * (0xFFFFFFFFL & sym.rcpFreq)) >> sym.rcpShift) * sym.cmplFreq;
            remainingSize--;
            reverseIndex++;
        }

        // Main interleaved encoding loop (inlined putSymbolNx16)
        for (int i = interleaveSize * Nway; i > 0; i -= Nway) {
            for (int r = Nway - 1; r >= 0; r--) {
                final RANSEncodingSymbol sym = syms[in[i - (Nway - r)] & 0xFF];
                long x = rans[r];
                if (x >= sym.xMax) {
                    compressedData[--pos] = (byte) ((x >> 8) & 0xFF);
                    compressedData[--pos] = (byte) (x & 0xFF);
                    x >>= 16;
                    if (x >= sym.xMax) {
                        compressedData[--pos] = (byte) ((x >> 8) & 0xFF);
                        compressedData[--pos] = (byte) (x & 0xFF);
                        x >>= 16;
                    }
                }
                rans[r] = x + sym.bias + ((x * (0xFFFFFFFFL & sym.rcpFreq)) >> sym.rcpShift) * sym.cmplFreq;
            }
        }

        // Flush states: rans[Nway-1] first (highest addr), rans[0] last (lowest addr)
        for (int i = Nway - 1; i >= 0; i--) {
            final int state = (int) rans[i];
            compressedData[--pos] = (byte) ((state >> 24) & 0xFF);
            compressedData[--pos] = (byte) ((state >> 16) & 0xFF);
            compressedData[--pos] = (byte) ((state >> 8) & 0xFF);
            compressedData[--pos] = (byte) (state & 0xFF);
        }

        final int compressedSize = maxCompressedSize - pos;
        final byte[] result = new byte[frequencyTableSize + compressedSize];
        System.arraycopy(freqTable, 0, result, 0, frequencyTableSize);
        System.arraycopy(compressedData, pos, result, frequencyTableSize, compressedSize);
        return result;
    }

    // ---- Core Order-1 encoder (byte[] backwards-write) ----

    private byte[] compressOrder1WayN(final byte[] in, final RANSNx16Params params) {
        final int inputSize = in.length;
        final int Nway = params.getNumInterleavedRANSStates();
        final int[][] frequencies = buildFrequenciesOrder1(in, Nway);

        Utils.normaliseFrequenciesOrder1(frequencies, Constants.TOTAL_FREQ_SHIFT);

        final byte[] uncompFreqTable = new byte[257 * 256 * 3 + 256];
        final int[] uncompPos = {0};
        writeFrequenciesOrder1(uncompFreqTable, uncompPos, frequencies);
        final int uncompFreqTableSize = uncompPos[0];

        final byte[] compFreqTable = compressOrder0WayN(
                java.util.Arrays.copyOf(uncompFreqTable, uncompFreqTableSize),
                new RANSNx16Params(~(RANSNx16Params.ORDER_FLAG_MASK | RANSNx16Params.N32_FLAG_MASK)));

        final byte[] freqHeader;
        if (compFreqTable.length < uncompFreqTableSize) {
            final byte[] h = new byte[1 + 10 + 10 + compFreqTable.length];
            final int[] hp = {0};
            h[hp[0]++] = (byte) (1 | Constants.TOTAL_FREQ_SHIFT << 4);
            CompressionUtils.writeUint7(uncompFreqTableSize, h, hp);
            CompressionUtils.writeUint7(compFreqTable.length, h, hp);
            System.arraycopy(compFreqTable, 0, h, hp[0], compFreqTable.length);
            hp[0] += compFreqTable.length;
            freqHeader = java.util.Arrays.copyOf(h, hp[0]);
        } else {
            freqHeader = new byte[1 + uncompFreqTableSize];
            freqHeader[0] = (byte) (0 | Constants.TOTAL_FREQ_SHIFT << 4);
            System.arraycopy(uncompFreqTable, 0, freqHeader, 1, uncompFreqTableSize);
        }

        Utils.normaliseFrequenciesOrder1Shift(frequencies, Constants.TOTAL_FREQ_SHIFT);
        buildSymsOrder1(frequencies);

        final long[] rans = new long[Nway];
        for (int r = 0; r < Nway; r++) rans[r] = Constants.RANS_Nx16_LOWER_BOUND;

        final int interleaveSize = (Nway == 4) ? inputSize >> 2 : inputSize >> 5;
        final int[] idx = new int[Nway];
        final byte[] symbol = new byte[Nway];
        for (int r = 0; r < Nway; r++) {
            idx[r] = (r + 1) * interleaveSize - 2;
            symbol[r] = 0;
            if (idx[r] + 1 >= 0 && r != Nway - 1) symbol[r] = in[idx[r] + 1];
            if (r == Nway - 1) symbol[r] = in[inputSize - 1];
        }

        final int maxCompressedSize = inputSize + inputSize / 4 + Nway * 4 + 64;
        final byte[] compressedData = new byte[maxCompressedSize];
        final int[] writePos = {maxCompressedSize};

        final RANSEncodingSymbol[][] syms = getEncodingSymbols();
        final byte[] context = new byte[Nway];

        // Remainder
        for (idx[Nway - 1] = inputSize - 2;
                idx[Nway - 1] > Nway * interleaveSize - 2 && idx[Nway - 1] >= 0;
                idx[Nway - 1]--) {
            context[Nway - 1] = in[idx[Nway - 1]];
            rans[Nway - 1] = syms[context[Nway - 1] & 0xFF][symbol[Nway - 1] & 0xFF].putSymbolNx16(
                    rans[Nway - 1], compressedData, writePos);
            symbol[Nway - 1] = context[Nway - 1];
        }

        // Main loop
        while (idx[0] >= 0) {
            for (int r = 0; r < Nway; r++) {
                context[Nway - 1 - r] = in[idx[Nway - 1 - r]];
                rans[Nway - 1 - r] = syms[context[Nway - 1 - r] & 0xFF][symbol[Nway - 1 - r] & 0xFF].putSymbolNx16(
                        rans[Nway - 1 - r], compressedData, writePos);
                symbol[Nway - 1 - r] = context[Nway - 1 - r];
            }
            for (int r = 0; r < Nway; r++) idx[r]--;
        }

        // Final context=0 symbols
        for (int r = 0; r < Nway; r++) {
            rans[Nway - 1 - r] =
                    syms[0][symbol[Nway - 1 - r] & 0xFF].putSymbolNx16(rans[Nway - 1 - r], compressedData, writePos);
        }

        // Flush states (same pattern as O0)
        for (int i = Nway - 1; i >= 0; i--) {
            final int state = (int) rans[i];
            compressedData[--writePos[0]] = (byte) ((state >> 24) & 0xFF);
            compressedData[--writePos[0]] = (byte) ((state >> 16) & 0xFF);
            compressedData[--writePos[0]] = (byte) ((state >> 8) & 0xFF);
            compressedData[--writePos[0]] = (byte) (state & 0xFF);
        }

        final int compressedSize = maxCompressedSize - writePos[0];
        final byte[] result = new byte[freqHeader.length + compressedSize];
        System.arraycopy(freqHeader, 0, result, 0, freqHeader.length);
        System.arraycopy(compressedData, writePos[0], result, freqHeader.length, compressedSize);
        return result;
    }

    // ---- Frequency helpers (byte[]) ----

    private static int[] buildFrequenciesOrder0(final byte[] in) {
        final int[] F = new int[Constants.NUMBER_OF_SYMBOLS];
        for (final byte b : in) F[b & 0xFF]++;
        return F;
    }

    private static int[][] buildFrequenciesOrder1(final byte[] in, final int Nway) {
        final int inputSize = in.length;
        final int[][] F = new int[Constants.NUMBER_OF_SYMBOLS + 1][Constants.NUMBER_OF_SYMBOLS];
        byte ctx = 0;
        for (int i = 0; i < inputSize; i++) {
            F[Constants.NUMBER_OF_SYMBOLS][ctx & 0xFF]++;
            F[ctx & 0xFF][in[i] & 0xFF]++;
            ctx = in[i];
        }
        F[Constants.NUMBER_OF_SYMBOLS][ctx & 0xFF]++;
        for (int n = 1; n < Nway; n++) {
            final int pos = Nway == 4 ? (n * (inputSize >> 2)) : (n * (inputSize >> 5));
            F[0][in[pos] & 0xFF]++;
        }
        F[Constants.NUMBER_OF_SYMBOLS][0] += Nway - 1;
        return F;
    }

    private static void writeFrequenciesOrder0(final byte[] out, final int[] pos, final int[] F) {
        writeAlphabet(out, pos, F);
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                if (F[j] < 128) {
                    out[pos[0]++] = (byte) (F[j] & 0x7f);
                } else {
                    out[pos[0]++] = (byte) (128 | (F[j] >> 7));
                    out[pos[0]++] = (byte) (F[j] & 0x7f);
                }
            }
        }
    }

    private static void writeFrequenciesOrder1(final byte[] out, final int[] pos, final int[][] F) {
        writeAlphabet(out, pos, F[Constants.NUMBER_OF_SYMBOLS]);
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
            if (F[Constants.NUMBER_OF_SYMBOLS][i] == 0) continue;
            int run = 0;
            for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
                if (F[Constants.NUMBER_OF_SYMBOLS][j] == 0) continue;
                if (run > 0) {
                    run--;
                    continue;
                }
                CompressionUtils.writeUint7(F[i][j], out, pos);
                if (F[i][j] == 0) {
                    for (int k = j + 1; k < Constants.NUMBER_OF_SYMBOLS; k++) {
                        if (F[Constants.NUMBER_OF_SYMBOLS][k] == 0) continue;
                        if (F[i][k] == 0) run++;
                        else break;
                    }
                    out[pos[0]++] = (byte) run;
                }
            }
        }
    }

    private static void writeAlphabet(final byte[] out, final int[] pos, final int[] F) {
        int rle = 0;
        for (int j = 0; j < Constants.NUMBER_OF_SYMBOLS; j++) {
            if (F[j] != 0) {
                if (rle != 0) {
                    rle--;
                } else {
                    out[pos[0]++] = (byte) j;
                    if (j != 0 && F[j - 1] != 0) {
                        for (rle = j + 1; rle < Constants.NUMBER_OF_SYMBOLS && F[rle] != 0; rle++)
                            ;
                        rle -= j + 1;
                        out[pos[0]++] = (byte) rle;
                    }
                }
            }
        }
        out[pos[0]++] = 0;
    }

    // ---- RLE and Stripe (ByteBuffer bridge) ----

    private ByteBuffer encodeRLE(
            final ByteBuffer inBuffer, final ByteBuffer outBuffer, final RANSNx16Params ransNx16Params) {
        final int[] runCounts = new int[Constants.NUMBER_OF_SYMBOLS];
        final int inputSize = inBuffer.remaining();
        int lastSymbol = -1;
        for (int i = 0; i < inputSize; i++) {
            final int s = inBuffer.get(i) & 0xFF;
            runCounts[s] += (s == lastSymbol ? 1 : -1);
            lastSymbol = s;
        }
        int numRLESymbols = 0;
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) if (runCounts[i] > 0) numRLESymbols++;
        if (numRLESymbols == 0) {
            numRLESymbols = 1;
            runCounts[0] = 1;
        }

        final ByteBuffer rleMetaData = CompressionUtils.allocateByteBuffer(numRLESymbols + 1 + inputSize);
        rleMetaData.put((byte) numRLESymbols);
        for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) if (runCounts[i] > 0) rleMetaData.put((byte) i);

        final ByteBuffer encodedBuffer = CompressionUtils.allocateByteBuffer(inputSize);
        int idx = 0;
        for (int i = 0; i < inputSize; i++) {
            encodedBuffer.put(idx++, inBuffer.get(i));
            if (runCounts[inBuffer.get(i) & 0xFF] > 0) {
                lastSymbol = inBuffer.get(i) & 0xFF;
                int run = 0;
                while (i + run + 1 < inputSize && (inBuffer.get(i + run + 1) & 0xFF) == lastSymbol) run++;
                CompressionUtils.writeUint7(run, rleMetaData);
                i += run;
            }
        }
        encodedBuffer.limit(idx);
        rleMetaData.limit(rleMetaData.position());
        rleMetaData.rewind();

        final byte[] rleMeta = new byte[rleMetaData.remaining()];
        rleMetaData.get(rleMeta);
        final byte[] compressedRleMeta = compressOrder0WayN(
                rleMeta, new RANSNx16Params(0x00 | ransNx16Params.getFormatFlags() & RANSNx16Params.N32_FLAG_MASK));

        CompressionUtils.writeUint7(rleMeta.length * 2, outBuffer);
        CompressionUtils.writeUint7(idx, outBuffer);
        CompressionUtils.writeUint7(compressedRleMeta.length, outBuffer);
        outBuffer.put(compressedRleMeta);

        inBuffer.position(inBuffer.limit());
        return encodedBuffer;
    }

    private void compressStripe(final ByteBuffer inBuffer, final ByteBuffer outBuffer) {
        final int numStreams = CompressionUtils.getStripeNumStreams();
        final int[] sizes = CompressionUtils.buildStripeUncompressedSizes(inBuffer.remaining());
        final ByteBuffer[] chunks = CompressionUtils.stripeTranspose(inBuffer, sizes);

        final byte[][] compressedChunks = new byte[numStreams][];
        for (int i = 0; i < numStreams; i++) {
            final byte[] chunkBytes = new byte[chunks[i].remaining()];
            chunks[i].get(chunkBytes);
            compressedChunks[i] = compress(chunkBytes, new RANSNx16Params(RANSNx16Params.NOSZ_FLAG_MASK));
        }

        outBuffer.put((byte) numStreams);
        for (int i = 0; i < numStreams; i++) CompressionUtils.writeUint7(compressedChunks[i].length, outBuffer);
        for (int i = 0; i < numStreams; i++) outBuffer.put(compressedChunks[i]);

        inBuffer.position(inBuffer.limit());
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
    }
}
