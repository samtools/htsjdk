package htsjdk.samtools.cram.compression.nametokenisation;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.CompressionUtils;
import htsjdk.samtools.cram.compression.nametokenisation.tokens.EncodeToken;
import htsjdk.samtools.cram.compression.range.RangeEncode;
import htsjdk.samtools.cram.compression.range.RangeParams;
import htsjdk.samtools.cram.compression.rans.RANSNx16Encode;
import htsjdk.samtools.cram.compression.rans.RANSNx16Params;
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
 * Name tokenization encoder that compresses read names by tokenizing them and encoding
 * each token stream independently using rANS or arithmetic coding. Uses per-token-type
 * flag selection to match htslib's tok3 encoder behavior.
 */
public class NameTokenisationEncode {

    // Per-token-type rANS flag sets, matching htslib's tok3 level -3 profile.
    // Each row is indexed by token type constant (TOKEN_TYPE=0 through TOKEN_END=12).
    // Within each row, the encoder tries all listed flag combinations and keeps the smallest.
    private static final int[][] RANS_FLAG_SETS_BY_TOKEN_TYPE = {
            /* TOKEN_TYPE    (0x00) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.RLE_FLAG_MASK, 0},
            /* TOKEN_STRING  (0x01) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.ORDER_FLAG_MASK, RANSNx16Params.ORDER_FLAG_MASK, 0},
            /* TOKEN_CHAR    (0x02) */ {0},
            /* TOKEN_DIGITS0 (0x03) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.STRIPE_FLAG_MASK, 0},
            /* TOKEN_DZLEN   (0x04) */ {0},
            /* TOKEN_DUP     (0x05) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.STRIPE_FLAG_MASK},
            /* TOKEN_DIFF    (0x06) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.STRIPE_FLAG_MASK},
            /* TOKEN_DIGITS  (0x07) */ {RANSNx16Params.PACK_FLAG_MASK | RANSNx16Params.RLE_FLAG_MASK | RANSNx16Params.STRIPE_FLAG_MASK},
            /* TOKEN_DELTA   (0x08) */ {0},
            /* TOKEN_DELTA0  (0x09) */ {RANSNx16Params.PACK_FLAG_MASK},
            /* TOKEN_MATCH   (0x0A) */ {0},
            /* TOKEN_NOP     (0x0B) */ {0},
            /* TOKEN_END     (0x0C) */ {0},
    };

    // Per-token-type Range (arithmetic) flag sets, mirroring the rANS sets above
    private static final int[][] RANGE_FLAG_SETS_BY_TOKEN_TYPE = {
            /* TOKEN_TYPE    (0x00) */ {RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK, 0},
            /* TOKEN_STRING  (0x01) */ {RangeParams.PACK_FLAG_MASK | RangeParams.ORDER_FLAG_MASK, RangeParams.ORDER_FLAG_MASK, 0},
            /* TOKEN_CHAR    (0x02) */ {0},
            /* TOKEN_DIGITS0 (0x03) */ {RangeParams.PACK_FLAG_MASK | RangeParams.STRIPE_FLAG_MASK, 0},
            /* TOKEN_DZLEN   (0x04) */ {0},
            /* TOKEN_DUP     (0x05) */ {RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.STRIPE_FLAG_MASK},
            /* TOKEN_DIFF    (0x06) */ {RangeParams.PACK_FLAG_MASK | RangeParams.STRIPE_FLAG_MASK},
            /* TOKEN_DIGITS  (0x07) */ {RangeParams.PACK_FLAG_MASK | RangeParams.RLE_FLAG_MASK | RangeParams.STRIPE_FLAG_MASK},
            /* TOKEN_DELTA   (0x08) */ {0},
            /* TOKEN_DELTA0  (0x09) */ {RangeParams.PACK_FLAG_MASK},
            /* TOKEN_MATCH   (0x0A) */ {0},
            /* TOKEN_NOP     (0x0B) */ {0},
            /* TOKEN_END     (0x0C) */ {0},
    };
    private final static String READ_NAME_TOK_REGEX = "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)";
    private final static Pattern READ_NAME_PATTERN = Pattern.compile(READ_NAME_TOK_REGEX);

    private final static String DIGITS0_REGEX = "^0+[0-9]*$";
    private final static Pattern DIGITS0_PATTERN = Pattern.compile(DIGITS0_REGEX);

    private final static String DIGITS_REGEX = "^[0-9]+$";
    private final static Pattern DIGITS_PATTERN = Pattern.compile(DIGITS_REGEX);

