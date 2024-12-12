package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class NameTokenisationDecode {
    // TODO: lift these values to a common location since they're used by the encoder, the decoder, and the tests
    // for now, since we're returning a String of all the names (instead of a list, which is more efficient) because,
    // use a single byte to separate the names; this particular byte is chosen because the calling code in the CRAM
    // reader for read names already assumes it will be handed a block of '\0' separated names
    public final static byte NAME_SEPARATOR = 0;
    public final static CharSequence LOCAL_NAME_SEPARATOR_CHARSEQUENCE = new String(new byte[] {NAME_SEPARATOR});

    // the input must be a ByteBuffer containing the read names, separated by the NAME_SEPARATOR byte, WITHOUT
    // a terminating separator
    public String uncompress(final ByteBuffer inBuffer) {
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final int uncompressedLength =  inBuffer.getInt() & 0xFFFFFFFF; //unused but we have to consume it
        final int numNames =  inBuffer.getInt() & 0xFFFFFFFF;
        final int useArith = inBuffer.get() & 0xFF;

        final TokenStreams tokenStreams = new TokenStreams(inBuffer, useArith, numNames);

        // keep track of token lists for names we've already seen for subsequent lookup/reference (indexed as (nameIndex, tokenPosition))

        //TODO: for performance reasons, it would probably be wise to separate the string tokens from the int tokens
        // so we don't have to repeatedly interconvert them when fetching from this list
        // two dimensional array of previously encoded tokens, indexed as [nameIndex, position]
        final List<List<String>> previousEncodedTokens = new ArrayList<>(numNames);
        for (int i = 0; i < numNames; i++) {
            //TODO: preallocate  this list to the expected number of tokens per name
            previousEncodedTokens.add(new ArrayList<>());
        }

        //TODO: if we stored the names in an index-addressible array, or list, as we find them, instead of a StringJoiner,
        // subsequent calls to decodeSingleName could look them up by index and return them directly when it sees a dup,
        // rather than re-joining the tokens each time; maybe not worth it ?
        final StringJoiner decodedNamesJoiner = new StringJoiner(LOCAL_NAME_SEPARATOR_CHARSEQUENCE);
        for (int i = 0; i < numNames; i++) {
            decodedNamesJoiner.add(decodeSingleName(tokenStreams, previousEncodedTokens, i));
        }
        return decodedNamesJoiner.toString();
    }

    private String decodeSingleName(
            final TokenStreams tokenStreams,
            final List<List<String>> previousEncodedTokens,
            final int currentNameIndex) {

        // The information about whether a name is a duplicate or not
        // is obtained from the list of tokens at tokenStreams[0,0]
        final byte nameType = tokenStreams.getTokenStream(0, TokenStreams.TOKEN_TYPE).get();
        final ByteBuffer distBuffer = tokenStreams.getTokenStream(0, nameType);
        final int dist = distBuffer.getInt() & 0xFFFFFFFF;
        final int prevNameIndex = currentNameIndex - dist;

        if (nameType == TokenStreams.TOKEN_DUP) {
            // propagate the tokens for the previous name in case there is a future instance of this same name
            // that refers to THIS name's tokens, and then reconstruct and return the name by joining the tokens
            // (we don't have index-accessible access the previous name directly here since the previous names are
            // stored in a StringJoiner. and also, we only store the index for the most recent instance
            // of a name anyway, since we store them in a Map<name, index)
            previousEncodedTokens.add(currentNameIndex, previousEncodedTokens.get(prevNameIndex));
            return String.join("", previousEncodedTokens.get(currentNameIndex));
        }

        int tokenPos = 1; // At position 0, we get nameType information
        byte type;
        final StringBuilder decodedNameBuilder = new StringBuilder();
        do {
            type = tokenStreams.getTokenStream(tokenPos, TokenStreams.TOKEN_TYPE).get();
            String currentToken = "";
            switch(type){
                case TokenStreams.TOKEN_CHAR:
                    final char currentTokenChar = (char) tokenStreams.getTokenStream(tokenPos, TokenStreams.TOKEN_CHAR).get();
                    currentToken = String.valueOf(currentTokenChar);
                    break;
                case TokenStreams.TOKEN_STRING:
                    currentToken = readString(tokenStreams.getTokenStream(tokenPos, TokenStreams.TOKEN_STRING));
                    break;
                case TokenStreams.TOKEN_DIGITS:
                    currentToken = getDigitsToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DIGITS);
                    break;
                case TokenStreams.TOKEN_DIGITS0:
                    final String digits0Token = getDigitsToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DIGITS0);
                    final int lenDigits0Token = tokenStreams.getTokenStream(tokenPos, TokenStreams.TOKEN_DZLEN).get() & 0xFF;
                    currentToken = leftPadWith0(digits0Token, lenDigits0Token);
                    break;
                case TokenStreams.TOKEN_DELTA:
                    currentToken = getDeltaToken(tokenStreams, tokenPos, previousEncodedTokens, prevNameIndex, TokenStreams.TOKEN_DELTA);
                    break;
                case TokenStreams.TOKEN_DELTA0:
                    final String delta0Token = getDeltaToken(tokenStreams, tokenPos, previousEncodedTokens, prevNameIndex, TokenStreams.TOKEN_DELTA0);
                    final int lenDelta0Token = previousEncodedTokens.get(prevNameIndex).get(tokenPos-1).length();
                    currentToken = leftPadWith0(delta0Token, lenDelta0Token);
                    break;
                case TokenStreams.TOKEN_MATCH:
                    currentToken = previousEncodedTokens.get(prevNameIndex).get(tokenPos-1);
                    break;
                case TokenStreams.TOKEN_END: // tolerate END, it terminates the enclosing loop
                    break;
                case TokenStreams.TOKEN_NOP:
                    //no-op token, inserted by the writer to take up space to keep the streams aligned
                    break;
                // These are either consumed elsewhere or otherwise shouldn't be present in the stream at this point
                case TokenStreams.TOKEN_TYPE:
                case TokenStreams.TOKEN_DUP:   //position 0 only
                case TokenStreams.TOKEN_DIFF:  //position 0 only
                case TokenStreams.TOKEN_DZLEN: //gets consumed as part of processing TOKEN_DIGITS0
                default:
                    throw new CRAMException(String.format(
                            "Invalid tokenType : %s. tokenType must be one of the valid token types",
                            type));
            }
            //TODO: this is expanding the list many times, which is not efficient
            previousEncodedTokens.get(currentNameIndex).add(tokenPos - 1, currentToken);
            decodedNameBuilder.append(currentToken);
            tokenPos++;
        } while (type!= TokenStreams.TOKEN_END);

        return decodedNameBuilder.toString();
    }

    private String getDeltaToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final List<List<String>> tokensList,
            final int prevNameIndex,
            final byte tokenType) {
        if (!(tokenType == TokenStreams.TOKEN_DELTA || tokenType == TokenStreams.TOKEN_DELTA0)){
            throw new CRAMException(String.format(
                    "Invalid tokenType : %s. tokenType must be either TOKEN_DELTA or TOKEN_DELTA0", tokenType));
        }
        int prevToken;
        try {
            prevToken = Integer.parseInt(tokensList.get(prevNameIndex).get(tokenPosition - 1));
        } catch (final NumberFormatException e) {
            final String exceptionMessageSubstring = (tokenType == TokenStreams.TOKEN_DELTA) ? "DIGITS or DELTA" : "DIGITS0 or DELTA0";
            throw new CRAMException(String.format("The token in the prior name must be of type %s", exceptionMessageSubstring), e);
        }
        final int deltaTokenValue = tokenStreams.getTokenStream(tokenPosition, tokenType).get() & 0xFF;
        return Long.toString(prevToken + deltaTokenValue);
    }

    private String getDigitsToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final byte tokenType ) {
        if (!(tokenType == TokenStreams.TOKEN_DIGITS || tokenType == TokenStreams.TOKEN_DIGITS0)) {
            throw new CRAMException(String.format("Invalid tokenType : %s. " +
                    "tokenType must be either TOKEN_DIGITS or TOKEN_DIGITS0", tokenType));
        }
        final ByteBuffer digitsByteBuffer = tokenStreams.getTokenStream(tokenPosition, tokenType);
        final long digits = digitsByteBuffer.getInt() & 0xFFFFFFFFL;
        return Long.toString(digits);
    }

    private String readString(final ByteBuffer inputBuffer) {
        // spec: We fetch one byte at a time from the value byte stream,
        // appending to the name buffer until the byte retrieved is zero.
        final StringBuilder resultStringBuilder = new StringBuilder();
        byte currentByte = inputBuffer.get();
        while (currentByte != 0) {
            //TODO: fix this sketchy cast
            resultStringBuilder.append((char) currentByte);
            currentByte = inputBuffer.get();
        }
        return resultStringBuilder.toString();
    }

    // return value such that it is at least len bytes long with leading zeros
    private String leftPadWith0(final String value, final int len) {
        if (value.length() >= len) {
            return value;
        } else {
            final StringBuilder sb = new StringBuilder();
            sb.append("0".repeat(Math.max(0, len - value.length())));
            sb.append(value);
            return sb.toString();
        }
    }

}