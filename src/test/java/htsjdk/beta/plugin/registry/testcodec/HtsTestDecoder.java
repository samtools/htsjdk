package htsjdk.beta.plugin.registry.testcodec;

import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.io.bundle.Bundle;
import htsjdk.beta.plugin.reads.ReadsDecoder;
import htsjdk.beta.plugin.reads.ReadsDecoderOptions;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;

import java.util.Optional;

// Dummy decoder class for use by tests
public class HtsTestDecoder implements ReadsDecoder {
    private final HtsVersion htsVersion;
    private final String htsFormat;

    public HtsTestDecoder(
            final Bundle inputBundle,
            final ReadsDecoderOptions decoderOptions,
            final String htsFormat,
            final HtsVersion htsVersion) {
        this.htsFormat = htsFormat;
        this.htsVersion = htsVersion;
    }

    @Override
    public String getFileFormat() { return htsFormat; }

    @Override
    public HtsVersion getVersion() {
        return htsVersion;
    }

    @Override
    public String getDisplayName() {
        return String.format("HtsTestDecoder-%s v%s", htsFormat, htsVersion);
    }

    @Override
    public SAMFileHeader getHeader() {
        throw new IllegalStateException("Not implemented by test decoder");
    }

    @Override
    public void close() { }

    @Override
    public CloseableIterator<SAMRecord> iterator() {
        throw new IllegalStateException("Not implemented by test decoder");
    }

    @Override
    public boolean isQueryable() {
        throw new IllegalStateException("Not implemented by test decoder");
    }

    @Override
    public boolean hasIndex() {
        throw new IllegalStateException("Not implemented by test decoder");
    }

    @Override
    public CloseableIterator<SAMRecord> queryUnmapped() {
        throw new IllegalStateException("Not implemented by test decoder");
    }

    @Override
    public Optional<SAMRecord> queryMate(SAMRecord rec) {
        throw new IllegalStateException("Not implemented by test decoder");
    }
}
