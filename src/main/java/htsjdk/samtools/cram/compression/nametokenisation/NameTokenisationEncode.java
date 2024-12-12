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
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Naive name tokenization encoder
 */
public class NameTokenisationEncode {
    private final static String READ_NAME_TOK_REGEX = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
    private final static Pattern READ_NAME_PATTERN = Pattern.compile(READ_NAME_TOK_REGEX);

    private final static String DIGITS0_REGEX = "^0+[0-9]*$";
    private final static Pattern DIGITS0_PATTERN = Pattern.compile(DIGITS0_REGEX);

    private final static String DIGITS_REGEX = "^[0-9]+$";
    private final static Pattern DIGITS_PATTERN = Pattern.compile(DIGITS_REGEX);

    private int maxToken;
    private int maxLength;

    // the output is a ByteBuffer containing the read names, separated by the NAME_SEPARATOR byte, WITHOUT
    // a terminating separator
    public ByteBuffer compress(final ByteBuffer inBuffer, final boolean useArith) {
        // strictly speaking, this is probably not even necessary, but we have to count the names anyway since its the
        // first thing that we need to write to the output stream, so parse the names out while we're at it
        final List<String> names = extractInputNames(inBuffer, CRAMEncodingStrategy.DEFAULT_READS_PER_SLICE);
        final int numNames = names.size();
        //TODO: is subtracting one correct here ?
        final int uncompressedInputSize = inBuffer.limit() - numNames - 1;

        //TODO: guess max size -> str.length*2 + 10000 (from htscodecs javascript code)
        final ByteBuffer outBuffer = CompressionUtils.allocateByteBuffer((inBuffer.limit()*2)+10000);
        //TODO: what is the correct value here ? does/should this include
        // the local name delimiter that we use to format the input stream (??)
        outBuffer.putInt(uncompressedInputSize);
        outBuffer.putInt(numNames);
        outBuffer.put((byte)(useArith == true ? 1 : 0));

        // keep a List<List<EncodeToken>>, as we also need to store the TOKEN_TYPE, relative value when compared
        // to prev name's token along with the token value.
        final List<List<EncodeToken>> encodedTokens = new ArrayList<>(numNames);
        //TODO: using a map is sketchy here, since read names can be duplicates; this is probably fine since it doesn't
        // really matter which index is recorded here - any one will do
        final HashMap<String, Integer> nameIndexMap = new HashMap<>();
        final int[] tokenFrequencies = new int[256];
        for(int nameIndex = 0; nameIndex < numNames; nameIndex++) {
            tokeniseName(encodedTokens, nameIndexMap, tokenFrequencies, names.get(nameIndex), nameIndex);
        }
        for (int tokenPosition = 0; tokenPosition < maxToken; tokenPosition++) {
            final List<ByteBuffer> tokenStream = new ArrayList(TokenStreams.TOTAL_TOKEN_TYPES);
            for (int i = 0; i < TokenStreams.TOTAL_TOKEN_TYPES; i++) {
                //TODO: this is overkill - creating giant buffers, the size of which surfaces in the decoder
                tokenStream.add(CompressionUtils.allocateByteBuffer(numNames * maxLength));
            }
            fillByteStreams(tokenStream, encodedTokens, tokenPosition, numNames);
            serializeByteStreams(tokenStream, useArith, outBuffer);
        }

        // sets limit to current position and position to '0'
        outBuffer.flip();
        return outBuffer;
    }

