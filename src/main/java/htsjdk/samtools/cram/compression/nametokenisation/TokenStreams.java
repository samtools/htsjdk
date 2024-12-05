package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;

import java.nio.ByteBuffer;

public class TokenStreams {
    public static final byte TOKEN_TYPE = 0x00;
    public static final byte TOKEN_STRING = 0x01;
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

    private static final int NEW_POSITION_FLAG_MASK = 0x80;
    private static final int DUP_PREVIOUS_STREAM_FLAG_MASK = 0x40;
    private static final int TYPE_TOKEN_FLAG_MASK = 0x3F;

    // choose an initial estimate of the number of expected token positions, which we use to preallocate lists
    private static final int DEFAULT_NUMBER_OF_TOKEN_POSITIONS = 32;
    private static int POSITION_INCREMENT = 2; // reallocate by 2 every time we exceed the initial estimate

    // called 'B' in the spec, (conceptually) indexed as tokenStreams(tokenType, tokenPos)
    private final ByteBuffer[][] tokenStreams;

    //TODO: its unfortunate that this class is only used by decode, but not encode

    public TokenStreams(final ByteBuffer inputByteBuffer, final int useArith, final int numNames) {
        // The outer index corresponds to type of the token
        // and the inner index corresponds to the position of the token in a name (starting at index 1)
        // Each element in this list of lists is a Token (ie, a ByteBuffer)

        // TokenStreams[type = TOKEN_TYPE(0x00), pos = 0] contains a ByteBuffer of length = number of names
        // This ByteBuffer helps determine if each of the names is a TOKEN_DUP or TOKEN_DIFF
        // when compared with the previous name

        // TokenStreams[type = TOKEN_TYPE(0x00), pos = all except 0]
        // contains a ByteBuffer of length = number of names
        // This ByteBuffer helps determine the type of each of the token at the specified pos

        tokenStreams = new ByteBuffer[TOTAL_TOKEN_TYPES][];
        int estimatedNumberOfPositions = DEFAULT_NUMBER_OF_TOKEN_POSITIONS;
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            tokenStreams[i] = new ByteBuffer[estimatedNumberOfPositions];
        }
        //System.out.println("Start new token streams");
        int tokenPosition = -1;
        while (inputByteBuffer.hasRemaining()) {
            //System.out.println("Pos: " + tokenPosition);
            final byte tokenTypeFlags = inputByteBuffer.get();

            final boolean isNewPosition = ((tokenTypeFlags & NEW_POSITION_FLAG_MASK) != 0);
            final boolean isDupStream = ((tokenTypeFlags & DUP_PREVIOUS_STREAM_FLAG_MASK) != 0);
            final int tokenType = tokenTypeFlags & TYPE_TOKEN_FLAG_MASK;
            if (tokenType < 0 || tokenType > TOKEN_END) {
                throw new CRAMException("Invalid name tokenizer token stream type: " + tokenType);
            }

            if (isNewPosition) {
                tokenPosition++;
                if (tokenPosition > estimatedNumberOfPositions) {
                //if (tokenPosition > 0) {
                    throw new IllegalStateException("finish me");
                    // If newToken and not the first newToken
                    // Ensure that the size of tokenStream for each type of token = tokenPosition
                    // by adding an empty ByteBuffer if needed
//                    for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
//                        final ByteBuffer[] currTokenColumn = tokenStreams[i];
//                        currentTokenColumn[tokenPosition] =
//                        if (currTokenColumn.length < tokenPosition) {
//                            currTokenColumn.add(ByteBuffer.allocate(0));
//                        }
//                        if (currTokenStream.size() < tokenPosition) {
//                            throw new CRAMException("TokenStream is missing token(s) at token type: " + i);
//                        }
//                    }
                }
            }
            if (isNewPosition && (tokenType != TOKEN_TYPE)) {
                // Spec: if we have a byte stream B[5,DIGITS] but no B[5,TYPE]
                // then we assume the contents of B[5,TYPE] consist of one DIGITS tokenType
                // followed by as many MATCH types as are needed.
                final ByteBuffer typeDataByteBuffer = ByteBuffer.allocate(numNames);
                for (int i = 0; i < numNames; i++) {
                    typeDataByteBuffer.put((byte) TOKEN_MATCH);
                }
                typeDataByteBuffer.rewind();
                typeDataByteBuffer.put(0, (byte) tokenType);
                //TOTO: tokenPosition--?
                tokenStreams[0][tokenPosition] = typeDataByteBuffer;
            }
            if (isDupStream) {
                // duplicate a previous stream
                final int dupPosition = inputByteBuffer.get() & 0xFF;
                final int dupType = inputByteBuffer.get() & 0xFF;
                final ByteBuffer dupTokenStream = tokenStreams[dupType][dupPosition].duplicate();
                tokenStreams[tokenType][tokenPosition] = dupTokenStream;
            } else {
                // retrieve and decompress another input stream
                final int clen = CompressionUtils.readUint7(inputByteBuffer);
                final byte[] compressedTokenStream = new byte[clen];
                inputByteBuffer.get(compressedTokenStream, 0, clen); // offset in the dst byte array
                final ByteBuffer decompressedTokenStream;
                if (useArith != 0) {
                    final RangeDecode rangeDecode = new RangeDecode();
                    decompressedTokenStream = rangeDecode.uncompress(ByteBuffer.wrap(compressedTokenStream));
                } else {
                    final RANSNx16Decode ransDecode = new RANSNx16Decode();
                    decompressedTokenStream = ransDecode.uncompress(ByteBuffer.wrap(compressedTokenStream));
                }
                getTokenStreamByType(tokenType)[tokenPosition] = decompressedTokenStream;
            }
        }
        displayTokenStreamSizes();
        //shrinkTokenStreams();
        //displayTokenStreamSizes();
    }

    private void displayTokenStreamSizes() {
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            int nCols = tokenStreams[i].length;
            System.out.println(String.format("Row %d %s nCols: %d", i, typeToString(i), nCols));
            for (int j = 0; j < nCols; j++) {
                final ByteBuffer bf = tokenStreams[i][j];
                if (bf == null) {
                    System.out.print("null ");
                } else {
                    System.out.print(String.format("%d ", bf.limit()));
                }
            }
            System.out.println();
        }
    }
    private void shrinkTokenStreams() {
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            int nCols = tokenStreams[i].length;
            //System.out.println(String.format("Row %d %s nCols: %d", i, typeToString(i), nCols));
            for (int j = 0; j < nCols; j++) {
                final ByteBuffer bf = tokenStreams[i][j];
                if (bf.limit() == 0) {
                    tokenStreams[i][j] = null;
                }
            }
        }
    }

    private String typeToString(int i) {
        switch (i) {
            case TOKEN_TYPE:
                return "TOKEN_TYPE";
            case TOKEN_STRING:
                return "TOKEN_STRING";
            case TOKEN_CHAR:
                return "TOKEN_CHAR";
            case TOKEN_DIGITS0:
                return "TOKEN_DIGITS0";
            case TOKEN_DZLEN:
                return "TOKEN_DZLEN";
            case TOKEN_DUP:
                return "TOKEN_DUP";
            case TOKEN_DIFF:
                return "TOKEN_DIFF";
            case TOKEN_DIGITS:
                return "TOKEN_DIGITS";
            case TOKEN_DELTA:
                return "TOKEN_DELTA";
            case TOKEN_DELTA0:
                return "TOKEN_DELTA0";
            case TOKEN_MATCH:
                return "TOKEN_MATCH";
            case TOKEN_END:
                return "TOKEN_END";
            case 11: //NOP
                return "NOP";
            default:
                throw new CRAMException("Invalid name tokenizer Token tokenType: " + i);
        }
    }

    public ByteBuffer[] getTokenStreamByType(final int tokenType) {
        return tokenStreams[tokenType];
    }

    public ByteBuffer getTokenStreamByteBuffer(final int tokenType, final int tokenPosition) {
        return getTokenStreamByType(tokenType)[tokenPosition];
    }
}