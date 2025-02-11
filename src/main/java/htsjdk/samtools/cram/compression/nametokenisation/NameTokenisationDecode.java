package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * CRAM 3.1 NameTokenisation decoder, used to compress read names in CRAM files. The NameTokeniser codec exploits
 * the fact that read names typically follow a structured pattern consisting of alternating alpha and numeric
 * components (i.e., "H0164ALXX140820:2:1101:17727:54981") that can be tokenised and then encoded as one or more
 * differences relative to a previously tokenised name.
 *
 * Uses the rAnsNx16 and/or range codecs internally to compress the resulting token streams.
 */
public class NameTokenisationDecode {
    public final static byte NAME_SEPARATOR = 0;

    public static final int DEFAULT_POSITION_ALLOCATION = 30;

    /**
     * Uncompress the compressed name data in the input buffer. Return is a byte[] containing the read names,
     * each separated by the byte value specified by nameSeparator, including a terminating separator.
     * @param inBuffer the buffer to uncompress
     * @param nameSeparator the name separator byte to use in the output buffer
     * @return the uncompressed read names
     */
    public byte[] uncompress(final ByteBuffer inBuffer, final byte nameSeparator) {
        inBuffer.order(ByteOrder.LITTLE_ENDIAN);
        final int uncompressedLength = inBuffer.getInt();

        final int numNames = inBuffer.getInt() & 0xFFFFFFFF;
        final int useArith = inBuffer.get() & 0xFF;
        final TokenStreams tokenStreams = new TokenStreams(inBuffer, useArith, numNames);

        // two-dimensional array of previously decoded tokens, indexed as (nameIndex, tokenPosition - 1); note
        // that unlike the TYPE stream in TokenStreams, where token position 1 is located at index 1 because of
        // the use of position 0 for metadata, since there is no metadata stored at position 0 in this list,
        // position n in the original token stream is located at index n-1 in this list
        //
        // we don't preallocate these lists because we don't know how many tokens there will be in each name,
        // so we'll create each one just-in-time as we need it
        final List<List<String>> decodedNameTokens = new ArrayList<>(numNames);
        final ByteBuffer decodedNames = CompressionUtils.allocateByteBuffer(uncompressedLength);
        for (int i = 0; i < numNames; i++) {
            decodedNames.put(decodeSingleName(tokenStreams, decodedNameTokens, i));
            decodedNames.put(nameSeparator);
        }
        return decodedNames.array();
    }

    private byte[] decodeSingleName(
            final TokenStreams tokenStreams,
            final List<List<String>> decodedNameTokens,
            final int currentNameIndex) {
        // consult tokenStreams[0, TokenStreams.TOKEN_TYPE] to determine if this name uses dup or diff, and either
        // way, determine the reference name from which we will construct this name
        final byte referenceType = tokenStreams.getStream(0, TokenStreams.TOKEN_TYPE).get();
        final ByteBuffer distStream = tokenStreams.getStream(0, referenceType);
        final int referenceName = currentNameIndex - distStream.getInt() & 0xFFFFFFFF;

        if (referenceType == TokenStreams.TOKEN_DUP) {
            // propagate the existing tokens for the reference name and use them for this name (in case there is
            // a future instance of this same name that refers to THIS name's tokens), and then reconstruct and
            // return the new name by joining the accumulated tokens
            decodedNameTokens.add(currentNameIndex, decodedNameTokens.get(referenceName));
            return String.join("", decodedNameTokens.get(currentNameIndex)).getBytes();
        } else if (referenceType != TokenStreams.TOKEN_DIFF) {
            throw new CRAMException(String.format(
                    "Invalid nameType %s. nameType must be either TOKEN_DIFF or TOKEN_DUP", referenceType));
        }

        // preallocate for DEFAULT_NUMBER_OF_POSITIONS token (columns), but the list size will auto-expand if we exceed that
        final List<String> currentNameTokens = new ArrayList<>(DEFAULT_POSITION_ALLOCATION);
        final StringBuilder decodedNameBuilder = new StringBuilder();
        byte type = -1;

        // start at position 1; at position 0, there is only nameType information
        for (int tokenPos = 1; type != TokenStreams.TOKEN_END; tokenPos++) {
            type = tokenStreams.getStream(tokenPos, TokenStreams.TOKEN_TYPE).get();

            // use "" instead of null, otherwise the null is rendered by the joiner as the string "null"
            String currentToken = "";

            switch (type) {
                case TokenStreams.TOKEN_CHAR:
                    final char currentTokenChar = (char) tokenStreams.getStream(tokenPos, TokenStreams.TOKEN_CHAR).get();
                    currentToken = String.valueOf(currentTokenChar);
                    break;
                case TokenStreams.TOKEN_STRING:
                    currentToken = readString(tokenStreams.getStream(tokenPos, TokenStreams.TOKEN_STRING));
                    break;
                case TokenStreams.TOKEN_DIGITS:
                    currentToken = getDigitsToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DIGITS);
                    break;
                case TokenStreams.TOKEN_DIGITS0:
                    final String digits0Token = getDigitsToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DIGITS0);
                    final int lenDigits0Token = tokenStreams.getStream(tokenPos, TokenStreams.TOKEN_DZLEN).get() & 0xFF;
                    currentToken = leftPadWith0(digits0Token, lenDigits0Token);
                    break;
                case TokenStreams.TOKEN_DELTA:
                    currentToken = getDeltaToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DELTA, decodedNameTokens, referenceName);
                    break;
                case TokenStreams.TOKEN_DELTA0:
                    final String delta0Token = getDeltaToken(tokenStreams, tokenPos, TokenStreams.TOKEN_DELTA0, decodedNameTokens, referenceName);
                    final int lenDelta0Token = decodedNameTokens.get(referenceName).get(tokenPos-1).length();
                    currentToken = leftPadWith0(delta0Token, lenDelta0Token);
                    break;
                case TokenStreams.TOKEN_MATCH:
                    currentToken = decodedNameTokens.get(referenceName).get(tokenPos-1);
                    break;
                case TokenStreams.TOKEN_END: // tolerate END, it will terminates the enclosing loop
                    break;
                case TokenStreams.TOKEN_NOP:
                    //no-op token, inserted by the writer to take up space to keep the streams aligned
                    break;

