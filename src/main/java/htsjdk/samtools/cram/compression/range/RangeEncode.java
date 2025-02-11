package htsjdk.samtools.cram.compression.range;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.BZIP2ExternalCompressor;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import htsjdk.samtools.cram.compression.CompressionUtils;

/**
 * Encoder for the CRAM 3.1 arithmetic (range) codec. This is an adaptive, byte-wise compression codec for use
 * with data streams that have a varying byte/symbol probability distribution. Significantly more expensive than
 * rAns.
 */
public class RangeEncode {

    private static final ByteBuffer EMPTY_BUFFER = CompressionUtils.allocateByteBuffer(0);

    // This method assumes that inBuffer is already rewound.
    // It compresses the data in the inBuffer, leaving it consumed.
    // Returns a rewound ByteBuffer containing the compressed data.
    public ByteBuffer compress(final ByteBuffer inBuffer, final RangeParams rangeParams) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        final ByteBuffer outBuffer = CompressionUtils.allocateOutputBuffer(inBuffer.remaining());
        outBuffer.order(ByteOrder.BIG_ENDIAN);
        final int formatFlags = rangeParams.getFormatFlags();
        outBuffer.put((byte) (formatFlags));

        if (!rangeParams.isNosz()) {
            // original size is not recorded
            CompressionUtils.writeUint7(inBuffer.remaining(), outBuffer);
        }

        ByteBuffer inputBuffer = inBuffer;

        // Stripe flag is not implemented in the write implementation
        if (rangeParams.isStripe()) {
            throw new CRAMException("Range Encoding with Stripe Flag is not implemented.");
        }

        final int inSize = inputBuffer.remaining(); // e_len -> inSize

        // Pack
        if (rangeParams.isPack()) {
            final int[] frequencyTable = new int[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < inSize; i++) {
                frequencyTable[inputBuffer.get(i) & 0xFF]++;
            }
            int numSymbols = 0;
            final int[] packMappingTable = new int[Constants.NUMBER_OF_SYMBOLS];
            for (int i = 0; i < Constants.NUMBER_OF_SYMBOLS; i++) {
                if (frequencyTable[i] > 0) {
                    packMappingTable[i] = numSymbols++;
                }
            }

            // skip Packing if numSymbols = 0  or numSymbols > 16
            if (numSymbols != 0 && numSymbols <= 16) {
                inputBuffer = CompressionUtils.encodePack(inputBuffer, outBuffer, frequencyTable, packMappingTable, numSymbols);
            } else {
                // unset pack flag in the first byte of the outBuffer
                outBuffer.put(0, (byte) (outBuffer.get(0) & ~RangeParams.PACK_FLAG_MASK));
            }
        }

