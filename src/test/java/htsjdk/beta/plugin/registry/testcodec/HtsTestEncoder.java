package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.HtsEncoder;
import htsjdk.beta.plugin.HtsEncoderOptions;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.io.IOPath;

import java.io.OutputStream;

// Dummy encoder class for use by tests
public class HtsTestEncoder implements HtsEncoder<HtsTestCodecFormat, HtsTestHeader, HtsTestRecord> {
    private final HtsCodecVersion htsVersion;
    private final HtsTestCodecFormat htsFormat;

    public HtsTestEncoder(final Bundle outputBundle,
                          final HtsEncoderOptions htsEncoderOptions,
                          final HtsTestCodecFormat htsFormat,
                          final HtsCodecVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    @Override
    public HtsTestCodecFormat getFormat() {
        return htsFormat;
    }

    @Override
    public HtsCodecVersion getVersion() {
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