                // fall through - these shouldn't be present at this point in the stream
                case TokenStreams.TOKEN_TYPE:
                case TokenStreams.TOKEN_DUP:   //position 0 only
                case TokenStreams.TOKEN_DIFF:  //position 0 only
                case TokenStreams.TOKEN_DZLEN: //gets consumed as part of processing TOKEN_DIGITS0
                default:
                    throw new CRAMException(String.format(
                            "Invalid tokenType : %s. tokenType must be one of the valid token types",
                            type));
            }
            currentNameTokens.add(tokenPos-1, currentToken);
            decodedNameBuilder.append(currentToken);
        }

        decodedNameTokens.add(currentNameIndex, currentNameTokens);
        return decodedNameBuilder.toString().getBytes();
    }

    private String getDeltaToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final byte tokenType,
            final List<List<String>> previousTokensList,
            final int prevNameIndex) {
        if (tokenType != TokenStreams.TOKEN_DELTA && tokenType != TokenStreams.TOKEN_DELTA0){
            throw new CRAMException(String.format(
                    "Invalid delta tokenType %s must be either TOKEN_DELTA or TOKEN_DELTA0", tokenType));
        }

        try {
            final String prevToken = previousTokensList.get(prevNameIndex).get(tokenPosition - 1);
            int prevTokenInt = Integer.parseInt(prevToken);
            final int deltaTokenValue = tokenStreams.getStream(tokenPosition, tokenType).get() & 0xFF;
            return Long.toString(prevTokenInt + deltaTokenValue);
        } catch (final NumberFormatException e) {
            throw new CRAMException(
                    String.format("The token in the prior name must be of type %s",
                            tokenType == TokenStreams.TOKEN_DELTA ?
                                    "DIGITS or DELTA" :
                                    "DIGITS0 or DELTA0", e));
        }
    }

    private String getDigitsToken(
            final TokenStreams tokenStreams,
            final int tokenPosition,
            final byte tokenType ) {
        if (tokenType != TokenStreams.TOKEN_DIGITS && tokenType != TokenStreams.TOKEN_DIGITS0) {
            throw new CRAMException(
                    String.format(
                            "Invalid tokenType: %s tokenType must be either TOKEN_DIGITS or TOKEN_DIGITS0",
                            tokenType));
        }
        final ByteBuffer digitsByteBuffer = tokenStreams.getStream(tokenPosition, tokenType);
        final long digits = digitsByteBuffer.getInt() & 0xFFFFFFFFL;
        return Long.toString(digits);
    }

    private String readString(final ByteBuffer inputBuffer) {
        // count the number of bytes in the next string; once we know that, we can efficiently convert them
        // into a string directly from the stream without an intermediate bytebuffer/allocation
        int pos = inputBuffer.position();
        int count = pos;
        while (inputBuffer.get(count) != 0) {
            count++;
        }
        final String s = new String(
                inputBuffer.array(),
                pos + inputBuffer.arrayOffset(), // include the offset since we're using the underlying array directly
                count - pos,
                StandardCharsets.UTF_8);
        inputBuffer.position(count+1); // skip over the null
        return s;
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