        if (rangeParams.isCAT()) {
            // Data is uncompressed
            outBuffer.put(inputBuffer);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
        } else if (rangeParams.isExternalCompression()) {
            final byte[] rawBytes = new byte[inputBuffer.remaining()];
            inputBuffer.get(rawBytes, inBuffer.position(), inputBuffer.remaining());
            final BZIP2ExternalCompressor compressor = new BZIP2ExternalCompressor();
            final byte[] extCompressedBytes = compressor.compress(rawBytes);
            outBuffer.put(extCompressedBytes);
            outBuffer.limit(outBuffer.position());
            outBuffer.rewind(); // set position to 0
        } else if (rangeParams.isRLE()) {
            switch (rangeParams.getOrder()) {
                case ZERO:
                    compressRLEOrder0(inputBuffer, outBuffer);
                    break;
                case ONE:
                    compressRLEOrder1(inputBuffer, outBuffer);
                    break;
                default:
                    throw new CRAMException("Unknown range order: " + rangeParams.getOrder());
            }
        } else {
            switch (rangeParams.getOrder()) {
                case ZERO:
                    compressOrder0(inputBuffer, outBuffer);
                    break;
                case ONE:
                    compressOrder1(inputBuffer, outBuffer);
                    break;
                default:
                    throw new CRAMException("Unknown range order: " + rangeParams.getOrder());
            }
        }
        return outBuffer;
    }

    private void compressOrder0(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer) {

        int maxSymbol = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbol < (inBuffer.get(i) & 0xFF)) {
                maxSymbol = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbol++;
        final ByteModel byteModel = new ByteModel(maxSymbol);
        outBuffer.put((byte) maxSymbol);
        final RangeCoder rangeCoder = new RangeCoder();
        for (int i = 0; i < inSize; i++) {
            byteModel.modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
    }

    private void compressOrder1(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer) {
        int maxSymbol = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbol < (inBuffer.get(i) & 0xFF)) {
                maxSymbol = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbol++;
        final List<ByteModel> byteModelList = new ArrayList();
        for (int i = 0; i < maxSymbol; i++) {
            byteModelList.add(i, new ByteModel(maxSymbol));
        }
        outBuffer.put((byte) maxSymbol);
        final RangeCoder rangeCoder = new RangeCoder();
        int last = 0;
        for (int i = 0; i < inSize; i++) {
            byteModelList.get(last).modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            last = inBuffer.get(i) & 0xFF;
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
    }

    private void compressRLEOrder0(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer) {
        int maxSymbols = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbols < (inBuffer.get(i) & 0xFF)) {
                maxSymbols = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbols++;  // FIXME not what spec states!

        final ByteModel modelLit = new ByteModel(maxSymbols);
        final List<ByteModel> byteModelRunsList = new ArrayList(258);

        for (int i = 0; i <= 257; i++) {
            byteModelRunsList.add(i, new ByteModel(4));
        }
        outBuffer.put((byte) maxSymbols);
        final RangeCoder rangeCoder = new RangeCoder();
        int i = 0;
        while (i < inSize) {
            modelLit.modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            int run = 1;
            while (i + run < inSize && (inBuffer.get(i + run) & 0xFF) == (inBuffer.get(i) & 0xFF)) {
                run++;
            }
            run--; // Check this!!
            int rctx = inBuffer.get(i) & 0xFF;
            i += run + 1;
            int part = run >= 3 ? 3 : run;
            byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
            run -= part;
            rctx = 256;
            while (part == 3) {
                part = run >= 3 ? 3 : run;
                byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
                rctx = 257;
                run -= part;
            }
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
    }

    private void compressRLEOrder1(
            final ByteBuffer inBuffer,
            final ByteBuffer outBuffer) {
        int maxSymbols = 0;
        final int inSize = inBuffer.remaining();
        for (int i = 0; i < inSize; i++) {
            if (maxSymbols < (inBuffer.get(i) & 0xFF)) {
                maxSymbols = inBuffer.get(i) & 0xFF;
            }
        }
        maxSymbols++;  // FIXME not what spec states!

        final List<ByteModel> modelLitList = new ArrayList<>(maxSymbols);
        for (int i = 0; i < maxSymbols; i++) {
            modelLitList.add(i, new ByteModel(maxSymbols));
        }
        final List<ByteModel> byteModelRunsList = new ArrayList(258);
        for (int i = 0; i <= 257; i++) {
            byteModelRunsList.add(i, new ByteModel(4));
        }
        outBuffer.put((byte) maxSymbols);
        final RangeCoder rangeCoder = new RangeCoder();
        int i = 0;
        int last = 0;
        while (i < inSize) {
            modelLitList.get(last).modelEncode(outBuffer, rangeCoder, inBuffer.get(i) & 0xFF);
            int run = 1;
            while (i + run < inSize && inBuffer.get(i + run) == inBuffer.get(i)) {
                run++;
            }
            run--; // Check this!!
            int rctx = inBuffer.get(i) & 0xFF;
            last = inBuffer.get(i) & 0xFF;
            i += run + 1;
            int part = run >= 3 ? 3 : run;
            byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
            run -= part;
            rctx = 256;
            while (part == 3) {
                part = run >= 3 ? 3 : run;
                byteModelRunsList.get(rctx).modelEncode(outBuffer, rangeCoder, part);
                rctx = 257;
                run -= part;
            }
        }
        rangeCoder.rangeEncodeEnd(outBuffer);
        outBuffer.limit(outBuffer.position());
        outBuffer.rewind();
    }

}