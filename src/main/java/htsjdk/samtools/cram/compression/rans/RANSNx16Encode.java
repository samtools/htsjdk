package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;

public class RANSNx16Encode extends RANSEncode<RANSNx16Params>{
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);
    private static final int MINIMUM__ORDER_1_SIZE = 4;

    public ByteBuffer compress(final ByteBuffer inBuffer, final RANSNx16Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        final ByteBuffer outBuffer = allocateOutputBuffer(inBuffer.remaining());
        final int formatFlags = params.getFormatFlags();
        outBuffer.put((byte) (formatFlags)); // one byte for formatFlags
        final RANSParams.ORDER order = params.getOrder(); // Order-0 or Order-1 entropy coding
        final boolean x32 = params.getX32(); // Interleave N = 32 rANS states (else N = 4)
        final boolean stripe = params.getStripe(); //multiway interleaving of byte streams
        final boolean nosz = params.getNosz(); // original size is not recorded
        final boolean cat = params.getCAT(); // Data is uncompressed
        final boolean rle = params.getRLE(); // Run length encoding, with runs and literals encoded separately
        final boolean pack = params.getPack(); // Pack 2, 4, 8 or infinite symbols per byte

        // TODO: add methods to handle various flags

        // N-way interleaving
        final int Nway = (x32) ? 32 : 4;

        //stripe size
        final int N = formatFlags>>8;

        if (!nosz) {
            int insize = inBuffer.remaining();
            Utils.writeUint7(insize,outBuffer);
        }
        initializeRANSEncoder();
        if (cat) {
            outBuffer.put(inBuffer);
            return outBuffer;
        }

        if (inBuffer.remaining() < MINIMUM__ORDER_1_SIZE) {
            // TODO: check if this still applies for Nx16 or if there is a different limit
            // ORDER-1 encoding of less than 4 bytes is not permitted, so just use ORDER-0
            return compressOrder0WayN(inBuffer, Nway, outBuffer);
        }

        switch (order) {
            case ZERO:
                return compressOrder0WayN(inBuffer, Nway, outBuffer);
//            case ONE:
//                return compressOrder1WayN(inBuffer, Nway, outBuffer);
            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    private ByteBuffer compressOrder0WayN(final ByteBuffer inBuffer, final int Nway, final ByteBuffer outBuffer) {
        final int inSize = inBuffer.remaining();
        final int[] F = FrequenciesNx16.buildFrequenciesOrder0(inBuffer);
        final ByteBuffer cp = outBuffer.slice();
        int bitSize = (int) Math.ceil(Math.log(inSize) / Math.log(2));
        if (bitSize == 0) {
            // TODO: check this!
            // If there is just one symbol, bitsize = log (1)/log(2) = 0.
            bitSize = 1;
        }
        if (bitSize > 12) {
            bitSize = 12;
        }
        final int prefix_size = outBuffer.position();

        // Normalize Frequencies such that sum of Frequencies = 1 << bitsize
        FrequenciesNx16.normaliseFrequenciesOrder0(F, bitSize);

        // Write the Frequency table. Keep track of the size for later
        final int frequencyTableSize = FrequenciesNx16.writeFrequenciesOrder0(cp, F);

        // Normalize Frequencies such that sum of Frequencies = 1 << 12
        FrequenciesNx16.normaliseFrequenciesOrder0(F, 12);

        // update the RANS Encoding Symbols
        FrequenciesNx16.buildSymsOrder0(F, getEncodingSymbols()[0]);
        inBuffer.rewind();
        final int compressedBlobSize = E0N.compress(inBuffer, getEncodingSymbols()[0], cp, Nway);
        outBuffer.rewind(); // set position to 0
        outBuffer.limit(prefix_size + frequencyTableSize + compressedBlobSize);
        return outBuffer;
    }

}