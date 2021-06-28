package htsjdk.beta.plugin.registry.testcodec;

import java.util.Optional;

// File formats for the HtsTest codec implmentations used to test codec resolution
public enum HtsTestCodecFormat {
    FILE_FORMAT_1,
    FILE_FORMAT_2,
    FILE_FORMAT_3,
    FILE_FORMAT_4;

    public static Optional<HtsTestCodecFormat> formatFromContentSubType(final String contentSubType) {
        for (final HtsTestCodecFormat f : HtsTestCodecFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
