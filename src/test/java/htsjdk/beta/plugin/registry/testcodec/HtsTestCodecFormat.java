package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsFormat;

import java.util.Optional;

// File formats for the HtsTest codec implementations used to test codec resolution
public enum HtsTestCodecFormat implements HtsFormat<HtsTestCodecFormat> {
    FILE_FORMAT_1,
    FILE_FORMAT_2,
    FILE_FORMAT_3,
    FILE_FORMAT_4;

    public Optional<HtsTestCodecFormat> contentSubTypeToFormat(final String contentSubType) {
        for (final HtsTestCodecFormat f : HtsTestCodecFormat.values()) {
            if (f.name().equals(contentSubType)) {
                return Optional.of(f);
            }
        }
        return Optional.empty();
    }

}
