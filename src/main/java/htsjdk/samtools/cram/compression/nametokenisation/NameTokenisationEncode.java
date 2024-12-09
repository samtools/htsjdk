package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.nametokenisation.tokens.EncodeToken;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.ransnx16.RANSNx16Params;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NameTokenisationEncode {
    private final static String nameTokenizerRegex = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
    private final static Pattern nameTokenizerPattern = Pattern.compile(nameTokenizerRegex);

    private int maxToken;
    private int maxLength;

    public ByteBuffer compress(final ByteBuffer inBuffer, final boolean useArith) {
        maxToken = 0;
        maxLength = 0;
        //TODO: make this an ArrayList of byte[] instead of String
        final ArrayList<String> names = new ArrayList<>();
        int lastPosition = inBuffer.position();

        // convert buffer to array of names
        while(inBuffer.hasRemaining()){
            final byte currentByte = inBuffer.get();
            //TODO: is this \n the same as the shared separator ? where is this defined ?
            if ((currentByte) == '\n' || inBuffer.position()==inBuffer.limit()){
                final int length = inBuffer.position() - lastPosition;
                final byte[] bytes = new byte[length];
                inBuffer.position(lastPosition);
                inBuffer.get(bytes, 0, length);
                names.add(new String(bytes, StandardCharsets.UTF_8).trim());
                lastPosition = inBuffer.position();
            }
        }

        final int numNames = names.size();
        // guess max size -> str.length*2 + 10000 (from htscodecs javascript code)
        //TODO: what is this calculation ?
        final ByteBuffer outBuffer = allocateOutputBuffer((inBuffer.limit()*2)+10000);
        outBuffer.putInt(inBuffer.limit());
        outBuffer.putInt(numNames);
        outBuffer.put((byte)(useArith == true ? 1 : 0));

        // Instead of List<List<String>> for tokensList like we did in Decoder, we use List<List<EncodeToken>>
        // as we also need to store the TOKEN_TYPE, relative value when compared to prev name's token
        // along with the token value.
        final List<List<EncodeToken>> tokensList = new ArrayList<>(numNames);
        final HashMap<String, Integer> nameIndexMap = new HashMap<>();
        final int[] tokenFrequencies = new int[256];
        for(int nameIndex = 0; nameIndex < numNames; nameIndex++) {
            tokeniseName(tokensList, nameIndexMap, tokenFrequencies, names.get(nameIndex), nameIndex);
        }
        for (int tokenPosition = 0; tokenPosition < maxToken; tokenPosition++) {
            final List<ByteBuffer> tokenStream = new ArrayList(TokenStreams.TOTAL_TOKEN_TYPES);
            for (int i = 0; i < TokenStreams.TOTAL_TOKEN_TYPES; i++) {
                tokenStream.add(ByteBuffer.allocate(numNames* maxLength).order(ByteOrder.LITTLE_ENDIAN));
            }
            fillByteStreams(tokenStream, tokensList, tokenPosition, numNames);
            serializeByteStreams(tokenStream, useArith, outBuffer);
        }

        // sets limit to current position and position to '0'
        outBuffer.flip();
        return outBuffer;
    }

    private void tokeniseName(final List<List<EncodeToken>> tokensList,
                              final HashMap<String, Integer> nameIndexMap,
                              final int[] tokenFrequencies,
                              final String name,
                              final int currentNameIndex) {
        int currMaxLength = 0;

        // always compare against last name only
        final int prevNameIndex = currentNameIndex - 1;
        tokensList.add(new ArrayList<>());
        if (nameIndexMap.containsKey(name)) {
            // TODO: Add Test to cover this code
            tokensList.get(currentNameIndex).add(
                    // TODO: lift the common subexpressions
                    new EncodeToken(
                            String.valueOf(currentNameIndex - nameIndexMap.get(name)),
                            String.valueOf(currentNameIndex - nameIndexMap.get(name)),
                            TokenStreams.TOKEN_DUP));
        } else {
            tokensList.get(currentNameIndex).add(
                    new EncodeToken(
                            String.valueOf(currentNameIndex == 0 ? 0 : 1),
                            String.valueOf(currentNameIndex == 0 ? 0 : 1),
                            TokenStreams.TOKEN_DIFF));
        }
        // Get the list of tokens `tok` for the current name
        nameIndexMap.put(name, currentNameIndex);
        final Matcher matcher = nameTokenizerPattern.matcher(name);
        final List<String> tok = new ArrayList<>();
        while (matcher.find()) {
            tok.add(matcher.group());
        }
        for (int i = 0; i < tok.size(); i++) {
            // In the list of tokens, all the tokens are offset by 1
            // because at position "0", we have a token that provides info if the name is a DIFF or DUP
            // token 0 = DIFF vs DUP
            int tokenIndex = i + 1;
            byte type = TokenStreams.TOKEN_STRING;
            final String str = tok.get(i); // absolute value of the token
            String val = tok.get(i); // relative value of the token (comparing to prevname's token at the same token position)
            //TODO: precompile these
            if (tok.get(i).matches("^0+[0-9]*$")) {
                type = TokenStreams.TOKEN_DIGITS0;
            } else if (tok.get(i).matches("^[0-9]+$")) {
                type = TokenStreams.TOKEN_DIGITS;
            } else if (tok.get(i).length() == 1) {
                type = TokenStreams.TOKEN_CHAR;
            }

            // compare the current token with token from the previous name at the current token's index
            // if there exists a previous name and a token at the corresponding index of the previous name
            if (prevNameIndex >=0 && tokensList.get(prevNameIndex).size() > tokenIndex) {
                final EncodeToken prevToken = tokensList.get(prevNameIndex).get(tokenIndex);
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
                } else if (type==TokenStreams.TOKEN_DIGITS0 && prevToken.getActualTokenValue().length() == val.length()
                        && (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS0 || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA0)) {
                    int d = Integer.parseInt(val) - Integer.parseInt(prevToken.getActualTokenValue());
                    tokenFrequencies[tokenIndex]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[tokenIndex] > currentNameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA0;
                        val = String.valueOf(d);
                    }
                }
            }
            tokensList.get(currentNameIndex).add(new EncodeToken(str, val, type));

            if (currMaxLength < val.length() + 3) {
                // TODO: check this? Why isn't unint32 case handled?
                // +3 for integers; 5 -> (Uint32)5 (from htscodecs javascript code)
                currMaxLength = val.length() + 3;
            }
        }

        tokensList.get(currentNameIndex).add(new EncodeToken("","",TokenStreams.TOKEN_END));
        final int currMaxToken = tokensList.get(currentNameIndex).size();
        if (maxToken < currMaxToken) {
            maxToken = currMaxToken;
        }
        if (maxLength < currMaxLength) {
            maxLength = currMaxLength;
        }
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
                CompressionUtils.writeUint7(tempOutByteBuffer.limit(),outBuffer);
                outBuffer.put(tempOutByteBuffer);
            }
        }
    }

    //TODO: consolidate this with the same method in CompressionUtils
    private ByteBuffer allocateOutputBuffer(final int inSize) {

        // same as the allocateOutputBuffer in RANS4x8Encode and RANSNx16Encode
        // TODO: de-duplicate
        final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 9);
        final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize);
        if (outputBuffer.remaining() < compressedSize) {
            throw new RuntimeException("Failed to allocate sufficient buffer size for name tokenization encoder.");
        }
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return outputBuffer;
    }
}