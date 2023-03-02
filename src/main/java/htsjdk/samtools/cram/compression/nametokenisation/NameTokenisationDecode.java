package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_TYPE;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_STRING;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_CHAR;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DIGITS0;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DZLEN;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DUP;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DIGITS;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DELTA;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_DELTA0;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_MATCH;
import static htsjdk.samtools.cram.compression.nametokenisation.TokenStreams.TOKEN_END;

public class NameTokenisationDecode {


    public static String uncompress(final ByteBuffer inBuffer) {
        return uncompress(inBuffer, "\n");
    }

    public static String uncompress(final ByteBuffer inBuffer, final String separator) {
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final int uncompressedLength =  inBuffer.getInt() & 0xFFFFFFFF; //unused variable. Following the spec
        final int numNames =  inBuffer.getInt() & 0xFFFFFFFF;
        final int useArith = inBuffer.get() & 0xFF;
        TokenStreams tokenStreams = new TokenStreams(inBuffer, useArith, numNames);
        List<List<String>> tokensList = new ArrayList<List<String>>(numNames);
        for(int i = 0; i < numNames; i++) {
            tokensList.add(new ArrayList<>());
        }
        String decodedNamesString = "";
        for (int i = 0; i< numNames; i++){
            decodedNamesString += decodeSingleName(tokenStreams, tokensList, i) + separator;
        }
        return decodedNamesString;
    }

    private static String decodeSingleName(final TokenStreams tokenStreams,
                                           final List<List<String>> tokensList,
                                           final int currentNameIndex) {
        int type = tokenStreams.getTokenStreamBuffer(0,TOKEN_TYPE).get() & 0xFF;
        final ByteBuffer distBuffer = tokenStreams.getTokenStreamBuffer(0,type).order(ByteOrder.LITTLE_ENDIAN);
        final int dist = distBuffer.getInt() & 0xFFFFFFFF;
        final int prevNameIndex = currentNameIndex - dist;
        if (type == TOKEN_DUP){
            tokensList.add(currentNameIndex, tokensList.get(prevNameIndex));
            return String.join("", tokensList.get(currentNameIndex));
        }
        int tokenPosition = 1;
        do {
            type = tokenStreams.getTokenStreamBuffer(tokenPosition, TOKEN_TYPE).get() & 0xFF;
            String currentToken = "";
            switch(type){
                case TOKEN_CHAR:
                    char currentTokenChar = (char) tokenStreams.getTokenStreamBuffer(tokenPosition, TOKEN_CHAR).get();
                    currentToken = String.valueOf(currentTokenChar);
                    break;
                case TOKEN_STRING:
                    currentToken = readString(tokenStreams.getTokenStreamBuffer(tokenPosition, TOKEN_STRING));
                    break;
                case TOKEN_DIGITS:
                    currentToken = getDigitsToken(tokenStreams, tokenPosition, TOKEN_DIGITS);
                    break;
                case TOKEN_DIGITS0:
                    String digits0Token = getDigitsToken(tokenStreams, tokenPosition, TOKEN_DIGITS0);
                    int lenDigits0Token = tokenStreams.getTokenStreamBuffer(tokenPosition, TOKEN_DZLEN).get() & 0xFF;
                    currentToken = leftPadNumber(digits0Token, lenDigits0Token);
                    break;
                case TOKEN_DELTA:
                    currentToken = getDeltaToken(tokenStreams, tokenPosition, tokensList, prevNameIndex, TOKEN_DELTA);
                    break;
                case TOKEN_DELTA0:
                    String delta0Token = getDeltaToken(tokenStreams, tokenPosition, tokensList, prevNameIndex, TOKEN_DELTA0);
                    final int lenDelta0Token = tokensList.get(prevNameIndex).get(tokenPosition-1).length();
                    currentToken = leftPadNumber(delta0Token, lenDelta0Token);
                    break;
                case TOKEN_MATCH:
                    currentToken = tokensList.get(prevNameIndex).get(tokenPosition-1);
                    break;
                default:
                    break;
            }
            tokensList.get(currentNameIndex).add(tokenPosition-1,currentToken);
            tokenPosition++;
        } while (type!= TOKEN_END);
        return String.join("", tokensList.get(currentNameIndex));
        }

    private static String getDeltaToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final List<List<String>> tokensList,
            final int prevNameIndex,
            final int tokenType) {
        if (!(tokenType == TOKEN_DELTA || tokenType == TOKEN_DELTA0)){
            throw new CRAMException(String.format("Invalid tokenType : %s. tokenType must be either TOKEN_DELTA or TOKEN_DELTA0", tokenType));
        }
        int prevToken;
        try {
            prevToken = Integer.parseInt(tokensList.get(prevNameIndex).get(tokenPosition -1));
        } catch (NumberFormatException e) {
            String exceptionMessageSubstring = (tokenType == TOKEN_DELTA) ? "DIGITS or DELTA" : "DIGITS0 or DELTA0";
            throw new CRAMException(String.format("The token in the prior name must be of type %s", exceptionMessageSubstring), e);
        }
        final int deltaTokenValue = tokenStreams.getTokenStreamBuffer(tokenPosition,tokenType).get() & 0xFF;
        return Long.toString(prevToken + deltaTokenValue);
    }

    private static String getDigitsToken(final TokenStreams tokenStreams, final int tokenPosition, final int tokenType ) {
        if (!(tokenType == TOKEN_DIGITS || tokenType == TOKEN_DIGITS0)){
            throw new CRAMException(String.format("Invalid tokenType : %s. tokenType must be either TOKEN_DIGITS or TOKEN_DIGITS0", tokenType));
        }
        ByteBuffer digitsByteBuffer = tokenStreams.getTokenStreamBuffer(tokenPosition, tokenType);
        digitsByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        long digits = digitsByteBuffer.getInt() & 0xFFFFFFFFL;
        return Long.toString(digits);
    }

    private static String readString(ByteBuffer inputBuffer) {
        // spec: We fetch one byte at a time from the value byte stream,
        // appending to the name buffer until the byte retrieved is zero.
        StringBuilder sb = new StringBuilder();
        byte b = inputBuffer.get();
        while (b != 0) {
            sb.append((char) b);
            b = inputBuffer.get();
        }
        return sb.toString();
    }

    private static String leftPadNumber(String value, int len) {
        // return value such that it is at least len bytes long with leading zeros
        while (value.length() < len) {
            value = "0" + value;
        }
        return value;
    }

}