    // Reusable encoder instance — avoids allocating 256x256 RANSEncodingSymbol matrix per trial
    private final RANSNx16Encode reusableRansEncoder = new RANSNx16Encode();

    private int maxPositions; // the maximum number of tokenised columns seen across all names
    private int maxStringValueLength; // longest *String* value for any token

    /**
     * Compress the input buffer of read names.
     * @param inBuffer formatted as read names separated by the byte specified by the nameSeparator parameter (this
     *                 generally happens as a result of using the ByteStopCodec to write the read names)
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
        if (numNames == 0) {
            throw new CRAMException(
                    "Name tokenizer input format requires a separator-delimited name list. No delimited names found in input.");
        }
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

        // Track all previously compressed streams for cross-position duplicate detection.
        // Each entry maps compressed bytes to the (position, tokenType) of the first occurrence.
        final List<CompressedStream> compressedStreamRegistry = new ArrayList<>();

        for (int position = 0; position < maxPositions; position++) {
            final List<ByteBuffer> streamsForPosition = distributeTokensForPosition(
                    encodedTokensByName,
                    position,
                    numNames);
            serializeTokenStreams(streamsForPosition, outBuffer, useArith, position, compressedStreamRegistry);
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
            final int duplicateIndex = nameIndex - nameIndexMap.get(name);
            return List.of(new EncodeToken.DupOrDiffToken(TokenStreams.TOKEN_DUP, String.valueOf(duplicateIndex)));
        }

        final List<EncodeToken> encodedTokens = new ArrayList<>(NameTokenisationDecode.DEFAULT_POSITION_ALLOCATION);

        encodedTokens.add(0, new EncodeToken.DupOrDiffToken(TokenStreams.TOKEN_DIFF, String.valueOf(nameIndex == 0 ? 0 : 1)));
        nameIndexMap.put(name, nameIndex);
        final int prevNameIndex = nameIndex - 1;

        // Tokenize the name by splitting on alphanumeric / non-alphanumeric boundaries.
        // Equivalent to regex: "([a-zA-Z0-9]{1,9})|([^a-zA-Z0-9]+)" but without regex overhead.
        int pos = 0;
        for (int i = 1; pos < name.length(); i++) {
            final int start = pos;
            if (isAlphanumeric(name.charAt(pos))) {
                final int limit = Math.min(name.length(), start + 9);
                do { pos++; } while (pos < limit && isAlphanumeric(name.charAt(pos)));
            } else {
                do { pos++; } while (pos < name.length() && !isAlphanumeric(name.charAt(pos)));
            }
            final String fragmentValue = name.substring(start, pos);

            byte type = TokenStreams.TOKEN_STRING;
            String relativeValue = fragmentValue; // relative value of the token (comparing to prev name's token at the same token position)

            if (isAllDigitsLeadingZero(fragmentValue)) {
                type = TokenStreams.TOKEN_DIGITS0;
            } else if (isAllDigits(fragmentValue)) {
                type = TokenStreams.TOKEN_DIGITS;
            } else if (fragmentValue.length() == 1) {
                type = TokenStreams.TOKEN_CHAR;
            } // else just treat it as an absolute string

            // compare the current token with the corresponding token from the previous name,
            // but ONLY if the previous name actually has a corresponding token and is not the terminal token
            final EncodeToken prevToken = prevNameIndex >= 0 && encodedTokensByName.get(prevNameIndex).size() > i + 1?
                    encodedTokensByName.get(prevNameIndex).get(i) :
                    null;
            if (prevToken != null && prevToken.getTokenType() != TokenStreams.TOKEN_END) {
                if (prevToken.getActualValue().equals(fragmentValue)) {
                    // identical to the previous name's token in this position
                    type = TokenStreams.TOKEN_MATCH;
                    relativeValue = null;
                } else if (type==TokenStreams.TOKEN_DIGITS &&
                        (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA)) {
                    final int curVal = Integer.parseInt(relativeValue);
                    final int d = curVal - Integer.parseInt(prevToken.getActualValue());
                    tokenFrequencies[i]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[i] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA;
                        encodedTokens.add(new EncodeToken(type, fragmentValue, d));
                        continue;
                    }
                } else if (type == TokenStreams.TOKEN_DIGITS0 &&
                        prevToken.getActualValue().length() == relativeValue.length() &&
                        (prevToken.getTokenType() == TokenStreams.TOKEN_DIGITS0 || prevToken.getTokenType() == TokenStreams.TOKEN_DELTA0)) {
                    final int curVal = Integer.parseInt(relativeValue);
                    final int d = curVal - Integer.parseInt(prevToken.getActualValue());
                    tokenFrequencies[i]++;
                    if (d >= 0 && d < 256 && tokenFrequencies[i] > nameIndex / 2) {
                        type = TokenStreams.TOKEN_DELTA0;
                        encodedTokens.add(new EncodeToken(type, fragmentValue, d));
                        continue;
                    }
                }
            }
            encodedTokens.add(new EncodeToken(type, fragmentValue, relativeValue));
            if (type == TokenStreams.TOKEN_STRING) {
                // update our longest value length, adding one for the null terminator we'll embed in the stream
                maxStringValueLength = Math.max(maxStringValueLength, fragmentValue.length() + 1);
            }
        }

        encodedTokens.add(new EncodeToken.EndToken());

        // keep track of the longest list of encoded tokens (this is basically the number of columns/fragments)
        // for use by downstream buffer allocations
        final int currMaxPositions = encodedTokens.size();
        if (maxPositions < currMaxPositions) {
            maxPositions = currMaxPositions;
        }

        return encodedTokens;
    }

    /** Check if a character is alphanumeric (a-z, A-Z, 0-9). */
    private static boolean isAlphanumeric(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    /** Check if all characters are ASCII digits (equivalent to ^[0-9]+$). */
    private static boolean isAllDigits(final String s) {
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return s.length() > 0;
    }

    /** Check if string matches ^0+[0-9]*$ (starts with at least one zero, rest are digits). */
    private static boolean isAllDigitsLeadingZero(final String s) {
        if (s.length() == 0 || s.charAt(0) != '0') return false;
        for (int i = 1; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
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
                            .putInt(encodeToken.getRelativeValueAsInt());
                    break;

                case TokenStreams.TOKEN_DUP:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DUP, numNames * 4)
                            .putInt(encodeToken.getRelativeValueAsInt());
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
                            .putInt(encodeToken.getRelativeValueAsInt());
                    break;

                case TokenStreams.TOKEN_DIGITS0:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DIGITS0, numNames * 4)
                            .putInt(encodeToken.getRelativeValueAsInt());
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DZLEN, numNames)
                        .put((byte) encodeToken.getRelativeValue().length());
                    break;

