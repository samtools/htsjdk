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

    public static final byte TOKEN_TYPE = 0x00;
    public static final byte TOKEN_STRING  = 0x01;
    public static final byte TOKEN_CHAR = 0x02;
    public static final byte TOKEN_DIGITS0 = 0x03;
    public static final byte TOKEN_DZLEN = 0x04;
    public static final byte TOKEN_DUP = 0x05;
    public static final byte TOKEN_DIFF = 0x06;
    public static final byte TOKEN_DIGITS = 0x07;
    public static final byte TOKEN_DELTA = 0x08;
    public static final byte TOKEN_DELTA0 = 0x09;
    public static final byte TOKEN_MATCH = 0x0A;
    public static final byte TOKEN_END = 0x0C;
    public static final int TOTAL_TOKEN_TYPES = 13;

    private static final int NEW_TOKEN_FLAG_MASK = 0x80;
    private static final int DUP_TOKEN_FLAG_MASK = 0x40;
    private static final int TYPE_TOKEN_FLAG_MASK = 0x3F;

    private final List<List<Token>> tokenStreams;

    public TokenStreams() {
        tokenStreams = new ArrayList<>(TOTAL_TOKEN_TYPES);
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            tokenStreams.add(new ArrayList<>());
        }
    }

    public TokenStreams(final ByteBuffer inputByteBuffer, final int useArith, final int numNames) {
        // The outer index corresponds to type of the token
        // and the inner index corresponds to the position of the token in a name (starting at index 1)
        // Each element in this list of lists is a Token (ie, a ByteBuffer)

        // TokenStreams[type = TOKEN_TYPE(0x00), pos = 0] contains a ByteBuffer of length = number of names
        // This ByteBuffer helps determine if each of the names is a TOKEN_DUP or TOKEN_DIFF
        // when compared with the previous name

        // TokenStreams[type = TOKEN_TYPE(0x00), pos = all except 0]
        // contains a ByteBuffer of length = number of names
        // This ByteBuffer helps determine the type of each of the token at the specicfied pos

        this();
        int tokenPosition = -1;
        while (inputByteBuffer.hasRemaining()) {
            final byte tokenTypeFlags = inputByteBuffer.get();
            final boolean isNewToken = ((tokenTypeFlags & NEW_TOKEN_FLAG_MASK) != 0);
            final boolean isDupToken = ((tokenTypeFlags & DUP_TOKEN_FLAG_MASK) != 0);
            final int tokenType = (tokenTypeFlags & TYPE_TOKEN_FLAG_MASK);
            if (tokenType < 0 || tokenType > TOKEN_END) {
                throw new CRAMException("Invalid Token tokenType: " + tokenType);
            }
            if (isNewToken) {
                tokenPosition++;
                if (tokenPosition > 0) {
                    // If newToken and not the first newToken
                    // Ensure that the size of tokenStream for each type of token = tokenPosition
                    // by adding an empty ByteBuffer if needed
                    for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
                        final List<Token> currTokenStream = tokenStreams.get(i);
                        if (currTokenStream.size() < tokenPosition) {
                            currTokenStream.add(new Token(ByteBuffer.allocate(0)));
                        }
                        if (currTokenStream.size() < tokenPosition) {
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
                this.getTokenStreamByType(tokenType).add(tokenPosition,new Token(uncompressedDataByteBuffer));
            }
        }
    }

    public List<Token> getTokenStreamByType(final int tokenType) {
        return tokenStreams.get(tokenType);
    }

    public ByteBuffer getTokenStreamByteBuffer(final int tokenPosition, final int tokenType) {
        return tokenStreams.get(tokenType).get(tokenPosition).getByteBuffer();
    }
}