package htsjdk.samtools.cram.compression.nametokenisation.tokens;

public class EncodeToken {

    private String actualTokenValue;
    private String relativeTokenValue;
    private byte tokenType;

    //TODO: its super wasteful to always store strings for these, since they're often ints
    public EncodeToken(final String str, final String val, final byte type) {
        this.actualTokenValue = str;
        this.relativeTokenValue = val;
        this.tokenType = type;
    }

    public String getActualTokenValue() {
        return actualTokenValue;
    }

    public String getRelativeTokenValue() {
        return relativeTokenValue;
    }

    public byte getTokenType() {
        return tokenType;
    }
}