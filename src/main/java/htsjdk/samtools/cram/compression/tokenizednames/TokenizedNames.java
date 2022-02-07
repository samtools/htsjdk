package htsjdk.samtools.cram.compression.tokenizednames;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TokenizedNames {
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    public ByteBuffer uncompress(final ByteBuffer inBuffer) {
        //TODO: these streams are NOT entirely little ENDIAN, although the CRAM spec says the should be
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);

        int notUsedForAnything = inBuffer.getInt(); // unused int ? length ?
        int nNames = inBuffer.getInt();  // number of names
        boolean useArithmeticCoder = inBuffer.get() == 0 ? false : true;

        //TODO: these streams are NOT little ENDIAN, although the CRAM spec says the should be
        // this needs to be fixed
        inBuffer.order(ByteOrder.BIG_ENDIAN);

        final  ByteBuffer[][] B = getTokenArrays(inBuffer, nNames, useArithmeticCoder);
        final String[] N = new String[nNames];
        final String[][] T = new String[nNames][];

        String str = "";
        for (int i = 0; i < nNames; i++) {
            //TODO: why does this accumulate the names in the return string AND in N ?
            str += decodeSingleName(B, N, T, i) + "\n";
        }

        return ByteBuffer.wrap(str.getBytes());
    }

    private String decodeSingleName(final ByteBuffer[][] B, String[] N, String[][] T, final int n) {
        byte type = B[0][NameTokens.TYPE.ordinal()].get();

        //B[0][type].order(ByteOrder.BIG_ENDIAN);
        final int dist = B[0][type].getInt();

        final int m = n - dist;

        if (type == NameTokens.DUP.ordinal()) {
            N[n] = N[m];
            T[n] = T[m];
            return N[n];
        }

        int t = 1;
        N[n] = "";
        T[n] = new String[255];
        do {
            type = B[t][NameTokens.TYPE.ordinal()].get();
            final NameTokens nt = NameTokens.byId(type);

            switch(nt) {
                case CHAR:
                    //TODO: string concat  is super inefficient
                    T[n][t] = T[n][t] + B[t][NameTokens.CHAR.ordinal()].getChar();
                    break;

                case STRING:
                    T[n][t] = readString(B[t][NameTokens.STRING.ordinal()]);
                    break;

                case DIGITS:
                    T[n][t] = T[n][t] + B[t][NameTokens.DIGITS.ordinal()].getInt();
                    break;

                case DIGITS0: {
                    int value = B[t][NameTokens.DIGITS0.ordinal()].getInt();
                    byte l = B[t][NameTokens.DZLEN.ordinal()].get();
                    final String fmt = String.format("%s%dd", "%0", l);
                    T[n][t] = String.format(fmt, value);
                    break;
                }

                case DELTA:
                    //TODO: >> 0 converts to int in javascript ? or something ?
                    //T[n][t] = (T[m][t]>>0) + B[t][TOK_DELTA].ReadByte()
                    //TODO: these type conversions ar super inefficient
                    T[n][t] = Integer.toString(Integer.valueOf(T[m][t]) + B[t][NameTokens.DELTA.ordinal()].get());
                    break;

                case DELTA0: {
                    //var d = (T[m][t]>>0) + B[t][TOK_DELTA0].ReadByte()
                    int v = Integer.valueOf(T[m][t]) + B[t][NameTokens.DELTA0.ordinal()].get();
                    int l = Integer.valueOf(T[m][t]);
                    final String fmt = String.format("%s%dd", "%0", l);
                    T[n][t] = String.format(fmt, v);
                    break;
                }

                case MATCH:
                    T[n][t] = T[m][t];
                    break;

                default:
                    T[n][t] = "";
                    break;
            }

            N[n] += T[n][t++];
        } while (type != NameTokens.END.ordinal());

        return N[n];
    }

    final String readString(final ByteBuffer byteBuffer) {
        byte b;
        try (final ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            while ((b = byteBuffer.get()) != 0) {
                baos.write(b);
            }
            return new String(baos.toByteArray());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

//    function DecodeSingleName(B, N, T, n) {
//        var type = B[0][TOK_TYPE].ReadByte()
//        var dist = B[0][type].ReadUint32()
//        var m = n - dist
//
//        if (type == TOK_DUP) {
//            N[n] = N[m]
//            T[n] = T[m]
//            return N[n]
//        }
//
//        var t = 1
//        N[n] = ""
//        T[n] = new Array(256)
//        do {
//            type = B[t][TOK_TYPE].ReadByte()
//
//            switch(type) {
//                case TOK_CHAR:
//                    T[n][t] = B[t][TOK_CHAR].ReadChar()
//                    break
//
//                case TOK_STRING:
//                    T[n][t] = B[t][TOK_STRING].ReadString()
//                    break
//
//                case TOK_DIGITS:
//                    T[n][t] = B[t][TOK_DIGITS].ReadUint32()
//                    break
//
//                case TOK_DIGITS0:
//                    var d = B[t][TOK_DIGITS0].ReadUint32()
//                    var l = B[t][TOK_DZLEN].ReadByte()
//                    T[n][t] = LeftPadNumber(d, l)
//                    break
//
//                case TOK_DELTA:
//                    T[n][t] = (T[m][t]>>0) + B[t][TOK_DELTA].ReadByte()
//                    break
//
//                case TOK_DELTA0:
//                    var d = (T[m][t]>>0) + B[t][TOK_DELTA0].ReadByte()
//                    var l = T[m][t].length
//                    T[n][t] = LeftPadNumber(d, l)
//                    break
//
//                case TOK_MATCH:
//                    T[n][t] = T[m][t]
//                    break
//
//                default:
//                    T[n][t] = ""
//                    break
//            }
//
//            N[n] += T[n][t++]
//        } while (type != TOK_END)
//
//        return N[n]
//    }

    private ByteBuffer[][] getTokenArrays(final ByteBuffer inBuffer, final int nNames, final boolean useArithmeticCoder) {
        final ByteBuffer[][] B = new ByteBuffer[256][]; // TODO: only 129 are actually used ?
        int t = -1;

        while (inBuffer.hasRemaining()) {
            int ttype = inBuffer.get();

            // Bit 6 (64) set indicates that this entire token data stream is a duplicate of one earlier.
            // Bit 7 (128) set indicates the token is the first token at a new position
            int tok_new = ttype & 128;
            int tok_dup = ttype & 64;
            int type = ttype & 63;

            if (tok_new != 0) {
                t++;
                B[t] = new ByteBuffer[13];
            }

            if (type != 0 && tok_new != 0) {
                byte[] M = new byte[nNames - 1];
                for (int i = 0; i < M.length; i++) {
                    //M.fill(TOK_MATCH)
                    M[i] = (byte) NameTokens.MATCH.ordinal();
                }
                B[t][0] = ByteBuffer.wrap(M);
            }

            if (tok_dup != 0) {
                byte dup_pos = inBuffer.get();
                byte dup_type = inBuffer.get();

                //TODO: does this need to make this copy ?
                byte[] copy = toByteArray(B[dup_pos][dup_type]);
                B[t][type] = ByteBuffer.wrap(Arrays.copyOf(copy, copy.length));
            } else {
                int clen = ReadUint7(inBuffer);
                //int clen = inBuffer.get();
                final byte[] embeddedStream = new byte[clen];
                final ByteBuffer byteBuf = inBuffer.get(embeddedStream, 0, clen);

                if (useArithmeticCoder) {
                    //B[t][type] = arith.decode(data)
                    B[t][type] = ByteBuffer.wrap(embeddedStream);
                } else {
                    //B[t][type] = rans.decode(data)
                    B[t][type] = ByteBuffer.wrap(embeddedStream);
                }
                //B[t][type] = new IOStream(B[t][type]);
            }
        }

        return B;
    }

    final int ReadUint7(final ByteBuffer inBuffer) {
        // Variable sized unsigned integers
        int i = 0;
        byte c;
        do {
            c = inBuffer.get();
            i = (i<<7) | (c & 0x7f);
        } while ((c & 0x80) != 0);

        return i;
    }

    public ByteBuffer compress(final ByteBuffer inBuffer, final int level) {
        if (inBuffer.remaining() == 0) {
            return EMPTY_BUFFER;
        }
        return EMPTY_BUFFER;
    }

//        private static ByteBuffer allocateOutputBuffer(final int inSize) {
//            // This calculation is identical to the one in samtools rANS_static.c
//            // Presumably the frequency table (always big enough for order 1) = 257*257, then * 3 for each entry
//            // (byte->symbol, 2 bytes -> scaled frequency), + 9 for the header (order byte, and 2 int lengths
//            // for compressed/uncompressed lengths) ? Plus additional 5% for..., for what ???
//            final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 9);
//            final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize);
//            if (outputBuffer.remaining() < compressedSize) {
//                throw new RuntimeException("Failed to allocate sufficient buffer size for RANS coder.");
//            }
//            outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
//            return outputBuffer;
//        }

    //TODO: there is a copy of the same method in tokenizedNameCompressor (and elsewhere ?)
    private byte[] toByteArray(final ByteBuffer buffer) {
        if (buffer.hasArray() && buffer.arrayOffset() == 0 && buffer.array().length == buffer.limit()) {
            return buffer.array();
        }

        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }

}
