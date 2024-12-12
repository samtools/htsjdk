package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.range.RangeDecode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Decode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
    public static final byte TOKEN_NOP = 0x0B; //unused
    public static final byte TOKEN_END = 0x0C;

    public static final int TOTAL_TOKEN_TYPES = 13;

    private static final int NEW_POSITION_FLAG_MASK = 0x80;
    private static final int DUP_PREVIOUS_STREAM_FLAG_MASK = 0x40;
    private static final int TYPE_TOKEN_FLAG_MASK = 0x3F;

    // choose an initial estimate of the number of expected token positions, which we use to preallocate lists
    //TODO: force a test of this (again) by setting it to a small number
    private static final int DEFAULT_NUMBER_OF_TOKEN_POSITIONS = 4;
    private static int POSITION_INCREMENT = 2; // expand allocation by 5 every time we exceed the initial estimate

    // called 'B' in the spec, indexed (conceptually) as tokenStreams(tokenPos, tokenType)
    private ByteBuffer[][] tokenStreams;

    /**
     * The outer index corresponds to the (column) position in a name (starting at index 0). The inner
     *  index corresponds to the  token type. Each element in this list of lists is a ByteBuffer of tokens.
     * @param inputByteBuffer - the input buffer of token streams
     * @param useArith - true to use range coding; false for rANS coding
     * @param numNames - the number of read names in the slice for which this token stream is being created
     */
    public TokenStreams(final ByteBuffer inputByteBuffer, final int useArith, final int numNames) {
        // pre-allocate enough room for 32 token positions; we'll reallocate if we exceed this; it is ok if
        // the actual number is less than the pre-allocated amount
        // note that this array is often very sparse (unused cells have null instead of an actual ByteBuffer)
        int numberOfPreallocatedPositions = initializeTokenStreams(DEFAULT_NUMBER_OF_TOKEN_POSITIONS);
        int tokenPosition = -1;
        while (inputByteBuffer.hasRemaining()) {
            final byte tokenTypeFlags = inputByteBuffer.get();
            final boolean startNewPosition = ((tokenTypeFlags & NEW_POSITION_FLAG_MASK) != 0);
            final boolean isDupStream = ((tokenTypeFlags & DUP_PREVIOUS_STREAM_FLAG_MASK) != 0);
            final int tokenType = tokenTypeFlags & TYPE_TOKEN_FLAG_MASK;
            if (tokenType < 0 || tokenType > TOKEN_END) {
                throw new CRAMException("Invalid name tokenizer token stream type: " + tokenType);
            }

            if (startNewPosition) {
                tokenPosition++;
                if (tokenPosition == numberOfPreallocatedPositions) {
                    // if we encounter a new position that is past the number of positions for which we've pre-allocated,
                    // expand our array and copy the old values into it; this has side effects of changing the size of
                    // the array
                    numberOfPreallocatedPositions = reallocateTokenStreams(numberOfPreallocatedPositions);
                }
                if (tokenType != TOKEN_TYPE) {
                    // Spec: if we have a byte stream B[5,DIGITS] but no B[5,TYPE]
                    // then we assume the contents of B[5,TYPE] consist of one DIGITS tokenType
                    // followed by as many MATCH types as are needed.
                    //TODO: this matches the spec, but what is it doing ? is it correct ?
                    final ByteBuffer typeDataByteBuffer = CompressionUtils.allocateByteBuffer(numNames);
                    for (int i = 0; i < numNames; i++) {
                        typeDataByteBuffer.put(TOKEN_MATCH);
                    }
                    typeDataByteBuffer.rewind();
                    typeDataByteBuffer.put(0, (byte) tokenType);
                    tokenStreams[tokenPosition][0] = typeDataByteBuffer;
                }
            }
            if (isDupStream) {
                // duplicate a previous stream
                final int dupPosition = inputByteBuffer.get() & 0xFF;
                final int dupType = inputByteBuffer.get() & 0xFF;
                final ByteBuffer dupTokenStream = tokenStreams[dupPosition][dupType].duplicate();
                dupTokenStream.order(ByteOrder.LITTLE_ENDIAN);
                tokenStreams[tokenPosition][tokenType] = dupTokenStream;
            } else {
                // retrieve and uncompress another input stream
                final int clen = CompressionUtils.readUint7(inputByteBuffer);
                final byte[] compressedTokenStream = new byte[clen];
                inputByteBuffer.get(compressedTokenStream, 0, clen); // offset in the dst byte array
                final ByteBuffer uncompressedTokenStream;
                if (useArith != 0) {
                    final RangeDecode rangeDecode = new RangeDecode();
                    uncompressedTokenStream = rangeDecode.uncompress(ByteBuffer.wrap(compressedTokenStream));
                } else {
                    final RANSNx16Decode ransDecode = new RANSNx16Decode();
                    uncompressedTokenStream = ransDecode.uncompress(ByteBuffer.wrap(compressedTokenStream));
                }
                getTokenStreamsForPos(tokenPosition)[tokenType] = uncompressedTokenStream;
            }
        }
        displayTokenStreamSizes();
    }

    private void displayTokenStreamSizes() {
        for (int t = 0; t < TOTAL_TOKEN_TYPES; t++) {
            System.out.print(String.format("%s ", typeToString(t)));
        }
        System.out.println();
        for (int pos = 0; pos < tokenStreams.length; pos++) {
            System.out.print(String.format("Pos %2d: ", pos));
            for (int typ = 0; typ < tokenStreams[pos].length; typ++) {
                final ByteBuffer bf = tokenStreams[pos][typ];
                if (bf == null) {
                    System.out.print(String.format("%8s", "null"));
                } else {
                    System.out.print(String.format("%8d", bf.limit()));
                }
            }
            System.out.println();
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
            case TOKEN_NOP:
                return "NOP";
            default:
                throw new CRAMException("Invalid name tokenizer tokenType: " + i);
        }
    }

    public ByteBuffer[] getTokenStreamsForPos(final int pos) {
        return tokenStreams[pos];
    }

    public ByteBuffer getTokenStream(final int tokenPosition, final int tokenType) {
        return getTokenStreamsForPos(tokenPosition)[tokenType];
    }

    private int initializeTokenStreams(final int preallocatedPositions) {
        tokenStreams = new ByteBuffer[preallocatedPositions][];
        for (int i = 0; i < preallocatedPositions; i++) {
            tokenStreams[i] = new ByteBuffer[TOTAL_TOKEN_TYPES];
        }
        return preallocatedPositions;
    }

    private int reallocateTokenStreams(final int preallocatedPositions) {
        final ByteBuffer[][] newTokenStreams = new ByteBuffer[preallocatedPositions + POSITION_INCREMENT][];
        for (int i = 0; i < preallocatedPositions; i++) {
            newTokenStreams[i] = tokenStreams[i];
        }
        // populate the new positions with ByteBuffers
        for (int i = 0; i < POSITION_INCREMENT; i++) {
            newTokenStreams[i + preallocatedPositions] = new ByteBuffer[TOTAL_TOKEN_TYPES];
        }
        tokenStreams = newTokenStreams;
        return preallocatedPositions + POSITION_INCREMENT;
    }
}