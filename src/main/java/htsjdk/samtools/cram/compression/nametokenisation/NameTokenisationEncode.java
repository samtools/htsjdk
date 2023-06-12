package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.compression.nametokenisation.tokens.EncodeToken;
import htsjdk.samtools.cram.compression.nametokenisation.tokens.Token;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANSEncode;
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

import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_CHAR;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DELTA;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DELTA0;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DIFF;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DIGITS;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DIGITS0;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DUP;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DZLEN;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_END;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_MATCH;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_STRING;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_TYPE;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOTAL_TOKEN_TYPES;
import static htsjdk.samtools.cram.compression.rans.Utils.writeUint7;


public class NameTokenisationEncode {

    private int maxToken;
    private int maxLength;

    public ByteBuffer compress(final ByteBuffer inBuffer){
        return compress(inBuffer, 0);
    }

    public ByteBuffer compress(final ByteBuffer inBuffer, final int useArith){
        maxToken = 0;
        maxLength = 0;
        ArrayList<String> names = new ArrayList<>();
        int lastPosition = inBuffer.position();

        // convert buffer to array of names
        while(inBuffer.hasRemaining()){
            byte currentByte = inBuffer.get();
            if ((currentByte) == '\n' || inBuffer.position()==inBuffer.limit()){
                int length = inBuffer.position() - lastPosition;
                byte[] bytes = new byte[length];
                inBuffer.position(lastPosition);
                inBuffer.get(bytes, 0, length);
                names.add(new String(bytes, StandardCharsets.UTF_8).trim());
                lastPosition = inBuffer.position();
            }
        }

        final int numNames = names.size();
        // guess max size -> str.length*2 + 10000 (from htscodecs javascript code)
        ByteBuffer outBuffer = allocateOutputBuffer((inBuffer.limit()*2)+10000);
        outBuffer.putInt(inBuffer.limit());
        outBuffer.putInt(numNames);
        outBuffer.put((byte)useArith);

        // Instead of List<List<String>> for tokensList like we did in Decoder, we use List<List<EncodeToken>>
        // as we also need to store the TOKEN_TYPE, relative value when compared to prev name's token
        // along with the token value.
        List<List<EncodeToken>> tokensList = new ArrayList<>(numNames);
        HashMap<String, Integer> H = new HashMap<>();
        int[] F = new int[256];
        for(int i = 0; i < numNames; i++) {
            tokeniseName(tokensList, H, F, names.get(i), i);
        }
        TokenStreams tokenStreams = new TokenStreams();

        // TODO: use one for loop for both fillByteStreams & serializeByteStreams
        // Reuse tokenStream instead of creating an array of tokenStreams
        for (int tokPosition = 0; tokPosition < maxToken; tokPosition++) {
            fillByteStreams( tokenStreams,tokensList,tokPosition,names);
        }
        for (int tokPosition = 0; tokPosition < maxToken; tokPosition++) {
            serializeByteStreams( tokenStreams,tokPosition,useArith,outBuffer);
        }

        // sets limit to current position and position to '0'
        outBuffer.flip();
        return outBuffer;
    }

