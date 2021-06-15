package htsjdk.beta.codecs.reads.htsget;

import htsjdk.beta.plugin.bundle.SignatureProbingInputStream;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsCodecVersion;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormat;
import htsjdk.samtools.HtsgetBAMFileReader;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.samtools.util.htsget.HtsgetFormat;
import htsjdk.samtools.util.htsget.HtsgetRequest;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

// An Htsget codec for reading BAM.
// Note: there is no Htsget encoder
public abstract class HtsgetBAMCodec implements ReadsCodec {
    public static final HtsCodecVersion HTSGET_VERSION = new HtsCodecVersion(1, 2, 0);

    private final Set<String> extensionMap = new HashSet(Arrays.asList(FileExtensions.BAM));

    @Override
    /**
     * The HtsGet protocol doesn't seem to have a version number ?
     */
    public HtsCodecVersion getVersion() { return HTSGET_VERSION; }

    @Override
    public ReadsFormat getFileFormat() { return ReadsFormat.HTSGET_BAM; }

    @Override
    public int getSignatureSize() {
        return 0;
    }

    @Override
    public boolean claimURI(final IOPath ioPath) {
        return matchesScheme(ioPath);
    }

    private boolean matchesScheme(final IOPath ioPath) {
        final String scheme = ioPath.getScheme();
        return scheme.equals(HtsgetBAMFileReader.HTSGET_SCHEME) ||
                scheme.equals("https") ||
                scheme.equals("http");
    }

    public boolean handlesURI(final IOPath ioPath) {
        final boolean hasExtension = extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
        final boolean hasScheme =matchesScheme(ioPath);

        //TODO: does this check for "/reads/" at the start of the path ? should it ?
        final HtsgetRequest htsgetRequest = new HtsgetRequest(ioPath.getURI());
        // no format == default == BAM
        final boolean matchesRequestType = htsgetRequest.getFormat() == null || htsgetRequest.getFormat() == HtsgetFormat.BAM;

        return hasExtension && hasScheme && matchesRequestType;
    }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) { return handlesURI(ioPath); }

    @Override
    public boolean canDecodeSignature(final SignatureProbingInputStream probingInputStream, final String sourceName) {
        return false;
    }

    boolean isQueryable() {
        //is this correct ??
        return true;
    }

    boolean hasIndex() { return false; }

    @Override
    public boolean runVersionUpgrade(final HtsCodecVersion sourceCodecVersion, final HtsCodecVersion targetCodecVersion) {
        return false;
    }
}
