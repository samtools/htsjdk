package htsjdk.samtools.cram.encoding.rans;

import htsjdk.samtools.cram.encoding.rans.Encoding.RansEncSymbol;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class RANS {
    public enum ORDER {
        ZERO, ONE;

        public static ORDER fromInt(final int value) {
            try {
                return ORDER.values()[value];
            } catch (final ArrayIndexOutOfBoundsException e) {
                throw new RuntimeException("Unknown rANS order: " + value);
            }
        }
    }

    private static final int ORDER_BYTE_LENGTH = 1;
    private static final int COMPRESSED_BYTE_LENGTH = 4;
    private static final int RAW_BYTE_LENGTH = 4;
    private static final int PREFIX_BYTE_LENGTH = ORDER_BYTE_LENGTH
            + COMPRESSED_BYTE_LENGTH + RAW_BYTE_LENGTH;
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public static ByteBuffer uncompress(final ByteBuffer in, ByteBuffer out) {
        if (in.remaining() == 0)
            return ByteBuffer.allocate(0);

        final ORDER order = ORDER.fromInt(in.get());

        in.order(ByteOrder.LITTLE_ENDIAN);
        final int in_sz = in.getInt();
        if (in_sz != in.remaining() - RAW_BYTE_LENGTH)
            throw new RuntimeException("Incorrect input length.");
        final int out_sz = in.getInt();
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

    public static ByteBuffer compress(final ByteBuffer in, final ORDER order, final ByteBuffer out) {
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

    private static ByteBuffer allocateIfNeeded(final int in_size,
                                               final ByteBuffer out_buf) {
        final int compressedSize = (int) (1.05 * in_size + 257 * 257 * 3 + 4);
        if (out_buf == null)
            return ByteBuffer.allocate(compressedSize);
        if (out_buf.remaining() < compressedSize)
            throw new RuntimeException("Insufficient buffer size.");
        out_buf.order(ByteOrder.LITTLE_ENDIAN);
        return out_buf;
    }

    private static ByteBuffer encode_order0_way4(final ByteBuffer in,
                                                 ByteBuffer out_buf) {
        final int in_size = in.remaining();
        out_buf = allocateIfNeeded(in_size, out_buf);
        final int freqTableStart = PREFIX_BYTE_LENGTH;
        out_buf.position(freqTableStart);

        final int[] F = Frequencies.calcFrequencies_o0(in);
        final RansEncSymbol[] syms = Frequencies.buildSyms_o0(F);

        final ByteBuffer cp = out_buf.slice();
        final int frequencyTable_size = Frequencies.writeFrequencies_o0(cp, F);

        in.rewind();
        final int compressedBlob_size = E04.compress(in, syms, cp);

        finalizeCompressed(0, out_buf, in_size, frequencyTable_size,
                compressedBlob_size);
        return out_buf;
    }

    private static ByteBuffer encode_order1_way4(final ByteBuffer in,
                                                 ByteBuffer out_buf) {
        final int in_size = in.remaining();
        out_buf = allocateIfNeeded(in_size, out_buf);
        final int freqTableStart = PREFIX_BYTE_LENGTH;
        out_buf.position(freqTableStart);

        final int[][] F = Frequencies.calcFrequencies_o1(in);
        final RansEncSymbol[][] syms = Frequencies.buildSyms_o1(F);

        final ByteBuffer cp = out_buf.slice();
        final int frequencyTable_size = Frequencies.writeFrequencies_o1(cp, F);

        in.rewind();
        final int compressedBlob_size = E14.compress(in, syms, cp);

        finalizeCompressed(1, out_buf, in_size, frequencyTable_size,
                compressedBlob_size);
        return out_buf;
    }

    private static void finalizeCompressed(final int order, final ByteBuffer out_buf,
                                           final int in_size, final int frequencyTable_size, final int compressedBlob_size) {
        out_buf.limit(PREFIX_BYTE_LENGTH + frequencyTable_size
                + compressedBlob_size);
        out_buf.put(0, (byte) order);
        out_buf.order(ByteOrder.LITTLE_ENDIAN);
        final int compressedSizeOffset = ORDER_BYTE_LENGTH;
        out_buf.putInt(compressedSizeOffset, frequencyTable_size
                + compressedBlob_size);
        final int rawSizeOffset = ORDER_BYTE_LENGTH + COMPRESSED_BYTE_LENGTH;
        out_buf.putInt(rawSizeOffset, in_size);
        out_buf.rewind();
    }

    private static ByteBuffer uncompress_order0_way4(final ByteBuffer in,
                                                     final ByteBuffer out) {
        in.order(ByteOrder.LITTLE_ENDIAN);
        final Decoding.AriDecoder D = new Decoding.AriDecoder();
        final Decoding.RansDecSymbol[] syms = new Decoding.RansDecSymbol[256];
        for (int i = 0; i < syms.length; i++)
            syms[i] = new Decoding.RansDecSymbol();

        Frequencies.readStats_o0(in, D, syms);

        D04.uncompress(in, D, syms, out);

        return out;
    }

    private static ByteBuffer uncompress_order1_way4(final ByteBuffer in,
                                                     final ByteBuffer out_buf) {
        final Decoding.AriDecoder[] D = new Decoding.AriDecoder[256];
        final Decoding.RansDecSymbol[][] syms = new Decoding.RansDecSymbol[256][256];
        for (int i = 0; i < syms.length; i++)
            for (int j = 0; j < syms[i].length; j++)
                syms[i][j] = new Decoding.RansDecSymbol();
        Frequencies.readStats_o1(in, D, syms);

        D14.uncompress(in, out_buf, D, syms);

        return out_buf;
    }
}
