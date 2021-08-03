package htsjdk.beta.codecs.reads.bam;

import htsjdk.beta.io.bundle.BundleResourceType;
import htsjdk.io.IOPath;
import htsjdk.beta.plugin.HtsVersion;
import htsjdk.beta.plugin.reads.ReadsCodec;
import htsjdk.beta.plugin.reads.ReadsFormats;
import htsjdk.samtools.util.FileExtensions;
import htsjdk.utils.InternalAPI;
import htsjdk.utils.ValidationUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * InternalAPI
 *
 * Base class for {@link BundleResourceType#READS_BAM} codecs.
 */
@InternalAPI
public abstract class BAMCodec implements ReadsCodec {
    public static final HtsVersion BAM_DEFAULT_VERSION = new HtsVersion(1, 0,0);

    private static final Set<String> extensionMap = new HashSet<>(Arrays.asList(FileExtensions.BAM));

    @Override
    public String getFileFormat() { return ReadsFormats.BAM; }

    @Override
    public boolean canDecodeURI(final IOPath ioPath) {
        ValidationUtils.nonNull(ioPath, "ioPath");
        return extensionMap.stream().anyMatch(ext-> ioPath.hasExtension(ext));
    }

}
