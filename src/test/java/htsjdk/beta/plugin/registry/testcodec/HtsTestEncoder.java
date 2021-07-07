package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.exception.HtsjdkPluginException;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsEncoder;
import htsjdk.beta.plugin.reads.ReadsEncoderOptions;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;

// Dummy encoder class for use by tests
public class HtsTestEncoder implements ReadsEncoder {
    private final HtsVersion htsVersion;
    private final String htsFormat;

    public HtsTestEncoder(final Bundle outputBundle,
                          final ReadsEncoderOptions readsEncoderOptions,
                          final String htsFormat,
                          final HtsVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    @Override
    public String getFormat() {
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
    public void setHeader(SAMFileHeader header) {
        throw new HtsjdkPluginException("Not implemented by test codec");
    }

    @Override
    public void write(SAMRecord record) {
        throw new HtsjdkPluginException("Not implemented by test codec");
    }

    @Override
    public void close() {
        // no-op
    }

}
