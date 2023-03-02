package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.nametokenisation.tokens.Token;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.rans.RANSDecode;
import htsjdk.samtools.cram.compression.rans.Utils;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class TokenStreams {

    public static final int TOKEN_TYPE = 0;
    public static final int TOKEN_STRING  = 1;
    public static final int TOKEN_CHAR = 2;
    public static final int TOKEN_DIGITS0 = 3;
    public static final int TOKEN_DZLEN = 4;
    public static final int TOKEN_DUP = 5;
    public static final int TOKEN_DIGITS = 7;
    public static final int TOKEN_DELTA = 8;
    public static final int TOKEN_DELTA0 = 9;
    public static final int TOKEN_MATCH = 10;
    public static final int TOKEN_END = 12;

    private static final int TOTAL_TOKEN_TYPES = 13;
    private static final int NEW_TOKEN_FLAG_MASK = 0x80;
    private static final int DUP_TOKEN_FLAG_MASK = 0x40;
    private static final int TYPE_TOKEN_FLAG_MASK = 0x3F;

    private final List<List<Token>> tokenStreams;

    public TokenStreams(final ByteBuffer inputByteBuffer, final int useArith, final int numNames) {
        // TokenStreams is a List of List of Tokens.
        // The outer index corresponds to the type of the token
        // and the inner index corresponds to the index of the current Name in the list of Names
        tokenStreams = new ArrayList<>(TOTAL_TOKEN_TYPES);
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            tokenStreams.add(new ArrayList<>());
        }
        int tokenPosition = -1;
        while (inputByteBuffer.hasRemaining()) {
            final int tokenTypeFlags = inputByteBuffer.get() & 0xFF;
            final boolean isNewToken = ((tokenTypeFlags & NEW_TOKEN_FLAG_MASK) != 0);
            final boolean isDupToken = ((tokenTypeFlags & DUP_TOKEN_FLAG_MASK) != 0);
            final int tokenType = (tokenTypeFlags & TYPE_TOKEN_FLAG_MASK);
            if (tokenType < 0 || tokenType > 13) {
                throw new CRAMException("Invalid Token tokenType: " + tokenType);
            }
            if (isNewToken) {
                tokenPosition++;
                if (tokenPosition > 0) {
                    // If newToken and not the first newToken
                    for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
                        final List<Token> tokenStream = tokenStreams.get(i);
                        if (tokenStream.size() < tokenPosition) {
                            tokenStream.add(new Token(ByteBuffer.allocate(0)));
                        }
                        if (tokenStream.size() < tokenPosition) {
                            throw new CRAMException("TokenStream is missing Token(s) at Token Type: " + i);
                        }
                    }
                }
            }

            if ((isNewToken) && (tokenType != TOKEN_TYPE)) {

                // Spec: if we have a byte stream B5,DIGIT S but no B5,T Y P E
                // then we assume the contents of B5,T Y P E consist of one DIGITS tokenType
                // followed by as many MATCH types as are needed.
                final ByteBuffer typeDataByteBuffer = ByteBuffer.allocate(numNames);
                for (int i = 0; i < numNames; i++) {
                    typeDataByteBuffer.put((byte) TOKEN_MATCH);
                }
                typeDataByteBuffer.rewind();
                typeDataByteBuffer.put(0, (byte) tokenType);
                tokenStreams.get(0).add(new Token(typeDataByteBuffer));
            }
            if (isDupToken) {
                final int dupPosition = inputByteBuffer.get() & 0xFF;
                final int dupType = inputByteBuffer.get() & 0xFF;
                final Token dupTokenStream = new Token(tokenStreams.get(dupType).get(dupPosition).getByteBuffer().duplicate());
                tokenStreams.get(tokenType).add(tokenPosition,dupTokenStream);
            } else {
                final int clen = Utils.readUint7(inputByteBuffer);
                final byte[] dataBytes = new byte[clen];
                inputByteBuffer.get(dataBytes, 0, clen); // offset in the dst byte array
                final ByteBuffer uncompressedDataByteBuffer;
                if (useArith != 0) {
                    RangeDecode rangeDecode = new RangeDecode();
                    uncompressedDataByteBuffer = rangeDecode.uncompress(ByteBuffer.wrap(dataBytes));

                } else {
                    RANSDecode ransdecode = new RANSNx16Decode();
                    uncompressedDataByteBuffer = ransdecode.uncompress(ByteBuffer.wrap(dataBytes));
                }
                tokenStreams.get(tokenType).add(tokenPosition,new Token(uncompressedDataByteBuffer));
            }
        }
    }

    public ByteBuffer getTokenStreamBuffer(final int position, final int type) {
        return tokenStreams.get(type).get(position).getByteBuffer();
    }
}