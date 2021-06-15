package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.beta.plugin.bundle.Bundle;

// Dummy encoder class for use by tests
public class HtsTestEncoder implements HtsEncoder<HtsTestCodecFormat, HtsTestHeader, HtsTestRecord> {
    private final HtsVersion htsVersion;
    private final HtsTestCodecFormat htsFormat;

    public HtsTestEncoder(final Bundle outputBundle,
                          final HtsEncoderOptions htsEncoderOptions,
                          final HtsTestCodecFormat htsFormat,
                          final HtsVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    @Override
    public HtsTestCodecFormat getFormat() {
        return htsFormat;
    }

    @Override
    public HtsVersion getVersion() {
        return htsVersion;
    }

    @Override
    public String getDisplayName() {
        return String.format("HtsTestEncoder-%s v%s", htsFormat, htsVersion);
    }

    @Override
    public void setHeader(HtsTestHeader header) { }

    @Override
    public void write(HtsTestRecord record) { }

    @Override
    public void close() { }

}
