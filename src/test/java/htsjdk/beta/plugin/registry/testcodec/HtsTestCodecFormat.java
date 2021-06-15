package htsjdk.beta.plugin.registry.testcodec;

public enum HtsTestCodecFormat {
    FILE_FORMAT_1,
    FILE_FORMAT_2,
    FILE_FORMAT_3,
    FILE_FORMAT_4;

    public static HtsTestCodecFormat formatFromContentSubType(final String contentSubType) {
        for (final HtsTestCodecFormat f : HtsTestCodecFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return f;
            }
        }
        return null;
    }

}
