package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.nametokenisation.tokens.EncodeToken;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;
import htsjdk.samtools.cram.structure.CRAMEncodingStrategy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A very naive implementation of a name tokenization encoder.
 *
 * It does not currently:
 *
 * - recognize and encode for duplicate streams (that is, it does not ever set the DUP_PREVIOUS_STREAM_FLAG_MASK flag)
 * - detect and encode for streams that are all match, as mentioned in the spec ("if a byte stream of token types
 *   is entirely MATCH apart from the very first value it is discarded. It is possible to regenerate this during decode
 *   by observing the other byte streams.")
 */
public class NameTokenisationEncode {
    private final static String READ_NAME_TOK_REGEX = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
    private final static Pattern READ_NAME_PATTERN = Pattern.compile(READ_NAME_TOK_REGEX);

    private final static String DIGITS0_REGEX = "^0+[0-9]*$";
    private final static Pattern DIGITS0_PATTERN = Pattern.compile(DIGITS0_REGEX);

    private final static String DIGITS_REGEX = "^[0-9]+$";
    private final static Pattern DIGITS_PATTERN = Pattern.compile(DIGITS_REGEX);

    private int maxPositions; // the maximum number of tokenised columns seen across all names
    private int maxStringValueLength; // longest *String* value for any token

    /**
     * Compress the input buffer of read names.
     * @param inBuffer formatted as read names separated by the byte specified by the nameSeparator parameter
     * @param useArith true if the arithmetic coder should be used
     * @param nameSeparator name separator
     * @return the compressed buffer
     */
    public ByteBuffer compress(final ByteBuffer inBuffer, final boolean useArith, final byte nameSeparator) {
        // strictly speaking, keeping this list isn't necessary, but since the first thing that we need to write
        // to the output stream is the number of names, we have to scan the entire input anyway to count them,
        // so just extract them while we're scanning
        final List<String> namesToEncode = extractInputNames(
                inBuffer,
                CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE,
                nameSeparator);
        final int numNames = namesToEncode.size();
        final int uncompressedDataSize = Integer.max(0, inBuffer.limit());

        // pre-allocate the output buffer; we don't know how big it will be. instead of implementing a wrapper around
        // the ByteBuffer to allow for dynamic resizing, over-allocate; if the writer ever exceeds this, writing will
        // fail with an exception, but it would indicate a serious error somewhere in the writer
        final int outputLen = (inBuffer.limit() * 2) + 10000; // include a constant in case input is empty
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer(outputLen);
        outBuffer.putInt(uncompressedDataSize);
        outBuffer.putInt(numNames);
        outBuffer.put((byte)(useArith == true ? 1 : 0));

        // note that using a map with a read name key is a little sketchy here, since read names can be duplicates;
        // but its fine since it doesn't really matter which index is recorded here - any one will suffice
        final HashMap<String, Integer> nameIndexMap = new HashMap<>();
        final int[] tokenFrequencies = new int[256]; // DELTA vs DIGIT frequency
        // keep track of the list of encoded tokens for each name
        final List<List<EncodeToken>> encodedTokensByName = new ArrayList<>(numNames);

        for (int nameIndex = 0; nameIndex < numNames; nameIndex++) {
            encodedTokensByName.add(
                    tokeniseName(
                            namesToEncode.get(nameIndex),
                            nameIndex,
                            encodedTokensByName,
                            nameIndexMap,
                            tokenFrequencies)
            );
        }

        for (int position = 0; position < maxPositions; position++) {
            final List<ByteBuffer> streamsForPosition = distributeTokensForPosition(
                    encodedTokensByName,
                    position,
                    numNames);
            serializeTokenStreams(streamsForPosition, outBuffer, useArith);
        }

        // set the limit to current position (important because we initially dramatically over-allocated the buffer,
        // so make sure the caller doesn't go past the actual limit), and reset position to '0'
        outBuffer.flip();
        return outBuffer;
    }

    // return the encoded token list for a single name
    private List<EncodeToken> tokeniseName(
            final String name,
            final int nameIndex,
            final List<List<EncodeToken>> encodedTokensByName,
            final HashMap<String, Integer> nameIndexMap,
            final int[] tokenFrequencies) {

        if (nameIndexMap.containsKey(name)) {
            // duplicate name, there is no need to tokenise the name, just encode the index of the duplicate
            final String duplicateIndex = String.valueOf(nameIndex - nameIndexMap.get(name));
            return List.of(new EncodeToken(TokenStreams.TOKEN_DUP, duplicateIndex));
        }

        final List<EncodeToken> encodedTokens = new ArrayList<>(NameTokenisationDecode.DEFAULT_POSITION_ALLOCATION);

        // if this name is the first name, the diff value must be 0; otherwise for now use a naive
        // strategy and only/always diff against the (immediately) preceding name
        encodedTokens.add(0, new EncodeToken(TokenStreams.TOKEN_DIFF, String.valueOf(nameIndex == 0 ? 0 : 1)));
        nameIndexMap.put(name, nameIndex);

        // tokenise the current name
        final Matcher matcher = READ_NAME_PATTERN.matcher(name);
        for (int i = 1; matcher.find(); i++) {
            byte type = TokenStreams.TOKEN_STRING;
            final String fragmentValue = matcher.group(); // absolute value of the token
            String relativeValue = fragmentValue; // relative value of the token (comparing to prev name's token at the same token position)

            if (DIGITS0_PATTERN.matcher(fragmentValue).matches()) {
                type = TokenStreams.TOKEN_DIGITS0;
            } else if (DIGITS_PATTERN.matcher(fragmentValue).matches()) {
                type = TokenStreams.TOKEN_DIGITS;
            } else if (fragmentValue.length() == 1) {
                type = TokenStreams.TOKEN_CHAR;
            }

            // compare the current token with token from the previous name (this naive implementation always
            // compares against last name only)
            final int prevNameIndex = nameIndex - 1;
            if (prevNameIndex >= 0 && encodedTokensByName.get(prevNameIndex).size() > i) {
                //there exists a token at the corresponding position of the previous name
                final EncodeToken prevToken = encodedTokensByName.get(prevNameIndex).get(i);
                if (prevToken.getActualValue().equals(fragmentValue)) {
                    // identical to the previous name's token in this position
                    type = TokenStreams.TOKEN_MATCH;
                    relativeValue = null;
                } else if (type==TokenStreams.TOKEN_DIGITS &&
                        (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA)) {
                    int d = Integer.parseInt(relativeValue) - Integer.parseInt(prevToken.getActualValue());
                    tokenFrequencies[i]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[i] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA;
                        relativeValue = String.valueOf(d);
                    }
                } else if (type == TokenStreams.TOKEN_DIGITS0 &&
                        prevToken.getActualValue().length() == relativeValue.length() &&
                        (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS0 || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA0)) {
                    int d = Integer.parseInt(relativeValue) - Integer.parseInt(prevToken.getActualValue());
                    tokenFrequencies[i]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[i] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA0;
                        relativeValue = String.valueOf(d);
                    }
                }
            }
            encodedTokens.add(new EncodeToken(type, fragmentValue, relativeValue));
            if (type == TokenStreams.TOKEN_STRING) {
                // update our longest value length, adding one for the null terminator we'll embed in the stream
                maxStringValueLength = Math.max(maxStringValueLength, fragmentValue.length() + 1);
            }
        }

        encodedTokens.add(new EncodeToken(TokenStreams.TOKEN_END));

        // keep track of the longest list of encoded tokens (this is basically the number of columns/fragments)
        // for use by downstream buffer allocations
        final int currMaxPositions = encodedTokens.size();
        if (maxPositions < currMaxPositions) {
            maxPositions = currMaxPositions;
        }

        return encodedTokens;
    }

    // extract the individual names from the input buffer and return in a list
    private static List<String> extractInputNames(
            final ByteBuffer inBuffer,
            final int preAllocationSize,
            final byte nameSeparator) {
        final List<String> names = new ArrayList(preAllocationSize);
        for (int lastPosition = inBuffer.position(); inBuffer.hasRemaining();) {
            final byte currentByte = inBuffer.get();
            if (currentByte == nameSeparator) {
                final int length = inBuffer.position() - lastPosition;
                final byte[] bytes = new byte[length];
                inBuffer.position(lastPosition);
                inBuffer.get(bytes, 0, length);  // consume the string + the terminator
                names.add(new String(
                        bytes,
                        0,
                        length - 1, // don't include the separator in the string
                        StandardCharsets.UTF_8));
                lastPosition = inBuffer.position();
            }
        }
        return names;
    }

    // go through all names, appending the encoded token for the current position to the appropriate byte stream
    // NOTE: the calling code relies on the fact that on return, the position of each ByteBuffer corresponds
    // to the location of the end of the stream
    private List<ByteBuffer> distributeTokensForPosition(
            final List<List<EncodeToken>> tokenAccumulator,
            final int tokenPosition,
            final int numNames) {
        // create a list, but don't preallocate the actual ByteBuffers until we actually need them (except
        // for the TOKEN_TYPE stream, which we'll need for sure), since this list can be quite sparse
        final List<ByteBuffer> tokenStreams = Arrays.asList(new ByteBuffer[TokenStreams.TOTAL_TOKEN_TYPES]);
        // we need 1 byte per name (so, numNames) for the type stream; the distance values go in the
        // individual streams (dup or diff)
        getByteBufferFor(tokenStreams, TokenStreams.TOKEN_TYPE, numNames);

        // for each name, push the name's encoded token for this position on to the end of the appropriate byte stream
        for (int n = 0; n < numNames; n++) {
            final List<EncodeToken> tokensForName = tokenAccumulator.get(n);
            if (tokenPosition > 0 && tokensForName.get(0).getTokenType() == TokenStreams.TOKEN_DUP) {
                // there is nothing more to do for this name, since it's a duplicate of a previous name
                // (the index of which was written to the (dup) stream when position 0 was (previously) processed
                continue;
            }
            if (tokensForName.size() <= tokenPosition) {
                // no encoded token for this position for this name
                continue;
            }

            // write the token type for this name/position to the TOKEN_TYPE stream
            final EncodeToken encodeToken = tokensForName.get(tokenPosition);
            final byte type = encodeToken.getTokenType();
            tokenStreams.get(TokenStreams.TOKEN_TYPE).put(type);

            // now write out the associated value for any token type that has one
            // whenever we allocate a new bytebuffer, size it according to the max that it can ever
            // be; this might be over-allocating, but that's better than under-allocating, and the
            // stream will be trimmed when it is written out
            switch (type) {
                case TokenStreams.TOKEN_DIFF:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIFF, numNames * 4)
                            .putInt(Integer.parseInt(encodeToken.getRelativeValue()));
                    break;

                case TokenStreams.TOKEN_DUP:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DUP, numNames * 4)
                            .putInt(Integer.parseInt(encodeToken.getRelativeValue()));
                    break;

                case TokenStreams.TOKEN_STRING:
                    writeString(
                            getByteBufferFor(tokenStreams, TokenStreams.TOKEN_STRING, numNames * maxStringValueLength),
                            encodeToken.getRelativeValue()
                    );
                    break;

                case TokenStreams.TOKEN_CHAR:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_CHAR, numNames * 1)
                            .put((byte) encodeToken.getRelativeValue().charAt(0));
                    break;

                case TokenStreams.TOKEN_DIGITS:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIGITS, numNames * 4)
                            .putInt(Integer.parseInt(encodeToken.getRelativeValue()));
                    break;

                case TokenStreams.TOKEN_DIGITS0:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIGITS0, numNames * 4)
                            .putInt(Integer.parseInt(encodeToken.getRelativeValue()));
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DZLEN, numNames)
                        .put((byte) encodeToken.getRelativeValue().length());
                    break;

                case TokenStreams.TOKEN_DELTA:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA, numNames * 1)
                            .put((byte)Integer.parseInt(encodeToken.getRelativeValue()));
                    break;

                case TokenStreams.TOKEN_DELTA0:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA0, numNames * 1)
                            .put((byte)Integer.parseInt(encodeToken.getRelativeValue()));
                    break;

                case TokenStreams.TOKEN_NOP:
                case TokenStreams.TOKEN_MATCH:
                case TokenStreams.TOKEN_END:
                    // noop - token types with no associated value
                    break;

                default:
                    throw new CRAMException("Invalid token type: " + type);
            }
        }
        // since we've over-allocated these streams,we need to set the limit on these streams so that the downstream
        // consumer doesn't try to consume the entire stream,
        for (int i = 0; i < TokenStreams.TOTAL_TOKEN_TYPES; i++) {
            if (tokenStreams.get(i) != null) {
                tokenStreams.get(i).limit(tokenStreams.get(i).position());
            }
        }

        return tokenStreams;
    }

    // since we don't want to allocate a ByteBuffer that we'll never use, allocate them just-in-time
    // as need
    private ByteBuffer getByteBufferFor(
            final List<ByteBuffer> tokenStreams,
            final int tokenType,
            final int requiredSize) {
        if (tokenStreams.get(tokenType) == null) {
            tokenStreams.set(tokenType, CompressionUtils.allocateByteBuffer(requiredSize));
        }
        return tokenStreams.get(tokenType);
    }

    private static void writeString(final ByteBuffer tokenStreamBuffer, final String val) {
        tokenStreamBuffer.put(val.getBytes());
        tokenStreamBuffer.put((byte) 0);
    }

    private static ByteBuffer tryCompress(final ByteBuffer nameTokenStream, final boolean useArith) {
        // compress with different formatFlags
        // and return the compressed output ByteBuffer with the least number of bytes
        int bestCompressedLength = 1 << 30;
        ByteBuffer compressedByteBuffer = null;

        if (useArith == true) { // use the range encoder
            final int[] rangeEncoderFlagsSets = {
                    0,
                    RangeParams.ORDER_FLAG_MASK,
                    RangeParams.RLE_FLAG_MASK,  //64
                    RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, //65
                    RangeParams.PACK_FLAG_MASK, //128,
                    RangeParams.PACK_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, //129
                    // we don't include stripe here since it's not implemented for write
                    RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.ORDER_FLAG_MASK // 193+8
            };
            for (int rangeEncoderFlagSet : rangeEncoderFlagsSets) {
                if ((rangeEncoderFlagSet & RangeParams.ORDER_FLAG_MASK) != 0 && nameTokenStream.remaining() < 100) {
                    continue;
                }
                if ((rangeEncoderFlagSet & RangeParams.STRIPE_FLAG_MASK) != 0 && (nameTokenStream.remaining() % 4) != 0) {
                    continue;
                }
                // Encode using Range
                final RangeEncode rangeEncode = new RangeEncode();
                nameTokenStream.rewind();
                final ByteBuffer tmpByteBuffer = rangeEncode.compress(nameTokenStream, new RangeParams(rangeEncoderFlagSet));
                if (bestCompressedLength > tmpByteBuffer.limit()) {
                    bestCompressedLength = tmpByteBuffer.limit();
                    compressedByteBuffer = tmpByteBuffer;
                }
            }
        } else {
            final int[] ransNx16FlagsSets = {
                    0,
                    RANSNx16Params.ORDER_FLAG_MASK,
                    RANSNx16Params.RLE_FLAG_MASK,  //64
                    RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK, //65
                    RANSNx16Params.PACK_FLAG_MASK, //128,
                    RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK, //129
                    // we don't include stripe here since it's not implemented for write
                    RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK // 193+8
            };
            for (int ransNx16FlagSet : ransNx16FlagsSets) {
                if ((ransNx16FlagSet & RANSNx16Params.ORDER_FLAG_MASK) != 0 && nameTokenStream.remaining() < 100) {
                    continue;
                }
                if ((ransNx16FlagSet & RANSNx16Params.STRIPE_FLAG_MASK) != 0 && (nameTokenStream.remaining() % 4) != 0) {
                    continue;
                }
                // Encode using RANSnx16
                final RANSNx16Encode ransEncode = new RANSNx16Encode();
                nameTokenStream.rewind();
                final ByteBuffer tmpByteBuffer = ransEncode.compress(nameTokenStream, new RANSNx16Params(ransNx16FlagSet));
                if (bestCompressedLength > tmpByteBuffer.limit()) {
                    bestCompressedLength = tmpByteBuffer.limit();
                    compressedByteBuffer = tmpByteBuffer;
                }
            }
        }
        return compressedByteBuffer;
    }

    private void serializeTokenStreams(
            final List<ByteBuffer> tokenStreams,
            final ByteBuffer outBuffer,
            final boolean useArith) {
        // Compress and serialise the non-null tokenStreams
        for (int tokenStreamType = 0; tokenStreamType <= TokenStreams.TOKEN_END; tokenStreamType++) {
            final ByteBuffer tokenBytes = tokenStreams.get(tokenStreamType);
            if (tokenBytes != null && tokenBytes.position() > 0) {
                // if this encoder was aware of duplicate streams, we would need to detect and encode them
                // here, and set the DUP_PREVIOUS_STREAM_FLAG_MASK bit
                outBuffer.put((byte) (tokenStreamType | (tokenStreamType == 0 ? TokenStreams.NEW_POSITION_FLAG_MASK : 0)));
                final ByteBuffer tempOutByteBuffer = tryCompress(tokenBytes, useArith);
                CompressionUtils.writeUint7(tempOutByteBuffer.limit(), outBuffer);
                outBuffer.put(tempOutByteBuffer);
            }
        }
    }
}