    // return the token list for a new name
    private List<EncodeToken> tokeniseName(
            final List<List<EncodeToken>> previousNameTokens,
            final HashMap<String, Integer> nameIndexMap,
            final int[] tokenFrequencies,
            final String name,
            final int currentNameIndex) {
        // create a new token list for this name, and populate position 0 with the token that indicates whether
        // the name is a DIFF or DUP
        final List<EncodeToken> nameTokens = new ArrayList<>();
        previousNameTokens.add(nameTokens);
        if (nameIndexMap.containsKey(name)) {
            //TODO:its super wasteful to do all this string interconversion
            final String indexStr = String.valueOf(currentNameIndex - nameIndexMap.get(name));
            nameTokens.add(0, new EncodeToken(indexStr, indexStr, TokenStreams.TOKEN_DUP));
            // this is a duplicate, so just return the tokens from the previous name, or just return
            // altogether ? YES!
            //????????????????????????????????????
            return nameTokens;
        } else {
            final String indexStr = String.valueOf(currentNameIndex == 0 ? 0 : 1);
            nameTokens.add(0,new EncodeToken(indexStr, indexStr, TokenStreams.TOKEN_DIFF));
        }
        nameIndexMap.put(name, currentNameIndex);

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
            final int prevNameIndex = currentNameIndex - 1; // always compare against last name only
            if (prevNameIndex >=0 && previousNameTokens.get(prevNameIndex).size() > tokenIndex) {
                final EncodeToken prevToken = previousNameTokens.get(prevNameIndex).get(tokenIndex);
                if (prevToken.getActualTokenValue().equals(tok.get(i))) {
                    type = TokenStreams.TOKEN_MATCH;
                    val = "";
                } else if (type==TokenStreams.TOKEN_DIGITS
                        && (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA)) {
                    int v = Integer.parseInt(val);
                    int s = Integer.parseInt(prevToken.getActualTokenValue());
                    int d = v - s;
                    tokenFrequencies[tokenIndex]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[tokenIndex] > currentNameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA;
                        val = String.valueOf(d);
                    }
                } else if (type == TokenStreams.TOKEN_DIGITS0 && prevToken.getActualTokenValue().length() == val.length()
                        && (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS0 || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA0)) {
                    int d = Integer.parseInt(val) - Integer.parseInt(prevToken.getActualTokenValue());
                    tokenFrequencies[tokenIndex]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[tokenIndex] > currentNameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA0;
                        val = String.valueOf(d);
                    }
                }
            }
            nameTokens.add(new EncodeToken(str, val, type));

            if (currMaxLength < val.length() + 3) {
                // TODO: check this? Why isn't unint32 case handled?
                // +3 for integers; 5 -> (Uint32)5 (from htscodecs javascript code)
                //if (max_len < T[n][t].val.length+3)  // +3 for integers; 5 -> (Uint32)5
                //    max_len = T[n][t].val.length+3
                currMaxLength = val.length() + 3;
            }
        }

        nameTokens.add(new EncodeToken("","",TokenStreams.TOKEN_END));
        final int currMaxToken = nameTokens.size();
        if (maxToken < currMaxToken) {
            maxToken = currMaxToken;
        }
        if (maxLength < currMaxLength) {
            maxLength = currMaxLength;
        }

        return nameTokens;
    }

    private List<String> extractInputNames(final ByteBuffer inBuffer, final int preAllocationSize) {
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
                        // special case handling end of the buffer, where there is no for the lack of a trailing separator
                        length - (inBuffer.position() == inBuffer.limit() ? 0 : 1),
                        StandardCharsets.UTF_8));
                lastPosition = inBuffer.position();
            }
        }
        return names;
    }

    private void fillByteStreams(
            final List<ByteBuffer> tokenStream,
            final List<List<EncodeToken>> tokensList,
            final int tokenPosition,
            final int numNames) {

        // Fill tokenStreams object using tokensList
        for (int nameIndex = 0; nameIndex < numNames; nameIndex++) {
            if (tokenPosition > 0 && tokensList.get(nameIndex).get(0).getTokenType() == TokenStreams.TOKEN_DUP) {
                continue;
            }
            if (tokensList.get(nameIndex).size() <= tokenPosition) {
                continue;
            }
            final EncodeToken encodeToken = tokensList.get(nameIndex).get(tokenPosition);
            final byte type = encodeToken.getTokenType();
            tokenStream.get(TokenStreams.TOKEN_TYPE).put(type);
            switch (type) {
                case TokenStreams.TOKEN_DIFF:
                    tokenStream.get(TokenStreams.TOKEN_DIFF).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DUP:
                    tokenStream.get(TokenStreams.TOKEN_DUP).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_STRING:
                    writeString(tokenStream.get(TokenStreams.TOKEN_STRING),encodeToken.getRelativeTokenValue());
                    break;

                case TokenStreams.TOKEN_CHAR:
                    tokenStream.get(TokenStreams.TOKEN_CHAR).put(encodeToken.getRelativeTokenValue().getBytes()[0]);
                    break;

                case TokenStreams.TOKEN_DIGITS:
                    tokenStream.get(TokenStreams.TOKEN_DIGITS).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DIGITS0:
                    tokenStream.get(TokenStreams.TOKEN_DIGITS0).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    tokenStream.get(TokenStreams.TOKEN_DZLEN).put((byte) encodeToken.getRelativeTokenValue().length());
                    break;

                case TokenStreams.TOKEN_DELTA:
                    tokenStream.get(TokenStreams.TOKEN_DELTA).put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TokenStreams.TOKEN_DELTA0:
                    tokenStream.get(TokenStreams.TOKEN_DELTA0).put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
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
                if (bestCompressedLength > tmpByteBuffer.remaining()) {
                    bestCompressedLength = tmpByteBuffer.remaining();
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
                if (bestCompressedLength > tmpByteBuffer.remaining()) {
                    bestCompressedLength = tmpByteBuffer.remaining();
                    compressedByteBuffer = tmpByteBuffer;
                }
            }
        }
        return compressedByteBuffer;
    }

    private void serializeByteStreams(
            final List<ByteBuffer> tokenStream,
            final boolean useArith,
            final ByteBuffer outBuffer) {

        // Compress and serialise tokenStreams
        for (int tokenType = 0; tokenType <= TokenStreams.TOKEN_END; tokenType++) {
            if (tokenStream.get(tokenType).remaining() > 0) {
                outBuffer.put((byte) (tokenType + ((tokenType == 0) ? 128 : 0))); //TODO: check this for sign bit correctness
                final ByteBuffer tempOutByteBuffer = tryCompress(tokenStream.get(tokenType), useArith);
                //TODO: the use of limit is sketchy here...
                CompressionUtils.writeUint7(tempOutByteBuffer.limit(),outBuffer);
                outBuffer.put(tempOutByteBuffer);
            }
        }
    }
}