                case TokenStreams.TOKEN_DELTA:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA, numNames * 1)
                            .put((byte) encodeToken.getRelativeValueAsInt());
                    break;

                case TokenStreams.TOKEN_DELTA0:
                    getByteBufferFor(tokenStreams, TokenStreams.TOKEN_DELTA0, numNames * 1)
                            .put((byte) encodeToken.getRelativeValueAsInt());
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

    /**
     * Try multiple compression flag combinations for the given token stream and return the
     * smallest compressed result. Flag sets are selected per token type to match htslib's
     * tok3 encoder behavior.
     */
    private ByteBuffer tryCompress(final ByteBuffer nameTokenStream, final boolean useArith, final int tokenType) {
        int bestCompressedLength = 1 << 30;
        ByteBuffer compressedByteBuffer = null;
        final int streamSize = nameTokenStream.limit();

        final int[] flagSets = useArith
                ? RANGE_FLAG_SETS_BY_TOKEN_TYPE[tokenType]
                : RANS_FLAG_SETS_BY_TOKEN_TYPE[tokenType];

        for (final int flagSet : flagSets) {
            if ((flagSet & RANSNx16Params.ORDER_FLAG_MASK) != 0 && streamSize < 100) {
                continue;
            }
            if ((flagSet & RANSNx16Params.STRIPE_FLAG_MASK) != 0 && (streamSize % 4) != 0) {
                continue;
            }
            nameTokenStream.rewind();
            final ByteBuffer tmpByteBuffer;
            if (useArith) {
                tmpByteBuffer = new RangeEncode().compress(nameTokenStream, new RangeParams(flagSet));
            } else {
                final byte[] streamBytes = new byte[nameTokenStream.remaining()];
                nameTokenStream.get(streamBytes);
                final byte[] compressed = reusableRansEncoder.compress(streamBytes, new RANSNx16Params(flagSet));
                tmpByteBuffer = ByteBuffer.wrap(compressed);
            }
            if (bestCompressedLength > tmpByteBuffer.limit()) {
                bestCompressedLength = tmpByteBuffer.limit();
                compressedByteBuffer = tmpByteBuffer;
            }
        }

        if (bestCompressedLength > nameTokenStream.limit()) {
            // compression doesn't buy us anything; fall back to CAT (uncompressed)
            nameTokenStream.rewind();
            if (useArith) {
                compressedByteBuffer = new RangeEncode().compress(nameTokenStream, new RangeParams(RangeParams.CAT_FLAG_MASK));
            } else {
                final byte[] streamBytes = new byte[nameTokenStream.remaining()];
                nameTokenStream.get(streamBytes);
                final byte[] compressed = reusableRansEncoder.compress(streamBytes, new RANSNx16Params(RANSNx16Params.CAT_FLAG_MASK));
                compressedByteBuffer = ByteBuffer.wrap(compressed);
            }
        }
        return compressedByteBuffer;
    }

    /**
     * Tracks a compressed stream's bytes and its source coordinates (position, tokenType) for
     * cross-position duplicate detection.
     */
    private static class CompressedStream {
        final byte[] compressedBytes;
        final int position;
        final int tokenType;

        CompressedStream(final byte[] compressedBytes, final int position, final int tokenType) {
            this.compressedBytes = compressedBytes;
            this.position = position;
            this.tokenType = tokenType;
        }
    }

    private void serializeTokenStreams(
            final List<ByteBuffer> tokenStreams,
            final ByteBuffer outBuffer,
            final boolean useArith,
            final int currentPosition,
            final List<CompressedStream> compressedStreamRegistry) {
        // Check if the TOKEN_TYPE stream is all MATCH after the first byte. If so, the spec allows
        // us to omit it entirely — the decoder regenerates it from the first non-null stream's type.
        boolean omitTypeStream = false;
        final ByteBuffer typeStream = tokenStreams.get(TokenStreams.TOKEN_TYPE);
        if (typeStream != null && typeStream.limit() > 1) {
            typeStream.rewind();
            typeStream.get(); // skip byte 0 (the non-MATCH type)
            boolean allMatch = true;
            while (typeStream.hasRemaining()) {
                if (typeStream.get() != TokenStreams.TOKEN_MATCH) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) {
                // Check that at least one other stream exists for this position (otherwise we can't omit TYPE)
                for (int t = 1; t <= TokenStreams.TOKEN_END; t++) {
                    final ByteBuffer s = tokenStreams.get(t);
                    if (s != null && s.limit() > 0) {
                        omitTypeStream = true;
                        break;
                    }
                }
            }
            typeStream.rewind();
        }

        // Compress and serialize the non-null tokenStreams
        boolean firstStreamForPosition = true;
        for (int tokenStreamType = 0; tokenStreamType <= TokenStreams.TOKEN_END; tokenStreamType++) {
            if (omitTypeStream && tokenStreamType == TokenStreams.TOKEN_TYPE) {
                continue;
            }
            final ByteBuffer tokenBytes = tokenStreams.get(tokenStreamType);
            if (tokenBytes != null && tokenBytes.limit() > 0) {
                byte headerByte = (byte) tokenStreamType;
                if (firstStreamForPosition) {
                    headerByte |= TokenStreams.NEW_POSITION_FLAG_MASK;
                    firstStreamForPosition = false;
                }

                final ByteBuffer compressedBuffer = tryCompress(tokenBytes, useArith, tokenStreamType);
                final byte[] compressedBytes = new byte[compressedBuffer.limit()];
                compressedBuffer.rewind();
                compressedBuffer.get(compressedBytes);

                // Check for a duplicate among previously compressed streams
                CompressedStream dupSource = null;
                if (compressedBytes.length > 4) {
                    for (final CompressedStream prev : compressedStreamRegistry) {
                        if (prev.compressedBytes.length == compressedBytes.length &&
                                Arrays.equals(prev.compressedBytes, compressedBytes)) {
                            dupSource = prev;
                            break;
                        }
                    }
                }

                if (dupSource != null) {
                    // Emit a 3-byte dup reference instead of the full compressed data
                    outBuffer.put((byte) (headerByte | TokenStreams.DUP_PREVIOUS_STREAM_FLAG_MASK));
                    outBuffer.put((byte) dupSource.position);
                    outBuffer.put((byte) dupSource.tokenType);
                } else {
                    // Emit the compressed data and register it for future dedup
                    outBuffer.put(headerByte);
                    CompressionUtils.writeUint7(compressedBytes.length, outBuffer);
                    outBuffer.put(compressedBytes);
                    compressedStreamRegistry.add(new CompressedStream(compressedBytes, currentPosition, tokenStreamType));
                }
            }
        }
    }
}