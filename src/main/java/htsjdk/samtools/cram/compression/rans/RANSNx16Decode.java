package htsjdk.samtools.cram.compression.rans;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANSNx16Decode extends RANSDecode<RANSNx16Params>{
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer,  final RANSNx16Params params) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }

        // For RANS decoding, the bytes are read in little endian from the input stream
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        initializeRANSDecoder();

        // the first byte of compressed stream gives the formatFlags
        final int formatFlags = inBuffer.get();
        params.setFormatFlags(formatFlags);
        int n_out = params.getnOut();
        final RANSParams.ORDER order = params.getOrder(); // Order-0 or Order-1 entropy coding
        final boolean x32 = params.getX32(); // Interleave N = 32 rANS states (else N = 4)
        final boolean stripe = params.getStripe(); //multiway interleaving of byte streams
        final boolean nosz = params.getNosz(); // original size is not recorded
        final boolean cat = params.getCAT(); // Data is uncompressed
        final boolean rle = params.getRLE(); // Run length encoding, with runs and literals encoded separately
        final boolean pack = params.getPack(); // Pack 2, 4, 8 or infinite symbols per byte

        // TODO: add methods to handle various flags

        // N-way interleaving. If the NWay flag is set, use 32 way interleaving, else use 4 way
        final int Nway = (x32) ? 32 : 4;

        // if nosz is set, then uncompressed size is not recorded.
        if (!nosz) {
            n_out = Utils.readUint7(inBuffer);
        }
        ByteBuffer outBuffer = ByteBuffer.allocate(n_out);

        // If CAT is set then, the input is uncompressed
        if (cat){
            byte[] data = new byte[n_out];
            outBuffer = inBuffer.get( data,0, n_out);
        }
        else {
            switch (order){
                case ZERO:
                    outBuffer = uncompressOrder0WayN(inBuffer,outBuffer, n_out,Nway);
                    break;
//                case ONE:
//                    uncompressOrder1WayN(inBuffer,n_out, Nway);
//                    break;
                default:
                    throw new RuntimeException("Unknown rANS order: " + order);
            }
        }
        return outBuffer;
    }

    private ByteBuffer uncompressOrder0WayN(final ByteBuffer inBuffer, final ByteBuffer outBuffer,final int n_out,final int Nway) {
        // read the frequency table, get the normalised frequencies and use it to set the RANSDecodingSymbols
        FrequenciesNx16.readStatsOrder0(inBuffer, getD()[0], getDecodingSymbols()[0]);
        // uncompress using Nway rans states
        D0N.uncompress(inBuffer, getD()[0], getDecodingSymbols()[0], outBuffer,n_out,Nway);
        return outBuffer;
    }

}