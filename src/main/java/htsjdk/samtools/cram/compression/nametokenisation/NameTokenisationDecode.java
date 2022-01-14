package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

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

    public static String uncompress(
            final ByteBuffer inBuffer,
            final String separator) {
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final int uncompressedLength =  inBuffer.getInt() & 0xFFFFFFFF; //unused variable. Following the spec
        final int numNames =  inBuffer.getInt() & 0xFFFFFFFF;
        final int useArith = inBuffer.get() & 0xFF;
        TokenStreams tokenStreams = new TokenStreams(inBuffer, useArith, numNames);
        List<List<String>> tokensList = new ArrayList<>(numNames);
        for(int i = 0; i < numNames; i++) {
            tokensList.add(new ArrayList<>());
        }
        StringJoiner decodedNamesJoiner = new StringJoiner(separator);
        for (int i = 0; i < numNames; i++) {
            decodedNamesJoiner.add(decodeSingleName(tokenStreams, tokensList, i));
        }
        String uncompressedNames = decodedNamesJoiner.toString();
        if (uncompressedLength == uncompressedNames.length() + separator.length()){
            return uncompressedNames + separator;
        }
        return uncompressedNames;
    }

    private static String decodeSingleName(
            final TokenStreams tokenStreams,
            final List<List<String>> tokensList,
            final int currentNameIndex) {

        // The information about whether a name is a duplicate or not
        // is obtained from the list of tokens at tokenStreams[0,0]
        byte nameType = tokenStreams.getTokenStreamByteBuffer(0,TOKEN_TYPE).get();
        final ByteBuffer distBuffer = tokenStreams.getTokenStreamByteBuffer(0,nameType).order(ByteOrder.LITTLE_ENDIAN);
        final int dist = distBuffer.getInt() & 0xFFFFFFFF;
        final int prevNameIndex = currentNameIndex - dist;
        if (nameType == TOKEN_DUP){
            tokensList.add(currentNameIndex, tokensList.get(prevNameIndex));
            return String.join("", tokensList.get(currentNameIndex));
        }
        int tokenPosition = 1; // At position 0, we get nameType information
        byte type;
        StringBuilder decodedNameBuilder = new StringBuilder();
        do {
            type = tokenStreams.getTokenStreamByteBuffer(tokenPosition, TOKEN_TYPE).get();
            String currentToken = "";
            switch(type){
                case TOKEN_CHAR:
                    final char currentTokenChar = (char) tokenStreams.getTokenStreamByteBuffer(tokenPosition, TOKEN_CHAR).get();
                    currentToken = String.valueOf(currentTokenChar);
                    break;
                case TOKEN_STRING:
                    currentToken = readString(tokenStreams.getTokenStreamByteBuffer(tokenPosition, TOKEN_STRING));
                    break;
                case TOKEN_DIGITS:
                    currentToken = getDigitsToken(tokenStreams, tokenPosition, TOKEN_DIGITS);
                    break;
                case TOKEN_DIGITS0:
                    final String digits0Token = getDigitsToken(tokenStreams, tokenPosition, TOKEN_DIGITS0);
                    final int lenDigits0Token = tokenStreams.getTokenStreamByteBuffer(tokenPosition, TOKEN_DZLEN).get() & 0xFF;
                    currentToken = leftPadNumber(digits0Token, lenDigits0Token);
                    break;
                case TOKEN_DELTA:
                    currentToken = getDeltaToken(tokenStreams, tokenPosition, tokensList, prevNameIndex, TOKEN_DELTA);
                    break;
                case TOKEN_DELTA0:
                    final String delta0Token = getDeltaToken(tokenStreams, tokenPosition, tokensList, prevNameIndex, TOKEN_DELTA0);
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
            decodedNameBuilder.append(currentToken);
            tokenPosition++;
        } while (type!= TOKEN_END);
        return decodedNameBuilder.toString();
        }

    private static String getDeltaToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final List<List<String>> tokensList,
            final int prevNameIndex,
            final byte tokenType) {
        if (!(tokenType == TOKEN_DELTA || tokenType == TOKEN_DELTA0)){
            throw new CRAMException(String.format("Invalid tokenType : %s. " +
                    "tokenType must be either TOKEN_DELTA or TOKEN_DELTA0", tokenType));
        }
        int prevToken;
        try {
            prevToken = Integer.parseInt(tokensList.get(prevNameIndex).get(tokenPosition -1));
        } catch (final NumberFormatException e) {
            final String exceptionMessageSubstring = (tokenType == TOKEN_DELTA) ? "DIGITS or DELTA" : "DIGITS0 or DELTA0";
            throw new CRAMException(String.format("The token in the prior name must be of type %s",
                    exceptionMessageSubstring), e);
        }
        final int deltaTokenValue = tokenStreams.getTokenStreamByteBuffer(tokenPosition,tokenType).get() & 0xFF;
        return Long.toString(prevToken + deltaTokenValue);
    }

    private static String getDigitsToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final byte tokenType ) {
        if (!(tokenType == TOKEN_DIGITS || tokenType == TOKEN_DIGITS0)){
            throw new CRAMException(String.format("Invalid tokenType : %s. " +
                    "tokenType must be either TOKEN_DIGITS or TOKEN_DIGITS0", tokenType));
        }
        final ByteBuffer digitsByteBuffer = tokenStreams.getTokenStreamByteBuffer(tokenPosition, tokenType).order(ByteOrder.LITTLE_ENDIAN);
        final long digits = digitsByteBuffer.getInt() & 0xFFFFFFFFL;
        return Long.toString(digits);
    }

    private static String readString(final ByteBuffer inputBuffer) {
        // spec: We fetch one byte at a time from the value byte stream,
        // appending to the name buffer until the byte retrieved is zero.
        StringBuilder resultStringBuilder = new StringBuilder();
        byte currentByte = inputBuffer.get();
        while (currentByte != 0) {
            resultStringBuilder.append((char) currentByte);
            currentByte = inputBuffer.get();
        }
        return resultStringBuilder.toString();
    }

    private static String leftPadNumber(String value, final int len) {
        // return value such that it is at least len bytes long with leading zeros
        while (value.length() < len) {
            value = "0" + value;
        }
        return value;
    }

}