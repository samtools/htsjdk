package htsjdk.samtools.cram.encoding.rans;

import htsjdk.samtools.cram.encoding.rans.Encoding.RansEncSymbol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS {
    public enum ORDER {
        ZERO, ONE;

        public static ORDER fromInt(int value) {
            try {
                return ORDER.values()[value];
            } catch (ArrayIndexOutOfBoundsException e) {
                throw new RuntimeException("Uknown rANS order: " + value);
            }
        }
    }

    private static final int ORDER_BYTE_LENGTH = 1;
    private static final int COMPRESSED_BYTE_LENGTH = 4;
    private static final int RAW_BYTE_LENGTH = 4;
    private static final int PREFIX_BYTE_LENGTH = ORDER_BYTE_LENGTH
            + COMPRESSED_BYTE_LENGTH + RAW_BYTE_LENGTH;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public static ByteBuffer uncompress(ByteBuffer in, ByteBuffer out) {
        if (in.remaining() == 0)
            return ByteBuffer.allocate(0);

        ORDER order = ORDER.fromInt(in.get());

        in.order(ByteOrder.LITTLE_ENDIAN);
        int in_sz = in.getInt();
        if (in_sz != in.remaining() - RAW_BYTE_LENGTH)
            throw new RuntimeException("Incorrect input length.");
        int out_sz = in.getInt();
        if (out == null)
            out = ByteBuffer.allocate(out_sz);
        else
            out.limit(out_sz);
        if (out.remaining() < out_sz)
            throw new RuntimeException("Output buffer too small to fit "
                    + out_sz + " bytes.");

        switch (order) {
            case ZERO:
                return uncompress_order0_way4(in, out);

            case ONE:
                return uncompress_order1_way4(in, out);

            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    public static ByteBuffer compress(ByteBuffer in, ORDER order, ByteBuffer out) {
        if (in.remaining() == 0)
            return EMPTY_BUFFER;

        if (in.remaining() < 4)
            return encode_order0_way4(in, out);

        switch (order) {
            case ZERO:
                return encode_order0_way4(in, out);
            case ONE:
                return encode_order1_way4(in, out);

            default:
                throw new RuntimeException("Unknown rANS order: " + order);
        }
    }

    private static ByteBuffer allocateIfNeeded(int in_size,
                                                     ByteBuffer out_buf) {
        int compressedSize = (int) (1.05 * in_size + 257 * 257 * 3 + 4);
        if (out_buf == null)
            return ByteBuffer.allocate(compressedSize);
        if (out_buf.remaining() < compressedSize)
            throw new RuntimeException("Insuffient buffer size.");
        out_buf.order(ByteOrder.LITTLE_ENDIAN);
        return out_buf;
    }

    private static ByteBuffer encode_order0_way4(ByteBuffer in,
                                                       ByteBuffer out_buf) {
        int in_size = in.remaining();
        out_buf = allocateIfNeeded(in_size, out_buf);
        int freqTableStart = PREFIX_BYTE_LENGTH;
        out_buf.position(freqTableStart);

        int[] F = Freqs.calcFreqs_o0(in);
        RansEncSymbol[] syms = Freqs.buildSyms_o0(F);

        ByteBuffer cp = out_buf.slice();
        int frequencyTable_size = Freqs.writeFreqs_o0(cp, F);

        in.rewind();
        int compressedBlob_size = E04.compress(in, syms, cp);

        finilizeCompressed(0, out_buf, in_size, frequencyTable_size,
                compressedBlob_size);
        return out_buf;
    }

    private static ByteBuffer encode_order1_way4(ByteBuffer in,
                                                       ByteBuffer out_buf) {
        int in_size = in.remaining();
        out_buf = allocateIfNeeded(in_size, out_buf);
        int freqTableStart = PREFIX_BYTE_LENGTH;
        out_buf.position(freqTableStart);

        int[][] F = Freqs.calcFreqs_o1(in);
        RansEncSymbol[][] syms = Freqs.buildSyms_o1(F);

        ByteBuffer cp = out_buf.slice();
        int frequencyTable_size = Freqs.writeFreqs_o1(cp, F);

        in.rewind();
        int compressedBlob_size = E14.compress(in, syms, cp);

        finilizeCompressed(1, out_buf, in_size, frequencyTable_size,
                compressedBlob_size);
        return out_buf;
    }

    private static void finilizeCompressed(int order, ByteBuffer out_buf,
                                                 int in_size, int frequencyTable_size, int compressedBlob_size) {
        out_buf.limit(PREFIX_BYTE_LENGTH + frequencyTable_size
                + compressedBlob_size);
        out_buf.put(0, (byte) order);
        out_buf.order(ByteOrder.LITTLE_ENDIAN);
        int compressedSizeOffset = ORDER_BYTE_LENGTH;
        out_buf.putInt(compressedSizeOffset, frequencyTable_size
                + compressedBlob_size);
        int rawSizeOffset = ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH;
        out_buf.putInt(rawSizeOffset, in_size);
        out_buf.rewind();
    }

    private static ByteBuffer uncompress_order0_way4(ByteBuffer in,
                                                           ByteBuffer out) {
        in.order(ByteOrder.LITTLE_ENDIAN);
        Decoding.ari_decoder D = new Decoding.ari_decoder();
        Decoding.RansDecSymbol[] syms = new Decoding.RansDecSymbol[256];
        for (int i = 0; i < syms.length; i++)
            syms[i] = new Decoding.RansDecSymbol();

        Freqs.readStats_o0(in, D, syms);

        D04.uncompress(in, D, syms, out);

        return out;
    }

    private static ByteBuffer uncompress_order1_way4(ByteBuffer in,
                                                           ByteBuffer out_buf) {
        Decoding.ari_decoder[] D = new Decoding.ari_decoder[256];
        Decoding.RansDecSymbol[][] syms = new Decoding.RansDecSymbol[256][256];
        for (int i = 0; i < syms.length; i++)
            for (int j = 0; j < syms[i].length; j++)
                syms[i][j] = new Decoding.RansDecSymbol();
        Freqs.readStats_o1(in, D, syms);

        D14.uncompress(in, out_buf, D, syms);

        return out_buf;
    }
}
