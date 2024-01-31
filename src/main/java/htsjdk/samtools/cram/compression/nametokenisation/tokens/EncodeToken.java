package htsjdk.samtools.cram.compression.nametokenisation.tokens;

public class EncodeToken {

    private String actualTokenValue;
    private String relativeTokenValue;
    private byte tokenType;

    public EncodeToken(String str, String val, byte type) {
        this.actualTokenValue = str;
        this.relativeTokenValue = val;
        this.tokenType = type;
    }

    public String getActualTokenValue() {
        return actualTokenValue;
    }

    public void setActualTokenValue(String actualTokenValue) {
        this.actualTokenValue = actualTokenValue;
    }

    public String getRelativeTokenValue() {
        return relativeTokenValue;
    }

    public void setRelativeTokenValue(String relativeTokenValue) {
        this.relativeTokenValue = relativeTokenValue;
    }

    public byte getTokenType() {
        return tokenType;
    }

    public void setTokenType(byte tokenType) {
        this.tokenType = tokenType;
    }
}