package htsjdk.samtools.cram.compression.nametokenisation.tokens;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.nametokenisation.TokenStreams;

/**
 * A token that represents a fragment of a read name that has been tokenised.
 *
 * It would be more efficient to separate the cases where we want to store a numeric fragment from the string cases,
 * but for simplicity for now we store all fragments as strings, and the caller interconverts them back to ints on
 * demand.
 */
public class EncodeToken {

    private byte tokenType;
    private String actualValue;
    private String relativeValue;

    /**
     * Token type TOKEN_END has no associated value at all.
     * @param typeTokenEnd - must be TokenStreams.TOKEN_END
     */
    public EncodeToken(final byte typeTokenEnd) {
        this(typeTokenEnd, null, null);
        if (typeTokenEnd != TokenStreams.TOKEN_END) {
            throw new CRAMException("This constructor should only be used for token type TOKEN_END");
        }
    }

    /**
     * Token types TOKEN_DIFF and TOKEN_DUP have a relative value, but no absolute value, since they
     * don't represent a token fragment at a particular position, but rather a token that dictates
     * which previous read to use as a diff reference.
     * @param type
     */
    public EncodeToken(final byte type, final String relativeValue) {
        this(type, null, relativeValue);
        if (tokenType != TokenStreams.TOKEN_DIFF && tokenType != TokenStreams.TOKEN_DUP) {
            throw new CRAMException("This constructor should only be used for token types TOKEN_DIFF and TOKEN_DUP");
        }
    }

    /**
     * Token types TOKEN_DELTA, TOKEN_DELTA0, TOKEN_DIGITS, TOKEN_DIGITS0 all have a relative value that
     * differs from the actual value of the original fragment, and for those token types, we need to preserve
     * both during the encoding process. We need to preserve the absolute value for so we can use it to detect
     * duplicate fragments in downstream reads, and we need to preserve the relative value since, if it's
     * present, that's what we emit to the output stream.
     *
     * @param type
     * @param actualValue
     * @param relativeValue
     */
    public EncodeToken(final byte type, final String actualValue, final String relativeValue) {
        this.tokenType = type;
        this.actualValue = actualValue;
        this.relativeValue = relativeValue;
    }

    public byte getTokenType() {
        return tokenType;
    }

    public String getActualValue() {
        return actualValue;
    }

    public String getRelativeValue() {
        return relativeValue;
    }

}