    private void tokeniseName(final List<List<EncodeToken>> tokensList,
                              HashMap<String, Integer> nameIndexMap,
                              int[] F,
                              final String name,
                              final int currentNameIndex) {
        int currMaxLength = 0;

        // always compare against last name only
        final int prevNameIndex = currentNameIndex - 1;
        tokensList.add(new ArrayList<>());
        if (nameIndexMap.containsKey(name)) {
            // TODO: Add Test to cover this code
            tokensList.get(currentNameIndex).add(new EncodeToken(String.valueOf(currentNameIndex - nameIndexMap.get(name)), String.valueOf(currentNameIndex - nameIndexMap.get(name)),TOKEN_DUP));
        } else {
            tokensList.get(currentNameIndex).add(new EncodeToken(String.valueOf(currentNameIndex == 0 ? 0 : 1),String.valueOf(currentNameIndex == 0 ? 0 : 1),TOKEN_DIFF));
        }
        // Get the list of tokens `tok` for the current name
        nameIndexMap.put(name, currentNameIndex);
        String regex = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(name);
        List<String> tok = new ArrayList<>();
        while (matcher.find()) {
            tok.add(matcher.group());
        }
        for (int i = 0; i < tok.size(); i++) {
            // In the list of tokens, all the tokens are offset by 1
            // because at position "0", we have a token that provides info if the name is a DIFF or DUP
            // token 0 = DIFF vs DUP
            int tokenIndex = i + 1;
            byte type = TOKEN_STRING;
            String str = tok.get(i); // absolute value of the token
            String val = tok.get(i); // relative value of the token (comparing to prevname's token at the same token position)
            if (tok.get(i).matches("^0+[0-9]*$")) {
                type = TOKEN_DIGITS0;
            } else if (tok.get(i).matches("^[0-9]+$")) {
                type = TOKEN_DIGITS;
            } else if (tok.get(i).length() == 1) {
                type = TOKEN_CHAR;
            }

            // compare the current token with token from the previous name at the current token's index
            // if there exists a previous name and a token at the corresponding index of the previous name
            if (prevNameIndex >=0 && tokensList.get(prevNameIndex).size() > tokenIndex) {
                EncodeToken prevToken = tokensList.get(prevNameIndex).get(tokenIndex);
                if (prevToken.getActualTokenValue().equals(tok.get(i))) {
                    type = TOKEN_MATCH;
                    val = "";
                } else if (type==TOKEN_DIGITS
                        && (prevToken.getTokenType() == TOKEN_DIGITS || prevToken.getTokenType() == TOKEN_DELTA)) {
                    int v = Integer.parseInt(val);
                    int s = Integer.parseInt(prevToken.getActualTokenValue());
                    int d = v - s;
                    F[tokenIndex]++;
                    if (d >= 0 && d < 256 && F[tokenIndex] > currentNameIndex / 2) {
                        type = TOKEN_DELTA;
                        val = String.valueOf(d);
                    }
                } else if (type==TOKEN_DIGITS0 && prevToken.getActualTokenValue().length() == val.length()
                        && (prevToken.getTokenType() == TOKEN_DIGITS0 || prevToken.getTokenType() == TOKEN_DELTA0)) {
                    int d = Integer.parseInt(val) - Integer.parseInt(prevToken.getActualTokenValue());
                    F[tokenIndex]++;
                    if (d >= 0 && d < 256 && F[tokenIndex] > currentNameIndex / 2) {
                        type = TOKEN_DELTA0;
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

        tokensList.get(currentNameIndex).add(new EncodeToken("","",TOKEN_END));
        final int currMaxToken = tokensList.get(currentNameIndex).size();
        if (maxToken < currMaxToken)
            maxToken = currMaxToken;
        if (maxLength < currMaxLength)
            maxLength = currMaxLength;
    }

    public void fillByteStreams(
            TokenStreams tokenStreams,
            List<List<EncodeToken>> tokensList,
            int tokenPosition,
            ArrayList<String> names) {

        // In tokenStreams, for every token, for the given position add a ByteBuffer of length = names.len * max_len
        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            final List<Token> currTokenStream = tokenStreams.getTokenStreamByType(i);
            currTokenStream.add(new Token(ByteBuffer.allocate(names.size()* maxLength).order(ByteOrder.LITTLE_ENDIAN)));
        }

        // Fill tokenStreams object using tokensList
        for (int n = 0; n < names.size(); n++) {
            if (tokenPosition > 0 && tokensList.get(n).get(0).getTokenType() == TOKEN_DUP) {
                continue;
            }
            if (tokensList.get(n).size() <= tokenPosition) {
                continue;
            }
            EncodeToken encodeToken = tokensList.get(n).get(tokenPosition);
            byte type = encodeToken.getTokenType();
            tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_TYPE).put(type);
            switch (type) {
                case TOKEN_DIFF:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DIFF).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TOKEN_DUP:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DUP).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TOKEN_STRING:
                    writeString(tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_STRING),encodeToken.getRelativeTokenValue());
                    break;

                case TOKEN_CHAR:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_CHAR).put(encodeToken.getRelativeTokenValue().getBytes()[0]);
                    break;

                case TOKEN_DIGITS:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DIGITS).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TOKEN_DIGITS0:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DIGITS0).putInt(Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DZLEN).put((byte) encodeToken.getRelativeTokenValue().length());
                    break;

                case TOKEN_DELTA:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DELTA).put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;

                case TOKEN_DELTA0:
                    tokenStreams.getTokenStreamByteBuffer(tokenPosition,TOKEN_DELTA0).put((byte)Integer.parseInt(encodeToken.getRelativeTokenValue()));
                    break;
            }
        }

        for (int i = 0; i < TOTAL_TOKEN_TYPES; i++) {
            final ByteBuffer currTokenStream = tokenStreams.getTokenStreamByteBuffer(tokenPosition,i);
            currTokenStream.flip();
        }
    }

    private static void writeString(ByteBuffer tokenStreamBuffer, String val) {
        byte[] bytes = val.getBytes();
        tokenStreamBuffer.put(bytes);
        tokenStreamBuffer.put((byte) 0);
    }

    public static ByteBuffer tryCompress(ByteBuffer src, final int useArith) {
        // compress with different formatFlags
        // and return the compressed output ByteBuffer with the least number of bytes
        int bestcompressedByteLength = 1 << 30;
        ByteBuffer compressedByteBuffer = null;
        int[] formatFlagsList = {0, 1, 64, 65, 128, 129, 193+8};
        for (int formatFlags : formatFlagsList) {
            if ((formatFlags & 1) != 0 && src.remaining() < 100)
                continue;

            if ((formatFlags & 8) != 0 && (src.remaining() % 4) != 0)
                continue;

            ByteBuffer tmpByteBuffer = null;
            try {
                if (useArith!=0) {
                    // Encode using Range
                    RangeEncode rangeEncode = new RangeEncode();
                    src.rewind();
                    tmpByteBuffer = rangeEncode.compress(src,new RangeParams(formatFlags));

                } else {
                    // Encode using RANS
                    RANSEncode ransEncode = new RANSNx16Encode();
                    src.rewind();
                    tmpByteBuffer = ransEncode.compress(src, new RANSNx16Params(formatFlags));
                }
            } catch (final Exception ignored) {}
            if (tmpByteBuffer != null && bestcompressedByteLength > tmpByteBuffer.remaining()) {
                bestcompressedByteLength = tmpByteBuffer.remaining();
                compressedByteBuffer = tmpByteBuffer;
            }
        }
        return compressedByteBuffer;
    }

    protected void serializeByteStreams(
            TokenStreams tokenStreams,
            final int tokenPosition,
            final int useArith,
            ByteBuffer outBuffer) {

        // Compress and serialise tokenStreams
        for (int tokenType = 0; tokenType <= TOKEN_END; tokenType++) {
            if (tokenStreams.getTokenStreamByteBuffer(tokenPosition, tokenType).remaining() > 0) {
                outBuffer.put((byte) (tokenType + ((tokenType == 0) ? 128 : 0)));
                ByteBuffer tempOutByteBuffer = tryCompress(tokenStreams.getTokenStreamByteBuffer(tokenPosition, tokenType), useArith);
                writeUint7(tempOutByteBuffer.limit(),outBuffer);
                outBuffer.put(tempOutByteBuffer);
            }
        }
    }

    protected ByteBuffer allocateOutputBuffer(final int inSize) {

        // same as the allocateOutputBuffer in RANS4x8Encode and RANSNx16Encode
        // TODO: de-duplicate
        final int compressedSize = (int) (1.05 * inSize + 257 * 257 * 3 + 9);
        final ByteBuffer outputBuffer = ByteBuffer.allocate(compressedSize);
        if (outputBuffer.remaining() < compressedSize) {
            throw new RuntimeException("Failed to allocate sufficient buffer size for Range coder.");
        }
        outputBuffer.order(ByteOrder.LITTLE_ENDIAN);
        return outputBuffer;
    }
}