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

//TODO: implement this from the spec: if a byte stream of token types is entirely MATCH apart from the very
// first value it is discarded. It is possible to regenerate this during decode by observing the other byte streams.
//TODO:its super wasteful (but simpler) to always store the accumulated tokens as Strings, since thisresults
// int lots of String<-> int interconversions

/**
 * Very naive implementation of a name tokenization encoder
 */
public class NameTokenisationEncode {
    private final static String READ_NAME_TOK_REGEX = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
    private final static Pattern READ_NAME_PATTERN = Pattern.compile(READ_NAME_TOK_REGEX);

    private final static String DIGITS0_REGEX = "^0+[0-9]*$";
    private final static Pattern DIGITS0_PATTERN = Pattern.compile(DIGITS0_REGEX);

    private final static String DIGITS_REGEX = "^[0-9]+$";
    private final static Pattern DIGITS_PATTERN = Pattern.compile(DIGITS_REGEX);

    private int maxNumberOfTokens; // the maximum number of tokenised columns seen across all names
    private int maxLength;

    /**
     * Output format is the read names, separated by the NAME_SEPARATOR byte, WITHOUT a terminating separator
     */
    public ByteBuffer compress(final ByteBuffer inBuffer, final boolean useArith) {
        // strictly speaking, keeping these in a list isn't necessary, but we have to scan the entire input
        // anyway to determine the number of names, since that's the first thing that we need to write to the
        // output stream, so extract the names while we're scanning
        final List<String> namesToEncode = extractInputNames(inBuffer, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE);
        final int numNames = namesToEncode.size();
        final int uncompressedInputSize = inBuffer.limit() - numNames - NameTokenisationDecode.UNCOMPRESSED_LENGTH_ADJUSTMENT;

        //TODO: guess max size -> str.length*2 + 10000 (from htscodecs javascript code)
        // what if this is exceeded ?
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer((inBuffer.limit()*2)+10000);
        outBuffer.putInt(uncompressedInputSize);
        outBuffer.putInt(numNames);
        outBuffer.put((byte)(useArith == true ? 1 : 0));

        // keep a List<List<EncodeToken>>, as we also need to store the TOKEN_TYPE, relative value when compared
        // to prev name's token along with the token value.
        final List<List<EncodeToken>> tokenAccumulator = new ArrayList<>(numNames);
        // note that using a map is a little sketchy here, since read names can be duplicates; but its fine since
        // it doesn't really matter which index is recorded here - any one will do
        final HashMap<String, Integer> nameIndexMap = new HashMap<>();
        final int[] tokenFrequencies = new int[256];
        for(int nameIndex = 0; nameIndex < numNames; nameIndex++) {
            tokenAccumulator.add(
                    tokeniseName(
                            namesToEncode.get(nameIndex),
                            nameIndex,
                            tokenAccumulator,
                            nameIndexMap,
                            tokenFrequencies)
            );
        }
        for (int tokenPosition = 0; tokenPosition < maxNumberOfTokens; tokenPosition++) {
            final List<ByteBuffer> tokenStreamForPosition = fillByteStreamsForPosition(tokenPosition, numNames, tokenAccumulator);
            serializeByteStreamsFor(tokenStreamForPosition, useArith, outBuffer);
        }

        // sets limit to current position and position to '0'
        //outBuffer.flip();
        outBuffer.limit(outBuffer.position());
        outBuffer.position(0);
        return outBuffer;
    }

