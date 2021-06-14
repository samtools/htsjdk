package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.HtsDecoder;
import htsjdk.beta.plugin.HtsHeader;
import htsjdk.beta.plugin.HtsRecord;
import htsjdk.beta.plugin.bundle.Bundle;
import htsjdk.io.IOPath;
import htsjdk.samtools.util.CloseableIterator;

import java.io.InputStream;

// Dummy decoder class for use by tests
public class HtsTestDecoder implements HtsDecoder<HtsTestCodecFormat, HtsHeader, HtsRecord> {
    private final HtsCodecVersion htsVersion;
    private final HtsTestCodecFormat htsFormat;

    public HtsTestDecoder(
            final IOPath inputPath,
            final HtsTestDecoderOptions decoderOptions,
            final HtsTestCodecFormat htsFormat,
            final HtsCodecVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    public HtsTestDecoder(
            final Bundle inputBundle,
            final HtsTestDecoderOptions decoderOptions,
            final HtsTestCodecFormat htsFormat,
            final HtsCodecVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    public HtsTestDecoder(
            final InputStream is,
            final String displayName,
            final HtsTestDecoderOptions decoderOptions,
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
        return String.format("HtsTestDecoder-%s v%s", htsFormat, htsVersion);
    }

    @Override
    public HtsHeader getHeader() { return null; }

    @Override
    public void close() { }

    @Override
    public CloseableIterator<HtsRecord> iterator() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean isQueryable() {
        throw new IllegalStateException("Not implemented");
    }

    @Override
    public boolean hasIndex() {
        throw new IllegalStateException("Not implemented");
    }
}
