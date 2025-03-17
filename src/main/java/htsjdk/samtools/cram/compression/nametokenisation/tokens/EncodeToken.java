package htsjdk.samtools.cram.compression.nametokenisation.tokens;

import htsjdk.samtools.cram.CRAMException;
import htsjdk.samtools.cram.compression.nametokenisation.TokenStreams;

/**
 * A token that represents a fragment of a read name that has been tokenised.
 *
 * Since not all token types have absolute and/or even relative values, there are a couple of subclasses used
 * to ensure that calling code never violates the assumptions of the token type. The subclasses (below)
 * enforce that the caller can only ask for and actual or relative value for token types that have them.
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

    // subclass for holding END tokens; throws if a caller ever requests an actual value or relative value
    public static class EndToken extends EncodeToken {
        public EndToken() {
            super(TokenStreams.TOKEN_END, null, null);
        }

        public String getActualValue() {
            throw new CRAMException("END tokens have no actual value properties");
        }

        public String getRelativeValue() {
            throw new CRAMException("END tokens have no relative value properties");
        }
    }

    // subclass for DUP and DIFF tokens; throws if a caller ever requests an actual value,
    // since these only have relative values (relative value requests are delegated to the base class)
    public static class DupOrDiffToken extends EncodeToken {
        public DupOrDiffToken(final byte tokenType, final String relativeValue) {
            super(tokenType, null, relativeValue);
            if (tokenType != TokenStreams.TOKEN_DIFF && tokenType != TokenStreams.TOKEN_DUP) {
                throw new CRAMException("This constructor should only be used for token types TOKEN_DIFF and TOKEN_DUP");
            }
        }

        public String getActualValue() {
            throw new CRAMException("DUP and DIFF tokens have no actual value properties");
        }
    }

}