    // return the token list for a new name
    private List<EncodeToken> tokeniseName(
            final String name,
            final int nameIndex,
            final List<List<EncodeToken>> tokenAccumulator,
            final HashMap<String, Integer> nameIndexMap,
            final int[] tokenFrequencies) {
        // create a new token list for this name, and populate position 0 with the token that indicates whether
        // the name is a DIFF or DUP
        final List<EncodeToken> encodedTokens = new ArrayList<>();
        if (nameIndexMap.containsKey(name)) {
            final String indexStr = String.valueOf(nameIndex - nameIndexMap.get(name));
            encodedTokens.add(0, new EncodeToken(indexStr, indexStr, TokenStreams.TOKEN_DUP));
            return encodedTokens;
        }
        //TODO: why does this list need a 0 or 1 to tell if this is the first token ?
        final String indexStr = String.valueOf(nameIndex == 0 ? 0 : 1);
        encodedTokens.add(0,new EncodeToken(indexStr, indexStr, TokenStreams.TOKEN_DIFF));
        nameIndexMap.put(name, nameIndex);

        // tokenise the current name
        final Matcher matcher = READ_NAME_PATTERN.matcher(name);
        final List<String> tok = new ArrayList<>();
        while (matcher.find()) {
            tok.add(matcher.group());
        }

        int currMaxLength = 0;
        for (int i = 0; i < tok.size(); i++) {
            // In the list of tokens, all the tokens are offset by 1
            // because at position "0", we have a token that provides info if the name is a DIFF or DUP
            // token 0 = DIFF vs DUP
            int tokenIndex = i + 1;
            byte type = TokenStreams.TOKEN_STRING;
            final String str = tok.get(i); // absolute value of the token
            String val = str; // relative value of the token (comparing to prevname's token at the same token position)

            if (DIGITS0_PATTERN.matcher(str).matches()) {
                type = TokenStreams.TOKEN_DIGITS0;
            } else if (DIGITS_PATTERN.matcher(str).matches()) {
                type = TokenStreams.TOKEN_DIGITS;
            } else if (str.length() == 1) {
                type = TokenStreams.TOKEN_CHAR;
            }

            // compare the current token with token from the previous name at the current token's index
            // if there exists a previous name and a token at the corresponding index of the previous name
            final int prevNameIndex = nameIndex - 1; // naive implementation where wer always compare against last name only
            if (prevNameIndex >=0 && tokenAccumulator.get(prevNameIndex).size() > tokenIndex) {
                final EncodeToken prevToken = tokenAccumulator.get(prevNameIndex).get(tokenIndex);
                if (prevToken.getActualTokenValue().equals(tok.get(i))) {
                    type = TokenStreams.TOKEN_MATCH;
                    val = "";
                } else if (type==TokenStreams.TOKEN_DIGITS
                        && (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA)) {
                    int v = Integer.parseInt(val);
                    int s = Integer.parseInt(prevToken.getActualTokenValue());
                    int d = v - s;
                    tokenFrequencies[tokenIndex]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[tokenIndex] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA;
                        val = String.valueOf(d);
                    }
                } else if (type == TokenStreams.TOKEN_DIGITS0 && prevToken.getActualTokenValue().length() == val.length()
                        && (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS0 || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA0)) {
                    int d = Integer.parseInt(val) - Integer.parseInt(prevToken.getActualTokenValue());
                    tokenFrequencies[tokenIndex]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[tokenIndex] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA0;
                        val = String.valueOf(d);
                    }
                }
            }
            encodedTokens.add(new EncodeToken(str, val, type));

            if (currMaxLength < val.length() + 3) {
                // TODO: check this? Why isn't unint32 case handled?
                // +3 for integers; 5 -> (Uint32)5 (from htscodecs javascript code)
                //if (max_len < T[n][t].val.length+3)  // +3 for integers; 5 -> (Uint32)5
                //    max_len = T[n][t].val.length+3
                currMaxLength = val.length() + 3;
            }
        }

        encodedTokens.add(new EncodeToken("","",TokenStreams.TOKEN_END));
        final int currMaxToken = encodedTokens.size();
        if (maxNumberOfTokens < currMaxToken) {
            maxNumberOfTokens = currMaxToken;
        }
        if (maxLength < currMaxLength) {
            maxLength = currMaxLength;
        }

        return encodedTokens;
    }

    private static List<String> extractInputNames(final ByteBuffer inBuffer, final int preAllocationSize) {
        final List<String> names = new ArrayList(CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE);
        // extract the individual names from the input buffer
        for (int lastPosition = inBuffer.position(); inBuffer.hasRemaining();) {
            final byte currentByte = inBuffer.get();
            if (currentByte == NameTokenisationDecode.NAME_SEPARATOR || inBuffer.position() == inBuffer.limit()) {
                final int length = inBuffer.position() - lastPosition;
                final byte[] bytes = new byte[length];
                inBuffer.position(lastPosition);
                inBuffer.get(bytes, 0, length);  // consume the string + the terminator
                names.add(new String(
                        bytes,
                        0,
                        //TODO: special case handling end of the buffer, where there is no for the lack of a trailing separator
                        length - (inBuffer.position() == inBuffer.limit() ? 0 : 1),
                        StandardCharsets.UTF_8));
                lastPosition = inBuffer.position();
            }
        }
        return names;
    }

    // NOTE: the calling code relies on the fact that on return, the position of each ByteBuffer corresponds to the
    // end of the corresponding stream
    private List<ByteBuffer> fillByteStreamsForPosition(
            final int tokenPosition,
            final int numNames,
            final List<List<EncodeToken>> tokenAccumulator) {
        // create the outer list, but don't allocate bytestreams until we actually need them, since this list is
        // often quite sparse
        final List<ByteBuffer> tokenStreams = Arrays.asList(new ByteBuffer[TokenStreams.TOTAL_TOKEN_TYPES]);
         // for the token type stream
        getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_TYPE, numNames); // for the one allocation we'll need for sure

        for (int n = 0; n < numNames; n++) {
            final List<EncodeToken> encodedTokensForName = tokenAccumulator.get(n);
            if (tokenPosition > 0 && encodedTokensForName.get(0).getTokenType() == TokenStreams.TOKEN_DUP) {
                continue;
            }
            if (encodedTokensForName.size() <= tokenPosition) {
                continue;
            }
            final EncodeToken encodeToken = encodedTokensForName.get(tokenPosition);
            final byte type = encodeToken.getTokenType();
            tokenStreams.get(TokenStreams.TOKEN_TYPE).put(type);
            switch (type) {
                case TokenStreams.TOKEN_DIFF:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIFF, numNames)
                            .putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DUP:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DUP, numNames)
                            .putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_STRING:
                    writeString(
                            getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_STRING, numNames),
                            encodeToken.getRelativeTokenValue()
                    );
                    break;

                case TokenStreams.TOKEN_CHAR:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_CHAR, numNames)
                            .put((byte) encodeToken.getRelativeTokenValue().charAt(0));
                    break;

                case TokenStreams.TOKEN_DIGITS:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIGITS, numNames)
                            .putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DIGITS0:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIGITS0, numNames)
                            .putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DZLEN, numNames)
                        .put((byte) encodeToken.getRelativeTokenValue().length());
                    break;

                case TokenStreams.TOKEN_DELTA:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA, numNames)
                            .put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DELTA0:
                    getOrCreateByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA0, numNames)
                            .put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_NOP:
                case TokenStreams.TOKEN_MATCH:
                case TokenStreams.TOKEN_END:
                    //TODO: do we need to handle these token types here? throwing causes exceptions
                    //throw new CRAMException("Invalid token type: " + type);
                    break;

                default:
                    throw new CRAMException("Invalid token type: " + type);
            }
        }
        // we need to set the limit on these streams so that the downstream consumer does't try to consume
        // the entire stream
        for (int i = 0; i < TokenStreams.TOTAL_TOKEN_TYPES; i++) {
            if (tokenStreams.get(i) != null) {
                tokenStreams.get(i).limit(tokenStreams.get(i).position());
            }
        }

        return tokenStreams;
    }

    private ByteBuffer getOrCreateByteBufferFor(
            final List<ByteBuffer> tokenStreams,
            final int tokenType,
            final int numNames) {
        if (tokenStreams.get(tokenType) == null) {
            tokenStreams.set(tokenType, CompressionUtils.allocateByteBuffer(maxLength * numNames));
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
                //TODO: using "remaining" is a pretty sketchy way to do this, since it assumes that the buffer's limit is meaningful
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

    private void serializeByteStreamsFor(
            final List<ByteBuffer> tokenStreams,
            final boolean useArith,
            final ByteBuffer outBuffer) {
        // Compress and serialise the non-null tokenStreams
        for (int tokenType = 0; tokenType <= TokenStreams.TOKEN_END; tokenType++) {
            final ByteBuffer tokenBytes = tokenStreams.get(tokenType);
            if (tokenBytes != null && tokenBytes.position() > 0) {
                outBuffer.put((byte) (tokenType + ((tokenType == 0) ? 128 : 0))); //TODO: check this for sign bit correctness
                final ByteBuffer tempOutByteBuffer = tryCompress(tokenBytes, useArith);
                //TODO: the use of limit is sketchy here...
                CompressionUtils.writeUint7(tempOutByteBuffer.limit(), outBuffer);
                outBuffer.put(tempOutByteBuffer);
            }
        }
    }
}